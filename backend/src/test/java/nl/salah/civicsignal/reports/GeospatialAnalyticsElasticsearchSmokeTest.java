package nl.salah.civicsignal.reports;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "elasticsearch.smoke", matches = "true")
class GeospatialAnalyticsElasticsearchSmokeTest {

    private static final String INDEX = "civic-reports-geospatial-smoke";
    private static RestClient restClient;
    private static RestClientTransport transport;
    private static ElasticsearchClient client;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getProperty("elasticsearch.url", "http://localhost:9200");
        restClient = RestClient.builder(HttpHost.create(url)).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
        if (client.indices().exists(request -> request.index(INDEX)).value()) {
            client.indices().delete(request -> request.index(INDEX));
        }
        client.indices().create(request -> request.index(INDEX).mappings(ReportIndexInitializer.mapping()));
        var bulk = client.bulk(request -> {
            request.refresh(Refresh.True);
            for (int number = 1; number <= 20; number++) {
                ReportDocument document = document(number);
                request.operations(operation -> operation.index(index -> index.index(INDEX).id(document.reportId()).document(document)));
            }
            return request;
        });
        if (bulk.errors()) {
            String reasons = bulk.items().stream().filter(item -> item.error() != null)
                    .map(item -> item.error().reason()).reduce((left, right) -> left + "; " + right).orElse("unknown");
            throw new IllegalStateException("Smoke bulk indexing failed: " + reasons);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try {
            if (client != null && client.indices().exists(request -> request.index(INDEX)).value()) {
                client.indices().delete(request -> request.index(INDEX));
            }
        } finally {
            if (transport != null) {
                transport.close();
            } else if (restClient != null) {
                restClient.close();
            }
        }
    }

    @Test
    void exactlyTwentyControlledDocumentsDriveSearchAnalyticsClustersAndPoints() {
        ReportSearchService search = new ReportSearchService(client, INDEX);
        AnalyticsService analytics = new AnalyticsService(client, INDEX);
        ReportMapService map = new ReportMapService(client, INDEX);

        var afval = search.search(new ReportSearchCriteria(new ReportFilterCriteria(null, null, "Afval", null,
                null, null, null, null), 0, 20));
        assertEquals(8, afval.totalElements());
        assertEquals(8, afval.items().size());

        var exact = search.search(new ReportSearchCriteria(new ReportFilterCriteria("SMOKE-07", null, null, null,
                null, null, null, null), 0, 20));
        assertEquals(1, exact.totalElements());
        assertEquals("SMOKE-07", exact.items().getFirst().reportId());

        var summary = analytics.summary(ReportFilterCriteria.empty(), AnalyticsInterval.MONTH);
        assertEquals(20, summary.total());
        assertEquals(8, summary.open());
        assertEquals(12, summary.closed());
        assertEquals(18, summary.withLocation());
        assertEquals(6.5, summary.averageResolutionDays());
        assertEquals(6.5, summary.p50ResolutionDays());
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), summary.earliest());
        assertEquals(Instant.parse("2026-09-20T12:00:00Z"), summary.latest());
        assertEquals(new AnalyticsBucket("Afval", 8), summary.topCategories().getFirst());
        assertEquals(new AnalyticsBucket("OFFICIAL_OPEN_DATA", 15), summary.topSources().getFirst());
        assertEquals(new AnalyticsBucket("Amsterdam", 18), summary.topMunicipalities().getFirst());
        assertEquals(new AnalyticsBucket("West", 10), summary.topDistricts().getFirst());
        assertEquals(new AnalyticsBucket("CLOSED", 12), summary.topStatuses().getFirst());
        assertEquals(1, summary.timeline().size());
        assertEquals(20, summary.timeline().getFirst().count());

        BoundingBox amsterdam = new BoundingBox(4.7, 52.2, 5.1, 52.5);
        var clusters = map.map(ReportFilterCriteria.empty(), amsterdam, 0, 10);
        assertEquals(ReportMapMode.CLUSTERS, clusters.mode());
        assertEquals(18, clusters.totalMatching());
        assertEquals(1, clusters.clusters().size());
        assertEquals(18, clusters.clusters().getFirst().count());
        assertFalse(clusters.truncated());

        var points = map.map(ReportFilterCriteria.empty(), amsterdam, 14, 5);
        assertEquals(ReportMapMode.POINTS, points.mode());
        assertEquals(18, points.totalMatching());
        assertEquals(5, points.points().size());
        assertEquals("SYNTHETIC", points.points().getFirst().sourceType());
        assertTrue(points.truncated());
    }

    private static ReportDocument document(int number) {
        Instant occurredAt = Instant.parse("2026-09-%02dT12:00:00Z".formatted(number));
        boolean closed = number <= 12;
        String reportId = "SMOKE-%02d".formatted(number);
        ReportEvent event = new ReportEvent(
                UUID.nameUUIDFromBytes(reportId.getBytes(StandardCharsets.UTF_8)),
                1,
                ReportEventType.REPORT_DISCOVERED,
                reportId,
                number <= 8 ? "Afval" : number <= 14 ? "Wegen" : "Groen",
                number <= 10 ? "West" : number <= 18 ? "Oost" : "Centrum",
                occurredAt,
                number <= 15 ? ReportSourceType.OFFICIAL_OPEN_DATA : ReportSourceType.SYNTHETIC,
                number <= 15 ? "Gemeente Amsterdam Open Data" : "Synthetic generator",
                number <= 18 ? "Amsterdam" : "Utrecht",
                number <= 10 ? "Jordaan" : "Oostelijke Eilanden",
                "Subcategorie",
                closed ? "CLOSED" : "OPEN",
                closed ? occurredAt.plus(number, ChronoUnit.DAYS) : null,
                closed ? number : null,
                number <= 18 ? new ReportLocation(52.35 + number / 1000.0, 4.87 + number / 1000.0) : null);
        return ReportDocument.from(event);
    }
}
