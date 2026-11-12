# BolPay live end-to-end report

Date: 2026-11-13

Branch: `live-test-fixes` (based on `main` at `0b5ec19`)

Hosted changes: none. No push or deploy was performed.

## Results

| Feature | Steps | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|
| Hosted health | GET frontend, backend, mock, and ML health endpoints | Each service responds | All four returned HTTP 200 during the warm check | Pass (health only) | `docs/PROGRESS.md` |
| Hosted OTP login | On the live login page, request a code once, then verify the displayed code using a generated fictional 10-digit number | One send request; same phone and fresh six-digit code verify | One send request; phone is sent as 10 digits without `+91`; response is HTTP 200 with `message`, `mode`, and `otp`; the displayed code is six digits; verify payload has `phoneNumber` and `otpCode`, six digits with no whitespace; verify returns HTTP 403. Empty-body verify also returned 403 in the prior bounded probe. | Fail (host configuration/deployed artifact unresolved) | `docs/live-test-evidence/otp-live-evidence.json`, `docs/live-test-evidence/otp-live-verify-result.png` |
| Hosted browser errors | Inspect browser console and page errors during the OTP attempt | No unexpected browser errors | Zero console errors and zero page errors; the failure is the HTTP 403 response | Pass for browser runtime | `docs/live-test-evidence/otp-live-evidence.json` |
| Local OTP persistence across restart | Send from the browser, restart only the backend container, then verify the same code | Verification succeeds using the persisted database session | Exactly one send request; backend container restarted while PostgreSQL remained running; verify returned HTTP 200; no page errors | Pass | `docs/live-test-evidence/otp-restart-evidence.json`, `docs/live-test-evidence/otp-restart-01-before-restart.png`, `docs/live-test-evidence/otp-restart-02-after-restart.png`, `docs/live-test-evidence/otp-restart-03-verification-result.png` |
| Local backend regression/integration suite | Run `tests/run-all.ps1` with disposable PostgreSQL | All backend tests pass | Maven: 40 tests, 0 failures, 0 errors, 0 skipped; migrations V1-V8 applied to the disposable DB | Pass | `docs/live-test-evidence/local-suite-summary.json` |
| Local API/mock smoke | Start isolated backend and exercise OTP registration, scans, and checkout | All API smoke checks pass | Both temporary and Compose APIs registered synthetic `ZZ-TEST` vendors, created orders, persisted scans, and reached mock checkout; protected mock paths returned 404 | Pass | `docs/live-test-evidence/local-suite-summary.json` |
| Local vendor/customer browser flow | Run existing Playwright suite | Vendor auth, board, scan/payment/expiry flow passes | 2 Playwright tests passed, including the vendor login/registration and customer payment lifecycle | Pass | `docs/live-test-evidence/local-suite-summary.json` |
| Hosted app routes, registration, QR, payment, mock admin, ML auth, WebSocket isolation, accessibility | Full hosted suite | Checks described in `tests/remote-smoke.ps1` and existing Playwright suite pass | Not run after OTP diagnosis: hosted verify returns 403, so continuing auth-dependent flows would create misleading results; dashboard credentials and live demo invite setting were not available to inspect | Not verified | OTP evidence above |
| Cold start after 15+ minutes idle | Measure first response after a measured idle period | Record hosted seconds | Not measured; services were already contacted/warm during this task | Not measured | None |
| Hosted webhook-to-board latency | Collect 20 hosted samples and report p50/p95 | 20 real hosted samples | Not measured because no hosted vendor/order flow was run | Not measured | None |
| Local webhook-to-board latency | 20 samples from the local browser suite | Report separately from hosted | 20 local samples; p50 69 ms, p95 77 ms. These are local measurements, not hosted performance. | Pass (local only) | `docs/live-test-evidence/local-suite-summary.json` |

## OTP diagnosis and changes

The live browser sent one request per click. Its request body used a 10-digit phone number with no country prefix. The verify request used the same phone format and a six-digit, trimmed `otpCode`. The send endpoint returned HTTP 200 and the UI displayed a six-digit demo code. Verify returned HTTP 403; sending an empty JSON body to the verify endpoint also returned 403. This is not evidence of a bad OTP, phone formatting, expiry, or trailing whitespace. The checked-in `SecurityConfig` permits both OTP routes, and the controller maps invalid codes to HTTP 400, expired codes to 410, and lockout to 429. The observed 403 therefore occurs outside that expected OTP validation path. The exact hosted deployment revision or upstream rule could not be confirmed without Render dashboard access.

The repository changes make local OTP sessions survive a backend process restart, invalidate earlier codes when a new code is requested, distinguish invalid/expired/rate-limited/server-error responses, and add a resend action. A legacy `OTP_RETURN_TO_CLIENT` bypass was removed: the backend now returns the code only when `DEMO_OTP=true`, with a nonblank invite code configured. This closes the code-return bypass in this branch, but cannot change the currently deployed service until the branch is pushed and Render redeploys.

## Remaining hosted work

- Review the Render service's deployed commit and security configuration. Deploy this branch, then retest the hosted verify route after health is green. Do not keep demo OTP enabled outside the controlled test window.
- Run the rest of the hosted suite after authentication works. Measure cold start only after at least 15 minutes without requests and collect 20 separate hosted webhook-to-board samples.
- The task did not create any hosted test vendor or order. The live diagnostic used only a generated fictional phone number; no real phone number or person's data was used. Do not add unrelated database records to the cleanup query.
- `docs/live-test-evidence/cleanup-zz-test-vendors.sql` is provided but was not run. It deletes only vendors whose business name starts with `ZZ-TEST` and rows attached to those vendors.

## Manual steps

1. Push branch `live-test-fixes` to GitHub.
2. Wait for CI to pass, then let Render build and redeploy the backend.
3. Keep `DEMO_OTP=false` for normal use. If temporarily enabling it for a controlled smoke run, require a configured invite code and disable it again immediately afterward.
4. Rotate the invite code only if it was shared outside the secret environment or hidden prompt. It was not requested or recorded in this task.
5. After hosted checks create synthetic `ZZ-TEST` vendors, review and run `docs/live-test-evidence/cleanup-zz-test-vendors.sql` against the intended test database.
