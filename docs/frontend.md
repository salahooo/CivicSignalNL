# Frontend dashboard

The React + TypeScript application is a responsive public dashboard with six history-backed routes:

- `/` — KPIs, five accessible charts and data-quality context;
- `/reports` — enriched, paginated Elasticsearch results;
- `/map` — bounded Leaflet map with low-zoom clusters and high-zoom points;
- `/sources` — source semantics, privacy choices and manual publishing;
- `/architecture` — portfolio-oriented system explanation;
- `/admin` — isolated administration, mounted only after successful login.

`api.ts` is the only HTTP boundary. Public calls never receive an Authorization header. The admin client is separate, keeps Basic credentials only in memory and clears them on a 401. Requests have a ten-second bound and stale analytics, map and search requests are cancelled.

## Shared selection and URL state

`q`, `sourceType`, `category`, `municipality`, `district`, `reportStatus`, `dateFrom` and `dateTo` form one applied selection for overview, reports and map. Editing is draft-only: requests start after **Filters toepassen**. Applied values become removable chips and are restored on refresh, browser back/forward and shared URLs. Reports additionally preserve the zero-based `page` value and reset it when filters change.

Dates are checked in the browser for valid order and a maximum 366-day span; the backend remains authoritative. The share action copies the complete safe URL through the Clipboard API, with a selection/copy fallback for older browsers.

## Analytics and map behaviour

The overview renders total/open/closed/location KPIs, average and approximate p50 handling time, timeline, category, source, area and status charts. Every chart has a textual summary. `null` means unavailable and remains distinct from numeric zero. Loading, empty and retryable error states stay local to the affected panel.

Leaflet is lazy-loaded. The map sends a `geo_bounding_box` only after **Zoek in dit kaartgebied**: low zoom receives clusters; high zoom receives points. A synchronized list is always available as a keyboard-friendly fallback. Truncation is explicit. OpenStreetMap attribution stays visible and the default public tile endpoint is intended only for local demonstration; production should configure an approved tile provider through `VITE_OSM_TILE_URL`.

Map locations are public source coordinates intended for visualization and may be rounded. Records without valid coordinates remain searchable but do not appear on the map. No address, reporter data, free text or original payload is exposed.

A successful manual publish only confirms Kafka accepted the event. Kafka processing and Elasticsearch indexing are asynchronous, so it may not be searchable immediately; the UI never polls indefinitely.

## Commands

```powershell
Set-Location frontend
npm install
npm run dev
npm run test
npm run build
```

Default local ports are frontend `5173`, backend `8080`, containerized frontend `8081`, Elasticsearch `9200`, Kafka `9092` and PostgreSQL `5432`. `VITE_API_BASE_URL` defaults to `http://localhost:8080`.

Known limits: analytics interval is currently fixed to day in the UI, cluster cells are server-defined rather than client-merged, the result list uses previous/next pagination, and the in-memory admin session intentionally disappears on refresh.

## Reference views

![Desktop dashboard](images/geospatial-dashboard-desktop.png)

![Mobile dashboard](images/geospatial-dashboard-mobile.png)
