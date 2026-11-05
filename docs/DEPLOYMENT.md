# Deployment preparation

Status: **prepared, not deployed**. No hosting provider, domain, payment account, or SMS provider has been selected. This document does not claim that the app is ready for live payments.

## Proposed split

- **Frontend:** Vercel static hosting with the project root set to `frontend`, framework preset Vite, build command `npm run build`, and output directory `dist`. `frontend/vercel.json` sends application routes to `index.html`, including `/v/{slug}` and `/o/{code}` when refreshed.
- **Backend, PostgreSQL, ML, and optional mock:** a Docker-capable host. Options to evaluate are a small managed Docker/container service with private service networking and persistent disks; a general-purpose VM where Docker Compose and backups are operated by the project owner; or an existing organization-managed Docker host. Each option needs a documented PostgreSQL backup/restore procedure and private networking. No option is selected here.
- **Payment gateway:** choose sandbox or live configuration only after the owner selects a provider/account and validates the gateway's current webhook signing requirements.

`docker-compose.production.yml` is a starting template. It keeps the database, backend, and ML service on a private Compose network and publishes the Nginx frontend only on host loopback. For the split Vercel setup, configure the backend host's TLS reverse proxy to expose only required `/api/` and `/ws` endpoints, and use the Vercel domain as the exact allowed browser origin. Do not expose PostgreSQL or the ML service publicly.

## Build and runtime environment

Configure Vercel build variables in the project settings. Keep backend credentials in the Docker host's secret manager. Values below are names and purposes only; this document contains no credentials.

| Variable | Where | Purpose / setting |
|---|---|---|
| `VITE_PUBLIC_BASE_URL` | Vercel build | Public frontend HTTPS origin, used to render vendor QR links. |
| `VITE_API_URL` | Vercel build | HTTPS origin of the backend API, e.g. `https://api.example.invalid`; no trailing `/api` because the client adds route paths. |
| `VITE_WS_URL` | Vercel build | HTTPS SockJS endpoint, e.g. `https://api.example.invalid/ws`. SockJS starts over HTTPS and negotiates the secure WebSocket (`wss`) transport through the proxy. Do not put a raw `wss://` URL here unless the client library is changed and verified. |
| `PUBLIC_BASE_URL` | Backend | Exact frontend HTTPS origin used for return links. |
| `CORS_ALLOWED_ORIGINS` | Backend | Exactly the Vercel frontend origin, with no wildcard or unrelated origins. Apply the same allowed-origin policy to the WebSocket handshake. |
| `POSTGRES_DB` | Backend/DB | Database name. |
| `POSTGRES_USER` | Backend/DB | Database user; use a dedicated least-privilege account. |
| `POSTGRES_PASSWORD` | Backend/DB secret | Unique database password from the host secret manager. |
| `SPRING_DATASOURCE_PASSWORD` | Backend | Set by the Compose template from `POSTGRES_PASSWORD`; do not duplicate it in source control. |
| `JWT_SECRET` | Backend secret | Unique random secret of at least 256 bits. Replace the development fallback. |
| `PAYMENT_GATEWAY` | Backend | Explicitly select a configured sandbox/live gateway. The production template refuses to guess. |
| `CASHFREE_BASE_URL` | Backend | Gateway API base URL for the selected environment. |
| `CASHFREE_CLIENT_ID` | Backend secret | Gateway client ID. |
| `CASHFREE_CLIENT_SECRET` | Backend secret | Gateway client secret. |
| `CASHFREE_WEBHOOK_SECRET` | Backend secret | Gateway webhook signing secret. |
| `CASHFREE_API_VERSION` | Backend (optional) | Gateway API version; current application default is `2025-01-01`, verify against the selected gateway account. |
| `ML_SERVICE_URL` | Backend | Internal URL, normally `http://ml-service:5000`; do not use a public URL. |
| `TRUSTED_PROXIES` | Backend (optional) | Only the exact trusted reverse-proxy addresses, after confirming what source addresses the host presents. |
| `FRONTEND_HTTP_PORT` | Compose host (optional) | Loopback-only local frontend port for a same-host TLS proxy. |
| `OTP_RETURN_TO_CLIENT` | Backend | Must remain `false` outside explicit local development. |
| `MOCK_ADMIN_PASSWORD` | Mock service only | If the mock service is enabled for a controlled demo, set a unique high-entropy password in the host secret store. The mock returns 404 for admin routes when it is unset. Do not use a default, commit it, or expose mock admin routes to the public internet. The production Compose file intentionally does not include the mock service. |
| `INVITE_CODE` | Not implemented | The backend currently has no `INVITE_CODE` setting or registration check. Do not assume setting this variable gates registration. Add and test server-side invite validation before exposing vendor registration publicly. |

The app does not yet integrate SMS delivery; production OTP delivery must be selected and implemented before real vendor registration. The login/register pages also have local-demo OTP presentation paths, so verify the non-dev production build and responses before inviting users.

## WebSocket and origin configuration

Set the frontend API base to the backend's HTTPS origin and the SockJS endpoint to `https://<backend-host>/ws`. The TLS reverse proxy must pass the SockJS HTTP requests and WebSocket upgrade, forward `Upgrade` and `Connection` headers, and use a read timeout long enough for active board sessions. Browsers negotiate WSS from the HTTPS SockJS URL. Confirm the backend only accepts the selected frontend origin and its STOMP CONNECT still requires a valid JWT; do not widen topic authorization.

The current production Nginx file assumes frontend and backend share a Compose network. Vercel is a separate origin, so a Docker-host reverse proxy or ingress must provide the public backend TLS endpoint and forward `/api/` and `/ws` to the private backend. Do not place a wildcard CORS rule in the frontend proxy as a workaround.

## Cold starts and service availability

Some container hosts may pause an idle service. The current app has generic loading states; it does not implement a dedicated “Waking up the server…” message or a tested retry/backoff flow. Before using a host that can sleep, add a user-visible waking state, retry safely for idempotent reads, and explain when the user should try again. Do not automatically resubmit payment creation or OTP requests on retries. Test cold start from a real phone before opening traffic.

## Manual deployment checklist

- [ ] Choose the frontend and Docker-hosting options; set a domain and DNS/TLS plan.
- [ ] Configure the Vercel project root as `frontend`; build with `npm run build` and publish `dist`.
- [ ] Set Vercel `VITE_PUBLIC_BASE_URL`, `VITE_API_URL`, and `VITE_WS_URL` to the final HTTPS origins; verify refresh on `/v/{slug}` and `/o/{code}`.
- [ ] Configure host secrets; replace all development credentials and use distinct production secrets. Never paste them into Git or logs.
- [ ] Set `PUBLIC_BASE_URL` to the frontend origin and `CORS_ALLOWED_ORIGINS` to that single exact origin.
- [ ] Configure the public reverse proxy for TLS, `/api/`, SockJS `/ws`, WebSocket upgrade, forwarded headers, and suitable idle timeout. Keep database, backend, and ML ports private.
- [ ] Ensure the ML container is reachable only on the internal network; confirm no public route reaches `/score` or `/model-info`.
- [ ] Decide whether to use mock mode for an isolated staging demo. If enabled, configure `MOCK_ADMIN_PASSWORD` in a secret store and block public admin paths; never treat the mock as a live gateway.
- [ ] Implement and test `INVITE_CODE` enforcement before allowing public registration; configure real SMS delivery and confirm OTP values never reach users' logs or default API responses.
- [ ] Register the final gateway webhook URL, verify signatures using current provider documentation, and test duplicate, stale, malformed, wrong-amount, and delayed events in sandbox.
- [ ] Take a PostgreSQL backup and prove restore to a separate database before the first release.
- [ ] Run `pwsh -NoProfile -File tests/run-all.ps1`, then stage the release and inspect server/browser logs for secrets and tokens.
- [ ] On a real phone, test both a vendor and customer over mobile data, QR scanning, amount confirmation, paid/expired/late states, screen brightness, reconnect after temporary network loss, and Hindi/English. Record device, browser, host, and outcome by hand.
- [ ] Conduct the vendor survey by hand; it has not been performed.

## Rollback plan

1. Before release, record the exact source commit and image digests, retain the previous frontend build and backend image, and take a verified PostgreSQL backup.
2. If a release causes errors, stop new traffic at the edge and switch Vercel to the previous frontend build; restore the previous backend image through the host's normal rollout mechanism.
3. Keep the database volume intact. Do not delete a volume or edit an applied migration as a rollback technique. Prefer backward-compatible expand/contract migrations. If the prior backend cannot read the migrated schema, stop traffic and restore the backup to a separate database for a reviewed recovery; do not overwrite the only current database copy.
4. Verify health, login, order lookup, and WebSocket board update on the recovered version before reopening traffic. Record the incident, database state, and recovery decision.

## What remains manual or incomplete

The hosting provider and payment account choice require the owner. Deployment, real-phone testing, and the vendor survey have not happened. `INVITE_CODE` validation and production SMS are absent. The dedicated cold-start message/retry behavior is also absent. This document is preparation only and makes no production-readiness or scam-prevention claim.
