# Geospatial analytics backend

The Elasticsearch read model exposes three public endpoints. All three share the same typed filter query, so a dashboard can move between a result list, summary and map without changing the selected population.

## Shared filters

`GET /api/v1/reports/search`, `GET /api/v1/analytics/summary` and `GET /api/v1/reports/map` accept:

- `q`: exact keyword lookup when it looks like a report ID (for example `AMS-123`), otherwise full-text search;
- `sourceType`: `MANUAL`, `SYNTHETIC` or `OFFICIAL_OPEN_DATA`;
- `category`, `municipality`, `district` and `reportStatus`: exact keyword filters;
- `dateFrom` and `dateTo`: inclusive ISO-8601 UTC instants applied to `occurredAt`.

Invalid enum values, malformed dates and a `dateFrom` after `dateTo` return HTTP 400 with `VALIDATION_FAILED`. Elasticsearch transport and request failures return HTTP 503 with `ELASTICSEARCH_UNAVAILABLE`.

Search additionally accepts zero-based `page` and `size` from 1 through 100. Search documents include municipality, neighborhood, subcategory, report status, completion time, resolution days and location. Free-text search also covers these descriptive fields.

## Summary

`GET /api/v1/analytics/summary?interval=DAY` returns:

- total, open, closed and geolocated counts;
- average and p50 resolution time in days (nullable when no completed report has a duration);
- earliest and latest occurrence (nullable for an empty result);
- the ten leading categories, source types, municipalities, districts and raw report statuses;
- a UTC date histogram using `DAY`, `WEEK` or `MONTH` calendar buckets.

A report is closed when `completedAt` exists; otherwise it is open. This keeps legacy indexed documents usable and avoids depending on source-specific status labels.

## Map

`GET /api/v1/reports/map` requires:

- `bbox=west,south,east,north`, with longitude in -180..180, latitude in -90..90 and ascending corners;
- `zoom` from 0 through 22;
- optional `limit` from 1 through 1000 (default 500).

The bounding box is executed as an Elasticsearch `geo_bounding_box` on `location`. Zoom levels below 12 return `mode: CLUSTERS` with bounded `geotile_grid` buckets and their centroids. Zoom 12 and above return `mode: POINTS` ordered newest first. `totalMatching` is the exact hit count inside the box after shared filters; `truncated` is true when the requested limit omitted matching clusters or points.

## Local smoke

Start only Elasticsearch, then run the single opt-in smoke test:

```powershell
docker compose up -d elasticsearch
Set-Location backend
.\mvnw.cmd -Dtest=GeospatialAnalyticsElasticsearchSmokeTest -Delasticsearch.smoke=true test
```

The smoke owns only the temporary `civic-reports-geospatial-smoke` index. It recreates that index, indexes exactly 20 deterministic documents, checks exact search/summary/cluster/point outcomes, and deletes the temporary index afterwards.

## Dashboard consumer

The public React routes `/`, `/reports` and `/map` serialize the same eight filters without credentials. Filter edits stay local until applied, after which the URL is the durable view state. Analytics uses a daily interval, reports request 20 records per page and the map requests at most 500 buckets or points for its confirmed bounding box.

Map points include `sourceType` so list badges and point popups use the same provenance vocabulary. Legacy documents without enrichment remain valid: optional text is shown as unknown, absent timing as unavailable, and missing or invalid coordinates are excluded from the map only.

The controlled frontend integration smoke indexes exactly 20 documents with municipality `CivicSmoke`, verifies exact total 20, newest report `CIVIC-SMOKE-20`, clustered location total 18 and a real admin login, then removes only those 20 known document IDs.
