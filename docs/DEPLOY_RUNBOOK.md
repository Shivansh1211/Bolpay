# BolPay deployment runbook (owner actions only)

This runbook prepares a private GitHub repository, Render Free Blueprint and Vercel frontend. No provider account has been accessed and no cloud resources have been created. The repository's payment workflow is mocked. Render Free is for a temporary demo: services sleep after 15 minutes, each gets 512 MB / 0.1 CPU, hours are shared, and a Free Postgres database has a 1 GB limit, expires after 30 days, and has no managed backups. Stop if any provider asks for a payment card or paid plan. [Render Free limits](https://render.com/docs/free), [Render compute plans](https://render.com/docs/compute-plans)

## 1. Push the reviewed repository

In PowerShell, from the BolPay repository:

```powershell
git status --short --branch
git switch main
git remote add origin https://github.com/Shivansh1211/Bolpay.git
git push --set-upstream origin main
git push origin --tags
```

If `origin` already exists, inspect it without exposing credentials and set the URL instead of adding it:

```powershell
git remote -v
git remote set-url origin https://github.com/Shivansh1211/Bolpay.git
git switch main
git push --set-upstream origin main
git push origin --tags
```

Git may open your configured browser authentication flow. Do not put a password or token in the remote URL or command line. In GitHub, open **Shivansh1211/Bolpay → Settings → General** and confirm the repository visibility is **Private**. Under **Settings → Branches**, confirm `main` is the default branch. In **Actions**, wait for the CI workflow on `main` to finish with all four jobs green before connecting Render. If the repository is not private or CI is red, stop and fix that first.

## 2. Create the Render Blueprint

1. Sign in to Render yourself and confirm the account has a no-card path and no paid resources are selected.
2. Choose **New → Blueprint** and connect the private `Shivansh1211/Bolpay` repository through Render's GitHub integration.
3. Select branch `main`, Blueprint file `render.yaml`, and inspect the proposed changes. It should list three Docker web services (`bolpay-backend`, `bolpay-ml-service`, `bolpay-mock-cashfree`) and one Free Postgres database in Singapore. Do not deploy if Render proposes a paid plan, card, or unexpected resource.
4. Enter these `sync: false` values in Render's environment-variable form. Generate long random values in your password manager; do not paste them into chat, Git, or a terminal command.

| Variable | Service(s) | Handling |
|---|---|---|
| `JWT_SECRET` | backend | Set a unique random 256-bit value. |
| `CASHFREE_WEBHOOK_SECRET` | backend, mock-cashfree | Enter the same random value in both. |
| `ML_API_KEY` | backend, ml-service | Enter the same random value in both. |
| `MOCK_ADMIN_PASSWORD` | mock-cashfree | Set a unique strong password. |
| `INVITE_CODE` | backend | Set a unique invite value; keep it private. |

Render generates `CASHFREE_CLIENT_SECRET`; the Blueprint shares it with the mock service. `DEMO_OTP` starts `false`; `OTP_RETURN_TO_CLIENT` stays `false`. There is no production SMS provider configured. Wait for all services to deploy and their `/health` checks to pass. The initial CORS/public URL placeholders are deliberately not the final frontend origin.

## 3. Record the three Render URLs

In Render, open each service and copy its public HTTPS URL into a local note that you keep private:

- `BACKEND_URL`: `bolpay-backend`
- `ML_URL`: `bolpay-ml-service`
- `MOCK_URL`: `bolpay-mock-cashfree`

Do not copy internal hostnames into browser settings. Confirm `https://<host>/health` returns a healthy response for each service. Do not share environment values or the Render database connection string.

## 4. Create the Vercel frontend project

1. In Vercel, choose **Add New → Project**, import the same private repository, and set **Root Directory** to `frontend`.
2. Use the Vite preset, install command `npm ci`, build command `npm run build`, and output directory `dist`.
3. Under **Environment Variables**, add these public URL values for **Production** only. They contain no credentials:

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://<backend-host>` |
| `VITE_WS_URL` | `wss://<backend-host>/ws` |
| `VITE_MOCK_URL` | `https://<mock-host>` |
| `VITE_PUBLIC_BASE_URL` | The final production Vercel origin, for example `https://<project>.vercel.app` |

Do not add any secret, JWT, ML key, webhook key, invite code, or admin password as a `VITE_*` variable. Deploy and record the production origin shown in Vercel. The SPA rewrite in `frontend/vercel.json` keeps `/v/{slug}` and `/o/{code}` working on refresh. [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite), [Vercel environment variables](https://vercel.com/docs/environment-variables)

## 5. Set production origin restrictions

In Render's `bolpay-backend` environment settings, set:

- `CORS_ALLOWED_ORIGINS` to exactly the Vercel production origin, with no path or trailing slash.
- `PUBLIC_BASE_URL` to the same production origin.

Save and redeploy the backend. Confirm the Vercel `VITE_PUBLIC_BASE_URL` equals it, and the API, WebSocket and mock URLs equal the three Render URLs. Rebuild Vercel after any `VITE_*` changes because Vite embeds them at build time.

For the safe default, open Vercel **Project Settings → Build and Deployment → Ignored Build Step** and choose **Only build production**. This skips non-production branch/PR builds. If previews are needed later, assign one stable branch-specific preview domain, add only that exact origin to `CORS_ALLOWED_ORIGINS`, set deliberately separate Preview `VITE_*` URLs, and remove the preview origin when done. Never use wildcard CORS. [Vercel project settings](https://vercel.com/docs/project-configuration/project-settings), [Vercel Git deployments](https://vercel.com/docs/git)

Render's Blueprint sets all three web services to `autoDeployTrigger: checksPass`. In each Render service's **Settings → Build & Deploy**, confirm **After CI Checks Pass** is selected. Do not change this to deploy-on-commit to bypass failed CI. [Render deploy triggers](https://render.com/docs/deploys)

## 6. Warm up and run the remote smoke checks

Run these only shortly before a demo. Free services may sleep and the first request after idle can take about a minute. The smoke script creates a synthetic vendor/order in the hosted database.

```powershell
pwsh -NoProfile -File tests/warmup.ps1 `
  -FrontendUrl 'https://<vercel-origin>' `
  -BackendUrl 'https://<backend-host>' `
  -MockUrl 'https://<mock-host>' `
  -MlUrl 'https://<ml-host>'
```

For the remote smoke test only:

1. Confirm `INVITE_CODE` is set and secured in Render.
2. Temporarily set `DEMO_OTP=true` for the test window and redeploy the backend. The application refuses this mode without `INVITE_CODE`; the OTP is explicitly labeled **demo login**.
3. Run the script. It requests the invite code using hidden input and prints only PASS/FAIL check names.

```powershell
pwsh -NoProfile -File tests/remote-smoke.ps1 `
  -FrontendUrl 'https://<vercel-origin>' `
  -BackendUrl 'https://<backend-host>' `
  -MockUrl 'https://<mock-host>' `
  -MlUrl 'https://<ml-host>'
```

4. Immediately set `DEMO_OTP=false` again and redeploy. Do not leave demo OTP enabled between demos. This is a mock payment test, not a real SMS or payment test.

## 7. Read a failed build safely

- **GitHub CI:** open the repository **Actions** tab, select the failed run, select the failed job and expand the first red step. Fix the first failure, push a new commit to `main` (or PR branch), and wait for all checks. Do not copy secret values from logs into issues or chat.
- **Render:** open the service → **Events** to identify the failed deploy, then **Logs** and select the build or runtime output. Check Docker build context/root directory, health path, database references, and Flyway startup error. Keep secret values hidden. A failed health check should leave the previous healthy deploy serving traffic; do not delete the database to retry.
- **Vercel:** open the project → **Deployments**, select the failed deployment, and open **Build Logs**. Check Root Directory `frontend`, Vite build output, and whether the production `VITE_*` variables were present for that deployment. Environment changes require a new build.

## By-hand checklist

- [ ] Confirm GitHub repository is private and `main` is default.
- [ ] Push `main` and all tags; wait for green CI.
- [ ] Create the Render Blueprint, choose only Free plans, and enter the five secret values.
- [ ] Record the backend, ML, and mock public URLs.
- [ ] Create the Vercel project with root `frontend` and four Production `VITE_*` URLs.
- [ ] Set backend CORS and `PUBLIC_BASE_URL` to the production Vercel origin; redeploy.
- [ ] Confirm Render waits for CI checks and Vercel previews are disabled or explicitly isolated.
- [ ] Before a demo, run warm-up and remote smoke; temporarily enable demo OTP only with `INVITE_CODE`, then disable it and redeploy.
- [ ] Export a database backup before the Free Postgres 30-day expiry; verify the backup can be listed/restored to a separate database.
- [ ] Complete `docs/phone-test.md` yourself. Real-phone testing, provider account setup, actual SMS/payment, and vendor survey are not performed by this runbook.
