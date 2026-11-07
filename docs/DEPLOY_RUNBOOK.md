# BolPay deployment runbook (owner actions only)

No provider account, cloud resource, or credential has been accessed by this change. Payment remains mocked. This configuration uses an external Neon Postgres database and three Render Free Docker web services. Render free web services can sleep and share workspace limits; check the current [Render Free limits](https://render.com/docs/free) before creating resources. This is not a production-readiness statement.

## Render project and environment

Create the Blueprint in Render Project **Bolpay**, environment **Production**. The Blueprint declares those exact project/environment names and assigns all three web services and both environment groups there. In the Render Dashboard, choose the workspace, open **Projects → Bolpay → Production**, then select **New → Blueprint Instance** (or create the project/environment first if absent), connect the private repository, select `main` and `render.yaml`, and review the proposed changes. The plan should contain only `bolpay-backend`, `bolpay-ml-service`, and `bolpay-mock-cashfree`, all Docker/free in Singapore. It must not contain a Render database.

Render supports `envVarGroups` and `fromGroup` references in Blueprints. Secret values cannot be prompted using `sync: false` inside an environment group. Create the groups and enter their values in the dashboard before syncing:

1. In **Environment Groups → New Environment Group**, create `bolpay-webhook-secret` and add key `CASHFREE_WEBHOOK_SECRET` with a value you generate and store privately.
2. Create `bolpay-ml-secret` with key `ML_API_KEY` and a privately generated value.
3. Move each group under **Manage → Move group** into Project **Bolpay**, environment **Production**, then link it to the services declared in `render.yaml` (backend+mock for webhook, backend+ML service for ML key). Never enter either value in Git, chat, or a terminal command.
4. Only after both groups exist with their keys and are linked, sync/create the Blueprint. If Render does not offer the existing groups during initial Blueprint creation, cancel that sync, create them as above, then retry.

The Blueprint's initial secret form asks for these service-only values in declaration order: backend `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `INVITE_CODE`; then mock service `MOCK_ADMIN_PASSWORD`. Render may visually group prompts by service, but these are the only `sync: false` entries. Shared webhook/ML credentials are entered once in their groups, not in the form. Render generates `CASHFREE_CLIENT_SECRET`. Do not paste credentials into a URL: username and password are separate fields.

## Neon connection settings

In Neon, create/select the project and database, then copy the **direct** connection details (connection pooling unchecked). Set the backend-only `SPRING_DATASOURCE_URL` to this shape, replacing placeholders in the Render dashboard only:

```text
jdbc:postgresql://<direct-host>/<db>?sslmode=require&channelBinding=require
```

The direct host has no `-pooler` suffix. This application uses that single connection for Flyway and JPA. Neon’s pooler is transaction-mode PgBouncer; pooled connections can alter session behavior expected by database tooling. Use direct connections for schema migrations and dumps; Neon identifies pooled hosts by the `-pooler` suffix ([Neon connection pooling](https://neon.com/docs/connect/connection-pooling)). Hikari provides the app process’s small local pool of four connections. `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` hold the separate Neon credentials.

Spring Boot runs Flyway automatically on first boot before Hibernate validates the schema. Render logs should show the Flyway version, schema-history creation, and migrations V1 onward, followed by successful application startup. On an empty Neon database all checked-in migrations apply. If startup fails, inspect the first `Flyway`, `SQLState`, or `HikariPool` exception and the non-secret configuration error. Do not paste JDBC URLs or credentials into logs/issues. The app fails early with a clear message when `SPRING_DATASOURCE_URL` is missing or has a non-JDBC scheme; it never includes the URL value in that message.

The Hikari pool is capped at four (`minimumIdle=0`), connection wait is 20 seconds, validation is 5 seconds, and initial connection acquisition retries for up to 120 seconds. A positive Hikari `initializationFailTimeout` blocks startup while attempting an initial connection and then fails if the timeout is exceeded ([HikariCP configuration](https://github.com/brettwooldridge/HikariCP)). Neon may suspend an idle compute and wake on connection. If startup remains unhealthy after that window, inspect Neon endpoint status/region and Render logs, wait for Neon to become active, then use Render **Manual Deploy → Deploy latest commit**. Do not repeatedly rebuild the image for a transient database wake-up, and do not alter or delete an applied migration/database.

## Environment variable ownership

| Exact variable | Service | Secret? | Set by |
|---|---|---:|---|
| `SPRING_DATASOURCE_URL` | backend only | Yes | Render initial prompt; direct Neon JDBC URL, with TLS and channel binding required |
| `SPRING_DATASOURCE_USERNAME` | backend only | Yes | Render initial prompt; separate Neon username |
| `SPRING_DATASOURCE_PASSWORD` | backend only | Yes | Render initial prompt; separate Neon password |
| `JWT_SECRET` | backend | Yes | Render initial prompt; unique high-entropy value |
| `INVITE_CODE` | backend | Yes | Render initial prompt; registration gate |
| `MOCK_ADMIN_PASSWORD` | mock-cashfree | Yes | Render initial prompt; mock admin protection |
| `CASHFREE_WEBHOOK_SECRET` | backend + mock-cashfree | Yes | dashboard group `bolpay-webhook-secret`; same value for both |
| `ML_API_KEY` | backend + ml-service | Yes | dashboard group `bolpay-ml-secret`; same value for both |
| `CASHFREE_CLIENT_ID` | backend + mock-cashfree | No | Blueprint value/reference |
| `CASHFREE_CLIENT_SECRET` | backend + mock-cashfree | Yes | Render generates on backend and references into mock |
| `CASHFREE_BASE_URL` | backend | No | Render mock service public URL reference |
| `BACKEND_URL` | mock-cashfree | No | Render backend public URL reference |
| `ML_SERVICE_URL` | backend | No | Render ML service public URL reference |
| `PAYMENT_GATEWAY` | backend | No | Blueprint: `mock` |
| `DEMO_OTP` | backend | No | Blueprint: `false`; enable only briefly for explicitly labeled demo login, and only with invite code configured |
| `OTP_RETURN_TO_CLIENT` | backend | No | Blueprint: `false` |
| `PUBLIC_BASE_URL` | backend | No | Set to exact Vercel production origin after first Vercel deployment |
| `CORS_ALLOWED_ORIGINS` | backend | No | Set to only the exact Vercel production origin |
| `EXPIRE_AFTER` | backend | No | Blueprint: `150` seconds for demo order expiry |
| `VITE_API_BASE_URL` | Vercel frontend build | No | Owner: backend public HTTPS URL |
| `VITE_WS_URL` | Vercel frontend build | No | Owner: `wss://<backend-host>/ws` |
| `VITE_MOCK_URL` | Vercel frontend build | No | Owner: mock public HTTPS URL |
| `VITE_PUBLIC_BASE_URL` | Vercel frontend build | No | Owner: Vercel production origin used in QR codes |

The PostgreSQL JDBC driver is pinned to 42.7.14. This includes current pgJDBC fixes for strict channel-binding enforcement and subsequent authentication/buffer advisories ([pgJDBC security advisories](https://jdbc.postgresql.org/security/), [42.7.14 release](https://jdbc.postgresql.org/changelogs/2026-10-07-42.7.14-release/)). Spring Boot datasource and Hikari properties follow the official [Spring Boot SQL configuration](https://docs.spring.io/spring-boot/reference/data/sql.html). Local Docker Compose and CI retain their local Postgres service and configuration. No Neon connection value is checked into the repository.

## Vercel setup and final origin

Create the Vercel project from the private repository with root directory `frontend`, Vite, install `npm ci`, build `npm run build`, output `dist`. Add `VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_MOCK_URL`, and `VITE_PUBLIC_BASE_URL` as Production variables. The last value is only known after the first Vercel deploy; then set it and rebuild. In Render, update backend `CORS_ALLOWED_ORIGINS` and `PUBLIC_BASE_URL` to that exact origin and redeploy. Do not add preview origins without deliberate review. The Vercel SPA rewrite supports refresh on `/v/{slug}` and `/o/{code}`.

## Demo seed

The idempotent seed uses only fictional data and reads only `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` from the current PowerShell environment. It requires PostgreSQL client tools (`psql`). Local hosts (`localhost`, `127.0.0.1`, `::1`, and Compose's `postgres`) may use the local Compose URL without TLS query options. Remote hosts must use a direct Neon URL (no `-pooler`) with `sslmode=require` and `channelBinding=require`; username and password must remain outside the URL.

For local Docker, set those three variables in the shell to `jdbc:postgresql://localhost:5432/bolpay` (or the local mapped port), plus the local username/password; do not change Compose. For hosted Neon, set them in a private shell session from Render/Neon’s connection details without echoing values, then run:

```powershell
pwsh -NoProfile -File scripts/seed-demo.ps1
```

It seeds a fictional vendor `Demo Chai Cart` and two sample orders. It prints only a PASS line. It does not edit migration files.

## Neon backup and restore

Install PostgreSQL client tools compatible with the Neon server version. Use Neon’s direct endpoint for backups and restores. In a private PowerShell session, set `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` from your own Neon dashboard/secret manager; also set `PGSSLMODE=require`, `PGCHANNELBINDING=require`, and `PGCONNECT_TIMEOUT=20`. Do not put a password in a command, connection URL, script, or chat. These variables are not read or stored by this repository. Then run:

```powershell
pg_dump --no-password --format=custom --no-owner --no-privileges --file=bolpay-backup.dump
pg_restore --list bolpay-backup.dump
```

Store the dump encrypted with restricted access. To restore, create/select a **separate empty Neon database** and set the `PG*` environment variables to that target. Review the target before running:

```powershell
pg_restore --no-password --exit-on-error --no-owner --no-privileges --dbname=$env:PGDATABASE bolpay-backup.dump
```

The restore connects using the `PG*` environment settings. It intentionally does not use `--clean`; never restore over the only live database. Verify the restored schema and row counts before any planned cutover. Keep a backup before migration/deployment changes.

## Warm-up, smoke, and diagnosis

Before a demo, run `tests/warmup.ps1` and `tests/remote-smoke.ps1` with the deployed Vercel, backend, mock, and ML URLs. The remote smoke creates fictional test rows; it prompts for the invite code without echo. Temporarily enable `DEMO_OTP=true` only while `INVITE_CODE` is configured; turn it off immediately after the test. Do not print OTPs or credentials.

| Symptom/log clue | Likely cause | Action |
|---|---|---|
| `SPRING_DATASOURCE_URL is required` | Render variable absent | Set the backend-only URL in Environment and redeploy |
| URL must start with `jdbc:postgresql://` | Render URL pasted in libpq form or typo | Use the direct Neon hostname and JDBC prefix; do not embed username/password |
| `Connection refused`, DNS, timeout, Hikari initialization failure | Neon cold start, wrong endpoint/region, or network issue | Check Neon compute status and direct host, wait for wake, then manual deploy/retry after status is active |
| Flyway validation/checksum/migration error | Existing schema mismatch or failed migration | Read first Flyway error; use a reviewed forward-only fix. Never edit applied migrations or delete database volumes |
| `password authentication failed` | Username/password pair mismatch | Re-enter both in the backend's Render environment; never put them in a group |
| `channel binding required` / TLS error | Old PostgreSQL client/driver, URL options, or TLS interception | Confirm driver 42.7.14+, exact URL options, direct endpoint and TLS support |
| Render form asks for a database | Wrong Blueprint contents/branch selected | Stop; this Blueprint must have no `databases:` section |
| Render group key missing | Group was linked without creating its key first | Add the exact key in the scoped group, then save/redeploy linked services |
| health check remains red after DB migration | Spring startup failed after Flyway or DB unavailable | Inspect backend runtime logs from first DB error; correct config and deploy again |

## Rollback and manual boundary

No deployment is performed by this runbook. Roll back app services to their prior known-good deploys from dashboards. Keep the Neon database intact. If a schema change is incompatible, restore a verified backup into a separate database and point a reviewed backend release to it; do not rewrite migrations or delete data to make a deploy pass. Current live SMS/payment integration, real-phone testing, and vendor survey remain manual and unverified.

Owner actions: create/choose the Neon database and enter its three credentials only in Render; create two scoped secret groups and enter their values; select/create Render Project **Bolpay** → **Production** when creating the Blueprint; create the Vercel project and set public URLs; run the seed only if desired; warm up and run remote smoke; complete `docs/phone-test.md`. Do not push/deploy until you choose to do so.
