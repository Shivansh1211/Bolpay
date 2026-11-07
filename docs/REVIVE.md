# Revive BolPay

This is a target checklist for restoring the demo in under 30 minutes once provider accounts exist and the repository is available. First-time account verification, Neon project provisioning, provider review, DNS, cold starts, or CI/build queues can take longer; no wall-clock guarantee is made. Do not enter provider credentials in Git or chat. The current Blueprint is in `render.yaml`; exact variable ownership and backup commands are in [DEPLOY_RUNBOOK.md](DEPLOY_RUNBOOK.md).

## Hosted route

1. **0–5 min: code.** Push the reviewed `main` and tags to the private repository; wait for all required CI checks to pass.
2. **5–10 min: Neon.** Create/select the Postgres database and obtain its direct endpoint, database name, username and password. Use the direct host (without `-pooler`) so Flyway has a direct connection for schema initialization. Keep credentials separate from the URL.
3. **10–15 min: Render.** Select Project **Bolpay** and environment **Production**. Create/link the Blueprint from `render.yaml`. Create and scope `bolpay-webhook-secret` (`CASHFREE_WEBHOOK_SECRET`) and `bolpay-ml-secret` (`ML_API_KEY`) in the dashboard, then fill the prompted backend database fields, JWT secret, invite code and mock-admin password. Check Render backend logs for Flyway V1–V8 and healthy status; also check ML and mock `/health`.
4. **15–20 min: Vercel.** Import the same private repository with root `frontend`, build `npm ci && npm run build`, output `dist`. Set `VITE_API_BASE_URL`, `VITE_WS_URL` (`wss://.../ws`), `VITE_MOCK_URL`, and `VITE_PUBLIC_BASE_URL`. Deploy and record the production URL.
5. **20–23 min: origin settings.** Set backend `CORS_ALLOWED_ORIGINS` and `PUBLIC_BASE_URL` to the exact Vercel origin, save/redeploy backend, and rebuild Vercel if its public URL variable changed.
6. **23–26 min: optional seed.** If sample data is wanted, set the three `SPRING_DATASOURCE_*` environment variables only in a private local shell and run `pwsh -NoProfile -File scripts/seed-demo.ps1`. It uses fictional records and a direct URL only.
7. **26–30 min: check.** Run `tests/warmup.ps1`, then `tests/remote-smoke.ps1` with the four public URLs. Temporarily enable `DEMO_OTP` only with `INVITE_CODE` configured, then disable it immediately. Complete [phone-test.md](phone-test.md) manually before using a customer-facing QR.

If Neon is suspended, the first connection can trigger a wake. Wait for the compute to become active, allow the configured Hikari startup retry window (120 seconds), then check the first Flyway/Hikari error in Render logs. Correct the URL/credentials or retry the backend deploy after Neon is active; do not change migrations or recreate the database to clear a transient timeout.

## Always-free local fallback

Local Compose continues to use the repository's local Postgres; it does not require Neon or Render. From the repository root:

```powershell
docker compose up --build -d
pwsh -NoProfile -File tests/run-all.ps1
```

For remote phone access to a local demo, start a trusted HTTPS tunnel to the frontend, backend, and mock service using a tunnel provider you choose. Set the frontend's public API, WebSocket, mock, and QR origins to the corresponding tunnel URLs; set backend CORS and `PUBLIC_BASE_URL` to the frontend tunnel origin. Keep the database and ML service private; do not tunnel Postgres or expose the ML API. Use only fictional test data and mock payments. Tunnel provider setup/authentication is a manual account action, and free-tier terms/availability may change.

## If a step fails

- Check GitHub Actions first; fix the first failed CI step before Render deploys.
- Check Render **Events** and backend **Logs** for build, Hikari connection, and Flyway errors. Do not paste secrets into logs or support messages.
- Check Vercel **Deployments → Build Logs** for a wrong root, missing build variable, or SPA rewrite issue.
- If any provider requires payment details or a paid resource, stop and use local Compose. No provider credentials or paid resources are part of this procedure.
