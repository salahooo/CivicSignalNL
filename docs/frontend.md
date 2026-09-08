# Frontend dashboard

The React + TypeScript dashboard is intentionally small: `App` contains the dashboard header, publish form, search filters, results and architecture panel. `api.ts` is the only API boundary and uses `VITE_API_BASE_URL`, with `http://localhost:8080` as a local fallback.

Search state (`q`, category, district, page and size) is written to URL query parameters. A shared or refreshed URL therefore restores the same backend search request. Errors show stable user-facing messages only; HTTP 503 is treated as a temporary availability issue.

A successful publish only confirms Kafka accepted the event. Kafka processing and Elasticsearch indexing are asynchronous, so the new report may not be searchable immediately. Users can press Search again; the application does not poll indefinitely.

## Commands

```powershell
Set-Location frontend
npm install
npm run dev
npm run test
npm run build
```
