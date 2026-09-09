package nl.salah.civicsignal.reports;

import java.io.IOException;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportMapService {

    static final int POINT_ZOOM_THRESHOLD = 12;
    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;

    @Autowired
    public ReportMapService(ElasticsearchClient elasticsearchClient) {
        this(elasticsearchClient, ReportDocumentIndexer.INDEX_NAME);
    }

    ReportMapService(ElasticsearchClient elasticsearchClient, String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    public ReportMapResponse map(ReportFilterCriteria filters, BoundingBox bbox, int zoom, int limit) {
        Query query = boundedQuery(filters, bbox);
        try {
            return zoom < POINT_ZOOM_THRESHOLD ? clusters(query, bbox, zoom, limit) : points(query, limit);
        } catch (IOException | ElasticsearchException exception) {
            throw new ElasticsearchUnavailableException(exception);
        }
    }

    Query boundedQuery(ReportFilterCriteria filters, BoundingBox bbox) {
        Query base = ReportFilterQueryBuilder.build(filters);
        Query geo = Query.of(query -> query.geoBoundingBox(box -> box
                .field("location")
                .boundingBox(bounds -> bounds.tlbr(corners -> corners
                        .topLeft(point -> point.latlon(latLon -> latLon.lat(bbox.north()).lon(bbox.west())))
                        .bottomRight(point -> point.latlon(latLon -> latLon.lat(bbox.south()).lon(bbox.east())))))));
        return Query.of(query -> query.bool(bool -> bool.filter(base).filter(geo)));
    }

    private ReportMapResponse points(Query query, int limit) throws IOException {
        SearchResponse<ReportDocument> response = elasticsearchClient.search(request -> request
                        .index(indexName)
                        .query(query)
                        .trackTotalHits(track -> track.enabled(true))
                        .size(limit)
                        .sort(sort -> sort.field(field -> field.field("occurredAt").order(SortOrder.Desc))),
                ReportDocument.class);
        long total = total(response);
        List<ReportMapPoint> points = response.hits().hits().stream()
                .map(hit -> hit.source())
                .filter(document -> document != null && document.location() != null)
                .map(ReportMapPoint::from)
                .toList();
        return new ReportMapResponse(ReportMapMode.POINTS, List.of(), points, total, total > points.size());
    }

    private ReportMapResponse clusters(Query query, BoundingBox bbox, int zoom, int limit) throws IOException {
        SearchResponse<Void> response = elasticsearchClient.search(request -> request
                        .index(indexName)
                        .query(query)
                        .trackTotalHits(track -> track.enabled(true))
                        .size(0)
                        .aggregations("clusters", aggregation -> aggregation
                                .geotileGrid(grid -> grid.field("location").precision(zoom).size(limit)
                                        .bounds(bounds -> bounds.tlbr(corners -> corners
                                                .topLeft(point -> point.latlon(latLon -> latLon.lat(bbox.north()).lon(bbox.west())))
                                                .bottomRight(point -> point.latlon(latLon -> latLon.lat(bbox.south()).lon(bbox.east()))))))
                                .aggregations("centroid", centroid -> centroid.geoCentroid(geo -> geo.field("location")))),
                Void.class);
        long total = total(response);
        List<ReportMapCluster> clusters = response.aggregations().get("clusters").geotileGrid().buckets().array().stream()
                .map(bucket -> new ReportMapCluster(bucket.key(), location(bucket.aggregations().get("centroid").geoCentroid().location()),
                        bucket.docCount()))
                .toList();
        long represented = clusters.stream().mapToLong(ReportMapCluster::count).sum();
        return new ReportMapResponse(ReportMapMode.CLUSTERS, clusters, List.of(), total, represented < total);
    }

    private ReportLocation location(GeoLocation location) {
        if (location.isLatlon()) {
            return new ReportLocation(location.latlon().lat(), location.latlon().lon());
        }
        if (location.isCoords() && location.coords().size() >= 2) {
            return new ReportLocation(location.coords().get(1), location.coords().get(0));
        }
        throw new IllegalStateException("Elasticsearch returned an unsupported geo centroid.");
    }

    private long total(SearchResponse<?> response) {
        return response.hits().total() == null ? 0 : response.hits().total().value();
    }
}
