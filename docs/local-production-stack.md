# Local production stack

The operational workflow adds Flyway V2 and the internal `civic-reports.workflow` Kafka topic. Normal local/Compose outbox publication is enabled with `CIVICSIGNAL_OUTBOX_ENABLED=true`; source import and scheduler defaults remain off. Readiness also checks the workflow topic. Run `node scripts/compose-smoke.mjs --workflow` for a complete dossier/outbox verification. This mode disables only its own automatic outbox worker to control the temporary Kafka-failure scenario. Existing named volumes and other Compose projects are untouched.

Backend tests now include an automatically cleaned PostgreSQL Testcontainer and need a running Docker Engine. The Java test dependency is pinned to Testcontainers 1.21.4 for modern Docker compatibility. Dossier/outbox UI uses the same Nginx origin and memory-only admin client as the existing dashboard.

This is a production-shaped local demonstration, not an internet-facing deployment. Requirements: Docker Engine/Desktop with Compose v2.24.4 or newer, about 4 GB of available Docker memory, and Node 24.15.0 for the smoke script. Java/Maven are built inside the backend image.

## Start and stop

From the repository root, inject generated credentials without committing them:

```powershell
$env:POSTGRES_PASSWORD = [guid]::NewGuid().ToString('N')
$env:CIVICSIGNAL_ADMIN_PASSWORD = [guid]::NewGuid().ToString('N')
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 240
docker compose ps
docker compose stop
```

Open `http://localhost:8081`; login is under Beheer with username `admin` and the password supplied in your environment. Reuse your existing PostgreSQL volume's password on subsequent starts: changing `POSTGRES_PASSWORD` does not rotate a database initialized earlier. Keep the generated value in a local password manager or ignored `.env` file. Do not regenerate it against an existing volume.

Empty admin credentials intentionally disable admin access. A configured Compose admin password must have at least 16 characters. Startup rejects an empty database password. Amsterdam import, scheduler and generator remain disabled by default. `docker compose stop` preserves named volumes. Do not use `down -v` against data you wish to retain.

## Configuration

| Setting | Purpose |
| --- | --- |
| `POSTGRES_PASSWORD`, `POSTGRES_USER`, `POSTGRES_DB` | Runtime database credentials/name; password is required |
| `CIVICSIGNAL_ADMIN_USERNAME`, `CIVICSIGNAL_ADMIN_PASSWORD` | Optional admin account; blank password disables it |
| `FRONTEND_PORT` | Loopback frontend port, default 8081 |
| `SPRING_PROFILES_ACTIVE` | `local` by default; Compose sets `compose`; `dev` aliases local |
| `KAFKA_BOOTSTRAP_SERVERS` | Compose uses `kafka:9092` |
| `ELASTICSEARCH_URIS` | Compose uses `http://elasticsearch:9200` |
| `SPRING_DATASOURCE_URL` | Compose uses `jdbc:postgresql://postgres:5432/civicsignal` |
| `CIVICSIGNAL_AMSTERDAM_ENABLED`, `CIVICSIGNAL_AMSTERDAM_SCHEDULER_ENABLED` | Opt-in source and scheduler switches |
| `KAFKA_MAX_BLOCK_MS`, `KAFKA_REQUEST_TIMEOUT_MS`, `KAFKA_DELIVERY_TIMEOUT_MS` | Bounded producer waits |
| `ELASTICSEARCH_CONNECT_TIMEOUT`, `ELASTICSEARCH_REQUEST_TIMEOUT`, `SHUTDOWN_TIMEOUT` | Connect/request/graceful-shutdown bounds |

Spring properties can be overridden by their environment equivalents in a local Compose override. The full source, generator and scheduler environment mapping remains in `application.yml`. Container image builds accept no credentials. Frontend builds use `same-origin`; the browser sends API requests back to Nginx, so browser CORS setup is unnecessary.

For Docker secrets, the compose profile imports `/run/secrets/` through Spring configtree. Mount an admin secret with target `civic-signal.admin.password` and a database secret with target `spring.datasource.password`; omit the corresponding empty environment entries in your private Compose override so they cannot override the secret. PostgreSQL separately supports `POSTGRES_PASSWORD_FILE`. The database secret and backend secret must agree. Do not commit the files or an override containing their values.

## Host development

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d kafka kafka-init postgres elasticsearch
Set-Location backend
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
.\mvnw.cmd spring-boot:run
```

In another terminal, `cd frontend`, `npm ci --no-audit --no-fund` and `npm run dev`. The override opens only loopback Kafka 9092, Elasticsearch 9200 and PostgreSQL 5435. Local backend defaults match those endpoints. Vite uses `http://localhost:8080`; the local CORS policy allows Vite and explicitly supports Authorization and request-ID headers. Compose remains independent of this policy.

## Controlled smoke

```powershell
node scripts/compose-smoke.mjs
# After images have already been built:
node scripts/compose-smoke.mjs --skip-build
```

The script owns project `civicsignal-readiness-smoke`, port 18081 and fresh tmpfs data. It refuses to reuse running smoke containers. It generates credentials in process memory, starts all six services, checks the frontend/SPA proxy, probes, authentication, public publication, Kafka header propagation, indexing, exact search, summary, map points/clusters, malformed-event DLT, migration tables, non-root runtime, safe structured logs and container health. It removes its Elasticsearch documents, always stops its containers, then removes only the smoke containers and their anonymous image volumes. Kafka fixtures, DLT records and PostgreSQL fixture state disappear with tmpfs; existing named volumes are never deleted. Re-running starts with clean data. Kafka explicitly uses the mounted log directory, rather than the image's writable-layer default.

## Troubleshooting

- `docker compose ps -a` identifies failed initialization or unhealthy services. `kafka-init` should exit with code 0; the other five services should be healthy.
- A 503 readiness status means an essential dependency or the geo mapping is unavailable. Authorized `/actuator/metrics` and `docker compose logs backend` help diagnose it; public health deliberately hides details.
- Database authentication failure after a password change requires the original database password or deliberate credential rotation, not volume deletion.
- Upgrading an older stack: its Kafka image may have written to `/tmp/kafka-logs` inside the container despite the named mount. Back up or migrate any needed old broker data before recreating that container. This configuration explicitly sets `KAFKA_LOG_DIRS` to the named mount; the smoke never migrates or modifies another project's data.
- If the backend is unhealthy after Elasticsearch first boot, inspect resource limits and `docker compose logs elasticsearch`; restart backend after the dependency is ready if index initialization failed.
- A failed DLT publication or exhausted DLT inspection retry stops listeners and latches readiness DOWN. Restore Kafka/Elasticsearch as needed, then restart backend; source offsets remain unacknowledged.
- Port 18081 is reserved by the smoke, while the normal stack defaults to 8081. Stop an earlier smoke before starting another.
- Graceful stop allows up to 60 seconds in normal Compose. If the broker or DLT is unavailable, unacknowledged records remain in Kafka for a later restart.
