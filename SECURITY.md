# Security notes

Public search, analytics, map and publishing requests never contain admin credentials. Administrative requests use a separate HTTP client and stateless HTTP Basic over the configured backend origin. Credentials live only in React memory, are not written to URLs or browser storage, disappear on refresh and are cleared after HTTP 401.

Use HTTPS outside local development, replace all example credentials through environment configuration and restrict CORS to trusted frontend origins. Do not place secrets in `VITE_*` variables: Vite embeds those values in the public bundle.

The dashboard deliberately excludes addresses, reporter details, free text and raw source payloads. Map points are limited to public visualization coordinates and may be rounded. OpenStreetMap's public tile service is suitable for bounded local demonstration only; configure an approved provider and follow its attribution, privacy and usage policy for production.

Report suspected vulnerabilities privately to the repository owner. Do not include personal data, live credentials or production payloads in an issue.
