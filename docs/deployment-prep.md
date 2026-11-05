# Deployment preparation

Status: **prepared for review, not deployed**. No hosting provider has been selected. The configuration builds the existing containers, keeps PostgreSQL and the backend off public host ports, omits the mock payment service, and binds the frontend HTTP port to loopback for a separately configured TLS reverse proxy.

The current lockfile audit reports 4 vulnerable package groups (3 moderate, 1 high by npm package-group summary) across 11 advisory records. React Router findings reach the runtime bundle; Vite and esbuild findings are development/build-only. npm offers only major-version upgrade paths. Full details are in `docs/npm-audit.md`; review them before external use.

## Production Compose configuration

Use `docker-compose.production.yml` with `frontend/nginx.production.conf`. The frontend proxies `/api` and `/ws` to the private backend service. The frontend image accepts `VITE_API_URL` and `VITE_WS_URL`; blank values use the same-origin reverse proxy. Production builds use the production Nginx config, which contains no mock checkout/admin routes.

The file requires these variables from a host secret store or a protected, untracked environment file:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `JWT_SECRET` (unique, random, at least 256 bits)
- `PAYMENT_GATEWAY`, `CASHFREE_BASE_URL`, `CASHFREE_CLIENT_ID`, `CASHFREE_CLIENT_SECRET`, `CASHFREE_WEBHOOK_SECRET`
- `PUBLIC_BASE_URL`, `CORS_ALLOWED_ORIGINS`
- Optional `TRUSTED_PROXIES`, `FRONTEND_HTTP_PORT`

The production Compose template deliberately has no real credentials. Its configuration check uses dummy values inside the test runner and does not print them. Optional backend `INVITE_CODE` restricts registration when set; leaving it unset allows open registration.

## Commands after selecting a host

From the project root, first create a protected runtime environment file outside version control (or inject these values through the host secret manager). Then validate the required settings and build:

```powershell
docker compose -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.production.yml build
```

After a staging review and provider-specific network/TLS setup, start and inspect the stack:

```powershell
docker compose -f docker-compose.production.yml up -d
docker compose -f docker-compose.production.yml ps
docker compose -f docker-compose.production.yml logs --since=10m backend frontend
```

`frontend` is published only at `127.0.0.1:<FRONTEND_HTTP_PORT>`; terminate HTTPS at a host or provider reverse proxy and forward to that loopback port. Do not open PostgreSQL or backend ports publicly. Choose and verify provider firewall rules, persistent storage, backup/restore, monitoring, alert delivery, DNS, TLS renewal, and rollback procedures before a real deployment.

## Manual checklist

- [ ] Choose a hosting provider and confirm its network, persistent storage, secret-store, logging, and TLS behavior.
- [ ] Set the final HTTPS domain and configure TLS, DNS, firewall rules, and reverse-proxy forwarding to the loopback frontend port.
- [ ] Put database, JWT, and gateway credentials in the host secret store; use unique production values and do not commit or paste them into the repository.
- [ ] Choose sandbox or live Cashfree operation, configure gateway credentials, register the HTTPS webhook URL, and confirm the gateway's actual signing format against its current documentation.
- [ ] Select and integrate an SMS OTP delivery provider. This repository still lacks production SMS delivery, so vendor registration is not ready for real users.
- [ ] Set `CORS_ALLOWED_ORIGINS` and `PUBLIC_BASE_URL` to the exact HTTPS origin. Determine the stable trusted-proxy address seen by the backend before setting `TRUSTED_PROXIES`; otherwise forwarded addresses are intentionally ignored and request limits share the frontend proxy address.
- [ ] Validate database backups by restoring a copy; do not delete the database volume as part of routine rollout.
- [ ] Run the full automated gate in the selected environment, review logs for sensitive data, and complete a staging smoke check before considering real traffic.
- [ ] Perform the real-phone checks and vendor survey by hand; neither has been run by this project work.

The hosting provider choice, production accounts, keys, money, SMS integration, and data for the survey remain user-side decisions/actions. This file does not represent that deployment or any external setup has happened.
