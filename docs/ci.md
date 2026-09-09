# Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests targeting main. Permissions are limited to repository read access. A newer run cancels the older run for the same workflow/ref; each job has a finite timeout.

The backend job uses Temurin Java 21, Maven Wrapper, a Maven dependency cache and `verify` (all ordinary backend tests). The explicit Elasticsearch integration smoke remains opt-in; infrastructure-dependent end-to-end validation is provided locally by the Compose smoke.

The frontend job uses Node 24.15.0, the committed npm lockfile, `npm ci`, ESLint, TypeScript, all Vitest tests and a production build. The Compose job validates configuration without printing secrets and builds both runtime images. It generates a disposable database value solely to satisfy Compose interpolation; no external CI secrets are required.

Actions use stable major versions and caches are scoped by dependency manifests. Build output, npm modules, test reports and environment files are ignored. No deployment, publishing or cloud resources are part of the workflow.

Local equivalents: `backend/mvnw verify`, the frontend npm scripts, `docker compose config --quiet`, `docker compose build backend frontend`, and `node scripts/compose-smoke.mjs --skip-build`. CI execution itself is not watched or triggered by the local development task.
