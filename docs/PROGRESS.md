# BolPay continuation log

## Current state

- Branch before this WIP preservation commit: `codex-takeover`; last verified green baseline is commit `d7f2e68` tagged `deploy-prep-pass`.
- Steps 2, 3, voice announcement, and deployment preparation are complete. The tags `step2-pass`, `step3-pass`, `voice-pass`, and `deploy-prep-pass` all exist. Voice and deployment preparation were not skipped or reordered.
- Playwright/attack-test work is in progress and unverified. The current `tests/run-all.ps1` attempt stopped during production image build because Docker's Linux engine pipe was unavailable in this shell. Maven and smoke tests did not run on this attempt.
- No migration has been edited and no database volume has been deleted.

## Proof at tagged commits

- Step 2: commit `6319b8d`, tag `step2-pass`. Recorded Maven line: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`; the runner ended `ALL TESTS PASSED.`
- Step 3: commit `6b0cc6d`, tag `step3-pass`. Recorded Maven line: `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`; API smoke output included `PASS: public scan session was persisted in PostgreSQL.` and the runner ended `ALL TESTS PASSED.`
- Voice: commit `df64391`, tag `voice-pass`. The recorded runner passed Vite build, 18 Maven tests, and Docker/API smoke checks, ending `ALL TESTS PASSED.` Voice is opt-in and browser speech behavior was not yet automated at that commit.
- Deploy preparation: commit `d7f2e68`, tag `deploy-prep-pass`. The recorded runner passed production Compose validation with dummy values, production frontend/Nginx image build, 18 Maven tests, smoke checks, and ended `ALL TESTS PASSED.` No provider was selected and no deployment occurred.
- Deploy-prep repair: the first production frontend build failed because `NGINX_CONFIG` was declared only in the Node builder stage. The declaration was added to the Nginx stage and the recorded full rerun passed. Production Compose checks use a temporary dummy env file. A prior non-quiet diagnostic printed resolved local `.env` values in tool output; values were not committed. Rotate any real credentials from that file before using them.
- The production frontend build reported 15 npm audit findings (14 moderate, 1 high); they remain untriaged.

## Current runner attempt

Command: `pwsh -NoProfile -File tests/run-all.ps1`

Output from the current working tree (the checked-out commit was `d7f2e68`; Playwright work was uncommitted):

```text
== Docker Compose configuration ==
PASS: docker compose config
== Production Compose template configuration ==
PASS: provider-neutral production Compose template config (dummy validation values only)
 Image bolpay-backend:production Building
 Image bolpay-frontend:production Building
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.

Exception: C:\Users\shiva\CascadeProjects\bolpay\tests\run-all.ps1:6
Line |
   6 |  … DE -ne 0) { throw "$Command $($Arguments -join ' ') failed ($LASTEXIT …
     |                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | docker compose --env-file
     | C:\Users\shiva\AppData\Local\Temp\bolpay-validation-3e426391a1ab45bb91ea4a14769ff506.env -f
     | docker-compose.production.yml build frontend failed (1)
```

This is a Docker environment failure, not a green test result. It did not reach backend tests or smoke checks. Rerun after Docker Desktop's Linux engine is available.

## Test/security evidence gaps

- No test currently sends a correctly signed webhook with an expired/replayed `X-Webhook-Timestamp`; `testReplayOldOrderCode_Invalid` tests an old order code, not timestamp replay.
- `otpIsNotReturnedToClientByDefault` verifies the HTTP response omits an `otp` field. No test captures backend logs to prove an OTP is never logged.
- `WebSocketSecurityConfigTest.rejectsSubscriptionToAnotherVendorTopic` tests a cross-vendor STOMP subscription at the interceptor, not an end-to-end WebSocket connection.
- The duplicate event test checks final paid state but not announcement count or concurrent duplicate delivery.
- Playwright artifacts and webhook-to-board p95 are not verified because the runner could not reach Docker and Playwright has not yet run.

## Current changes and next actions

- Uncommitted Playwright/instrumentation changes were preserved as a WIP commit on a separate branch; they are not tagged as passing.
- Next: restore Docker Linux engine access; run `pwsh -NoProfile -File tests/run-all.ps1`; fix root causes without weakening tests. After the gate passes, capture screenshot artifacts and p95, update attack/compliance evidence, then commit/tag `tests-pass`.
- After tests, prepare `docs/design-plan.md`, run the gate, and stop for user approval before implementing any UI redesign.
- External/manual tasks remain: user selects hosting provider and supplies any account credentials; user handles real-phone checks and vendor survey. No deployment will be performed here.

## Resume commands

```powershell
Set-Location 'C:\Users\shiva\CascadeProjects\bolpay'
git status --short --branch
# Start/verify Docker Desktop Linux engine first, then:
pwsh -NoProfile -File tests/run-all.ps1
```
