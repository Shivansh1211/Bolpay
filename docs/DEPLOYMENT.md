# Deployment preparation: Vercel + Render Free

This is a deployment recipe only. No provider account has been accessed, no cloud resources have been created, and no deployment has been run. Render Free is suitable for a temporary demo, not durable production: free web services sleep after 15 minutes without inbound requests and take about a minute to wake; there are 750 shared free instance hours per workspace/month. Free Postgres is limited to 1 GB and expires 30 days after creation, with a 14-day upgrade grace period; it has no managed backups. Do not add keep-alive pingers. Warm services only before a demo. Check current workspace usage before creating anything. [Render Free limits](https://render.com/docs/free)

Render's Blueprint syntax below follows its current `render.yaml` reference (`runtime: docker`, `plan: free`, `healthCheckPath`, `fromDatabase`, `fromService`, `sync: false`, `generateValue`, and `autoDeployTrigger: checksPass`). The three web services and database are pinned to Singapore so they share a region. Free services cannot receive private network traffic, so backend-to-mock calls and mock-to-backend callbacks use the services' public HTTPS URLs (`RENDER_EXTERNAL_URL`); Postgres uses its private connection string. Each web service is configured to auto-deploy only after linked GitHub CI checks pass. [Blueprint reference](https://render.com/docs/blueprint-spec), [Docker deploys](https://render.com/docs/docker), [Render default environment variables](https://render.com/docs/environment-variables)

Vercel builds this Vite SPA from the `frontend` project root. The checked-in `frontend/vercel.json` uses Vercel's documented SPA rewrite to `index.html`, allowing `/v/{slug}` and `/o/{code}` to load after refresh or QR scan. Vite `VITE_*` values are embedded at build time and must be set before a new production deployment. [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite), [Vercel environment variables](https://vercel.com/docs/environment-variables)

## Exact setup order

1. Push this branch to your **private** Git repository. Review the staged diff first; never include `.env`, test artifacts, OTPs, provider credentials, or local database dumps.
2. In Render, create a Blueprint from that repository and `render.yaml`. Choose the free plans and Singapore for all services/database. Enter only the prompted `sync: false` secret values in the Render dashboard. Render generates the shared mock client secret; JWT, webhook, ML API, mock-admin and invite secrets are entered by you. During this first deployment the backend CORS and public base URL intentionally point to `https://SET-AFTER-VERCEL-DEPLOY`; replace them after Vercel creates its production URL.
3. In Render, note the public HTTPS URLs for `bolpay-backend`, `bolpay-ml-service`, and `bolpay-mock-cashfree`. Confirm each `/health` returns HTTP 200. Do not paste URLs containing credentials; service URLs have no credentials.
4. Create a Vercel project connected to the same private repository. Set **Root Directory** to `frontend`, framework preset Vite, build command `npm run build`, output directory `dist`. Add the Vercel environment values in the table below and deploy. Record the production origin, such as `https://your-project.vercel.app`.
5. In Render, set backend `CORS_ALLOWED_ORIGINS` to exactly that production origin (no trailing slash, no preview domains) and backend `PUBLIC_BASE_URL` to the same origin. Redeploy the backend. `PUBLIC_BASE_URL` is used for customer return links; Vercel `VITE_PUBLIC_BASE_URL` is the compile-time equivalent used to encode QR URLs. Keep both equal. Set Vercel `VITE_API_BASE_URL`, `VITE_WS_URL`, and `VITE_MOCK_URL` to the recorded Render URLs and redeploy Vercel if those values were not available at its first build. `VITE_WS_URL` is entered as `wss://<backend-host>/ws`; the client converts that base to HTTPS for SockJS negotiation, which uses WSS for its WebSocket transport and keeps HTTPS fallback transports available.
6. Before a demo, run `tests/warmup.ps1` with the four public URLs, then `tests/remote-smoke.ps1` with those URLs. For the remote registration test only, temporarily enable `DEMO_OTP=true` in Render after `INVITE_CODE` is set; the app labels the returned code as `demo login`. Turn `DEMO_OTP` back off and redeploy after the smoke test. Production OTP delivery is not configured, so ordinary OTP login is not usable until a delivery provider is implemented and configured.

### CI-gated Render deploys and Vercel previews

- `render.yaml` sets `autoDeployTrigger: checksPass` on the backend, ML and mock web services. Render's current Blueprint specification supports `checksPass`; in the Render Dashboard, confirm each Git-linked service is set to deploy **After CI Checks Pass** and that the required GitHub checks report success. Initial Blueprint creation still provisions the services, and later commits wait for checks.
- Vercel creates Preview deployments for non-production branches by default. This app's safe default is production-only builds: in **Project Settings → Build and Deployment → Ignored Build Step**, select **Only build production**. Keep backend CORS limited to the production Vercel origin. If you later want a preview, assign one stable branch-specific preview domain, add only that exact origin to `CORS_ALLOWED_ORIGINS`, configure separate Preview `VITE_*` values deliberately, and remove the origin after testing. Never use a wildcard CORS origin. [Render deploy triggers](https://render.com/docs/deploys), [Vercel Git deployments](https://vercel.com/docs/git), [Vercel project settings](https://vercel.com/docs/project-configuration/project-settings)

The frontend directly opens the public mock URL for checkout. Browser payment controls call the mock service on that same origin; the mock signs callbacks and sends them to the public backend URL. Backend service credentials are sent only server-to-server. The mock admin panel and ML API are independently protected. Mock orders are in process memory and disappear on restart/sleep/redeploy, so create and complete demo orders while that service is awake. These are demonstration services; this configuration does not connect a real payment processor or SMS provider.

## Environment variables

Set secrets in provider dashboards only. The names below describe ownership and exposure; never place secret values in source control or test output.

| Variable | Service | Secret? | Set by / purpose |
|---|---|---:|---|
| `DATABASE_URL` | Render backend | Yes | Blueprint reference to Postgres private connection string; entrypoint converts `postgresql://` to JDBC form without printing it. |
| `POSTGRES_USER`, `SPRING_DATASOURCE_PASSWORD` | Render backend | Yes | Blueprint references to generated database user/password. |
| `JWT_SECRET` | Render backend | Yes | Enter a randomly generated, high-entropy value through the dashboard prompt; keep it stable across restarts. |
| `CASHFREE_WEBHOOK_SECRET` | backend + mock | Yes | Enter through the dashboard prompt on backend; mock receives it through a service reference. This is a mock signing key, not a real gateway credential. |
| `CASHFREE_CLIENT_ID` | backend + mock | No | Blueprint value shared with mock for its server-to-server API. |
| `CASHFREE_CLIENT_SECRET` | backend + mock | Yes | Render generates it on backend and references it in mock. |
| `CASHFREE_BASE_URL` | backend | No | Render service reference to the mock's public `RENDER_EXTERNAL_URL`. |
| `BACKEND_URL` | mock | No | Render service reference to backend's public `RENDER_EXTERNAL_URL` for callbacks. |
| `ML_SERVICE_URL` | backend | No | Render service reference to the ML public URL. The backend currently does not call the ML service; this is configured for the existing property only. |
| `ML_API_KEY` | Backend and ML service | Yes | Enter the same secret in both Render services' `sync: false` prompts; backend sends it as `X-API-Key`, and ML requires it on all routes except `/health`. |
| `MOCK_ADMIN_PASSWORD` | mock | Yes | Enter using Render's `sync: false` prompt; Basic authentication protects `/mock-admin` and `/admin/attack/*`. |
| `INVITE_CODE` | backend | Yes | Enter using Render's `sync: false` prompt; required to register when set. Leave it unavailable to users except the intended invitees. |
| `DEMO_OTP` | backend | No | Default `false`. Only turn on temporarily for a demo when `INVITE_CODE` is configured; returned OTP is labelled `demo login`. |
| `OTP_RETURN_TO_CLIENT` | backend | No | Must remain `false` on hosted deployments; retained for local automated test setup. |
| `PAYMENT_GATEWAY` | backend | No | Blueprint sets `mock`; the mock is not a real payment processor. |
| `PUBLIC_BASE_URL` | backend | No | Set to the exact Vercel production origin after first Vercel deploy; return links. |
| `CORS_ALLOWED_ORIGINS` | backend | No | Set to that same single production origin only. Do not add Vercel preview origins unless separately requested. |
| `EXPIRE_AFTER` | backend | No | Blueprint uses 150 seconds for demo orders. |
| `VITE_API_BASE_URL` | Vercel production build | No | `https://<bolpay-backend-host>`; API origin. |
| `VITE_WS_URL` | Vercel production build | No | `wss://<bolpay-backend-host>/ws`; authenticated live board socket. |
| `VITE_MOCK_URL` | Vercel production build | No | `https://<bolpay-mock-cashfree-host>`; public customer checkout origin. Never use Render's private hostname. |
| `VITE_PUBLIC_BASE_URL` | Vercel production build | No | Exact production Vercel origin; QR cards encode `/v/{slug}` on this origin. |

Render's `sync: false` values are requested during initial Blueprint creation and must be added manually in the dashboard if introduced on later Blueprint syncs. Blueprint-generated values and service/database references follow Render's current behavior. [Render env vars and secrets](https://render.com/docs/configure-environment-variables)

## Flyway and database lifecycle

Spring Boot starts Flyway against the empty database on initial backend boot (`spring.flyway.enabled: true`) before Hibernate validates the schema. Confirm the first backend logs show migrations applied and `/health` passes. A migration failure should leave the deployment unhealthy; inspect the error and fix forward before retrying. Do not delete the database or alter an applied migration.

Free Postgres has no Render-managed backups and expires 30 days after creation. Make and verify an external backup early, then repeat before the expiry date. Download the external Postgres connection string from the Render dashboard into a local environment variable without echoing it. Example in PowerShell (from a trusted machine with PostgreSQL client tools):

```powershell
$env:PGCONNECT_TIMEOUT = '15'
pg_dump --format=custom --no-owner --file=bolpay-backup.dump $env:RENDER_EXTERNAL_DATABASE_URL
pg_restore --list bolpay-backup.dump
```

For a restore, create a **separate** compatible PostgreSQL database, set its connection string in the same environment variable, then run:

```powershell
pg_restore --clean --if-exists --no-owner --dbname=$env:RENDER_EXTERNAL_DATABASE_URL bolpay-backup.dump
```

The dump contains customer/vendor/order data and must be stored encrypted with restricted access. Test restore before relying on it. Do not run restore against the only live database. Follow the database provider's current connection/TLS instructions and check `pg_dump` client major-version compatibility.

## Warm-up and remote smoke

Run only shortly before a demo; requests keep free services active and count toward shared workspace usage.

```powershell
pwsh -NoProfile -File tests/warmup.ps1 `
  -FrontendUrl 'https://<vercel-origin>' `
  -BackendUrl 'https://<backend-host>' `
  -MockUrl 'https://<mock-host>' `
  -MlUrl 'https://<ml-host>'

pwsh -NoProfile -File tests/remote-smoke.ps1 `
  -FrontendUrl 'https://<vercel-origin>' `
  -BackendUrl 'https://<backend-host>' `
  -MockUrl 'https://<mock-host>' `
  -MlUrl 'https://<ml-host>'
```

The second script asks for the invite code without echoing it. It uses the explicit demo OTP response to create a synthetic test vendor/order, exercises the public mock checkout and signed callback, and checks unauthorized paths and WebSocket auth. It prints check names and PASS/FAIL only; it never prints OTPs, invite codes, JWTs, or secret headers. It leaves one synthetic vendor and order in the database. The REST `/health` checks do not keep a service awake indefinitely.

## Common failures and recovery

| Symptom | Likely cause | Fix |
|---|---|---|
| Render Blueprint rejects `render.yaml` | Unsupported plan/region field or YAML indentation | Compare the error with the linked Blueprint schema; keep all resources in one available region and re-run local YAML validation. |
| Render does not deploy a pushed commit | `checksPass` is waiting for required GitHub checks or a check failed | Open the repository Actions/Checks view, fix the failing job, and confirm all expected checks complete successfully. Do not switch to deploy-on-commit to bypass CI. |
| Backend health stays red or Flyway fails | Missing/incorrect database references, cold database, or migration/schema mismatch | Inspect startup logs for the first Flyway/connection error; verify internal `DATABASE_URL`, DB user/password references, and region. Apply a reviewed forward fix; never edit an applied migration. |
| Vercel `/v/...` or `/o/...` returns 404 | Wrong Vercel project root or missing rewrite | Set Root Directory to `frontend`; verify `frontend/vercel.json` was included and redeploy. |
| Browser API calls fail with CORS | Wrong or trailing-slash origin, or stale frontend build | Set backend CORS to exactly the Vercel production origin, set the matching Vite API URL, redeploy backend and rebuild Vercel. |
| Vendor board stays offline | `VITE_WS_URL` missing/wrong, JWT rejected, or backend waking | Use `wss://<backend>/ws`; inspect browser console and backend socket auth logs; warm backend and retry login. Never relax topic authorization. |
| Checkout page is blank/404 | `VITE_MOCK_URL` is missing or mock still waking | Set the public HTTPS mock URL in Vercel and rebuild; warm the mock before the demo. Do not use an internal Docker/Render hostname in browser code. |
| Mock says invalid client or webhook rejected | Backend/mock shared credentials differ or backend URL is stale | Check Blueprint references; sync the Blueprint and redeploy both services. Do not print the secret while debugging. |
| ML returns 401 | Missing/wrong `X-API-Key` | Use the ML key only in server-to-server calls. Never expose it as a Vite variable. |
| OTP response contains no code | `DEMO_OTP` is off | Expected hosted default. Production OTP delivery is not implemented. For the smoke test only, set `DEMO_OTP=true` with `INVITE_CODE` set, then turn it off again. |
| First request takes about a minute or times out | Render Free service slept after 15 minutes | Run warm-up before a demo and wait for each `healthy` result. Do not add an external pinger. |
| Database is expired, unreachable, or near 30 days | Free Postgres lifecycle limit | Restore the encrypted dump to a new database and update the Blueprint reference; before expiry either export data or manually choose an affordable durable database plan. |

## Rollback

1. Stop sharing the QR/link and note the current source commit and deployment identifiers.
2. Roll Vercel back to the last working frontend deployment, or redeploy its prior reviewed commit.
3. Roll Render backend/mock/ML to the last known-good deploys using Render's dashboard. Check that Blueprint-linked service URLs and generated secrets remain consistent.
4. Preserve the database. Do not roll back by deleting it or editing applied migrations. If a schema change is incompatible, restore the verified dump to a separate database and point a reviewed backend deployment at that copy.
5. Re-run health checks, the remote smoke flow, customer status lookup, and live vendor board before sharing the QR again.

## Boundaries and known gaps

No deployment or provider account action has occurred. No real SMS provider, live gateway, production payments, real-phone test, or vendor survey is included. Free Render services are single-instance, 512 MB / 0.1 CPU, sleep when idle, and share workspace limits; four free web services plus Vercel may not fit workspace/build/bandwidth quotas. If the dashboard requires a card, changes terms, or presents a paid plan, stop; this recipe assumes a $0/no-card path and does not authorize billing. Service URLs and current free plan availability should be checked in the dashboards before applying the Blueprint. The demo is not production-ready and makes no scam-prevention claim.
