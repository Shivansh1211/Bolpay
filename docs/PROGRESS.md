# BolPay continuation log

## Current state

- Branch: `codex-takeover` (WIP merged from `codex/wip-playwright-evidence`). Tags `step2-pass`, `step3-pass`, `voice-pass`, `deploy-prep-pass`, and `tests-pass` are present.
- Tasks 1–5 of the current phase are complete, and the user approved the design plan and preview on 2026-11-05. The approved live UI redesign is implemented and the full gate passed; see “UI implementation after approval” below.
- No migration or database volume was edited/deleted.

## WebSocket reproduction and root cause

- Before the fix, Chromium received raw STOMP `ERROR` with exact header `message:Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]`; the frame is preserved in ignored `tests/artifacts/ws-error-frame.log`.
- Matching backend diagnostic at `2026-11-04T21:44:20.300Z`: `vendorTopic=true, numericDestination=true, principalPresent=false, destinationMatchesPrincipal=false`; the same session logged Spring's inbound channel failure at `21:44:20.301Z`. Raw evidence is in ignored `tests/artifacts/backend-websocket-cause.log`.
- The CONNECT interceptor reads native STOMP `Authorization: Bearer ...`, validates the JWT, and uses the JWT `vendorId` claim as the principal name (decimal string); it separately logs the `phoneNumber` claim. Existing `WebSocketSecurityConfigTest` built `Principal("41")` by hand and did not exercise a real signed JWT.
- Root cause: `StompHeaderAccessor.wrap(message)` mutated a detached wrapper while `preSend` returned the original message, so later SUBSCRIBE frames had no principal. Fixed by obtaining the accessor attached to the inbound message with `MessageHeaderAccessor.getAccessor`. Vendor-topic exact-match authorization remains unchanged.
- `WebSocketStompIntegrationTest` now uses a signed JWT and real SockJS/STOMP socket: own vendor topic receives a message; another vendor's topic is denied; absent or invalid JWT is denied during CONNECT.

## WebSocket-fix verification baseline (historical)

- Command: `pwsh -NoProfile -File tests/run-all.ps1` (exit 0).
- Compose configuration, provider-neutral production Compose validation, production frontend build, backend tests, Docker/API smoke checks, and Chromium Playwright all passed.
- Maven: 22 tests, 0 failures, 0 errors, 0 skipped. The real-STOMP integration test passed.
- Playwright: scan -> Scanned counter -> amount/token -> pending amber row -> mock pay -> paid green row updated live without refresh -> customer Payment verified -> Abandon without webhook -> server expiry turns row red. Captures also showed browser STOMP `CONNECTED` and `MESSAGE` frames; no `ERROR` frame occurred in the live-flow run.
- At that earlier WebSocket-fix run, latency was 20 webhook-received-to-board-DOM samples; p95 **116 ms**. The later post-merge run measured 90 ms and the latest brand run measured 94 ms.
- Runner final lines: `1 passed (19.6s)`, `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples`, `ALL TESTS PASSED.` Full transcript: ignored `tests/artifacts/full-run-output.txt`; safe excerpt: `docs/test-run-output.txt`.
- Reviewed, redacted screenshots are in `docs/test-evidence/`. Raw artifacts stay ignored.

## Security proof and limitations

- Exact WebSocket proofs: `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic`; `WebSocketStompIntegrationTest.missingAndInvalidJwtAreDeniedDuringStompConnect`.
- Webhook proof: `AttackSuiteTest.webhookWithoutSignatureIsRejected`, `testForgedWebhook_BadSignature`, `correctlySignedWebhookWithStaleTimestampIsRejected`, `webhookMissingEventIdOrAmountIsRejected`, `testDuplicateWebhook_Idempotent`, and `testWrongAmountWebhook_Rejected`.
- Other proof: `registrationWithoutOtpProofIsRejected`, `otpIsNotReturnedToClientByDefault`, `vendorCannotReadAnotherVendorsOrderByCode`, `scanSessionsArePersistedAndCountedForVendorBoard`, `concurrentOrderCreationAllocatesDistinctActiveTokens`, and `OrderTokenAllocationTest` two/three-digit cases.
- Fake-screen test waits for configured expiry; lost-webhook test uses mock `scenario=drop` and reconciliation, not manual status edits.
- Not fixed: duplicate deliveries are not tested concurrently and emitted announcements are not counted; npm audit findings remain untriaged; live gateway/SMS/hosting/deployment, real-phone checks, vendor survey, and optional historical tag reruns remain outstanding. UI work awaits the design-plan approval gate.
- The prototype is not production-ready and does not claim to prevent scams.

## Resume commands

```powershell
Set-Location 'C:\Users\shiva\CascadeProjects\bolpay'
git switch codex-takeover
git status --short --branch
pwsh -NoProfile -File tests/run-all.ps1
```

For the static preview screenshots and Vercel rewrite validation:

```powershell
docker run --rm -v "${PWD}\docs\design-preview:/preview" bolpay-e2e:local node /tests/capture-design-preview.mjs
$vercel = Get-Content frontend/vercel.json -Raw | ConvertFrom-Json
pwsh -NoProfile -File tests/run-all.ps1
```

The final full output is in ignored `tests/artifacts/final-run-output.txt`; the safe excerpt and screenshot index are in `docs/test-run-output.txt` and `test-report.md`.

## Completed phase: housekeeping through deployment preparation

- Merged `codex/wip-playwright-evidence` into `codex-takeover` with merge commit `4e5546a`; the `tests-pass` tag still points to the verified WIP snapshot.
- Post-merge `tests/run-all.ps1` passed on HEAD `4e5546a`: 22 Maven tests (0 failures/errors/skips), API/Compose smoke tests, and Playwright (1 passed in 21.6s). Final output: `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.`; 20-sample p95 was 90 ms. Full transcript is ignored at `tests/artifacts/post-merge-run-output.txt`. The optional historical `step2-pass` rerun was interrupted before results and remains unverified.
- The temporary historical worktree and its ignored directory were removed after the build ended.
- Next: proceed through brand wiring, pattern research, static design preview, and deployment preparation. Do not change the live app for the static preview; stop for approval after Task 5.
- WIP commit and `tests-pass` tag were created after a successful full run. Raw runner output and latency samples remain Git-ignored.

## Task 2: brand wiring

- Created original SVG replacements because `frontend/public/brand/` was absent; the user authorized replacement artwork. Added the favicon, app-header logos with accessible live text, and printable QR-card mark. Documented palette, minimum sizes, clear space, and typeface choice in `docs/brand.md`.
- First full run stopped during backend tests because Docker Desktop had exited and the Docker daemon was unavailable. Restarted Docker Desktop, confirmed `docker info` showed the Linux/WSL2 server, then reran the full gate.
- Verified with `pwsh -NoProfile -File tests/run-all.ps1` (exit 0): Maven 22 tests, 0 failures/errors/skips; API smoke checks passed; Playwright 1 passed in 19.9s; final lines were `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.` Full raw transcript: ignored `tests/artifacts/brand-run-output.txt`.
- Latest latency evidence: 20 webhook-to-board samples, p95 94 ms in ignored `tests/artifacts/latency.json`. The post-merge 90 ms value above belongs to the earlier run.
- No third-party font or image files were downloaded or bundled. The wordmark paths are original hand-drawn SVG geometry, not converted from the chosen font; Noto Sans / Noto Sans Devanagari are the OFL typeface choice for live text.
- Task 2 is complete. Next: finish UI pattern research, run the required full test gate, then prepare the static preview and deployment document. No live UI redesign is authorized.

## Task 3: UI pattern research

- `docs/ui-research.md` documents 10 checkout, payment confirmation, POS, and queue/KDS references with source links, observed patterns, rationale, and fit for BolPay's street vendors and queued customers.
- Research is descriptive only. No third-party assets or layouts were downloaded, embedded, or copied; no real payment app's visual identity is adopted.
- Verified with `pwsh -NoProfile -File tests/run-all.ps1` (exit 0): 22 Maven tests, 0 failures/errors/skips; Docker/API smoke checks passed; Playwright 1 passed in 16.8s. Final lines: `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.` Full output: ignored `tests/artifacts/research-run-output.txt`.
- One initial gate attempt failed before tests when the sandbox could not access Docker's default buildx lock and engine pipe; queried Docker with approved local-engine access, then completed the retry. No tests or checks were changed.
- Task 3 is complete. Next: create the design plan and static preview, without changing the live application.

## Task 4: design plan and static preview

- Drafted `docs/design-plan.md` with six palette tokens, state treatment, type/spacing/tap-target guidance, accessibility, bilingual wording, 360 px wireframes, and a critique of generic defaults.
- Added static fake-data screens under `docs/design-preview/` for amount, waiting, verified, expired, late payment, vendor board, glance mode, and printable QR. Quick amount chips and “Mark as served” are disabled and labeled planned. The QR is clearly labeled as a sample placeholder.
- Captured 10 Playwright screenshots at 360x820, including light/dark board and Hindi header view, under `docs/design-preview/screenshots/`. Raw test transcripts remain ignored.
- Verified with `pwsh -NoProfile -File tests/run-all.ps1` (exit 0): 22 Maven tests, 0 failures/errors/skips; Docker/API smoke checks passed; Playwright 1 passed in 16.7s. Final lines: `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.` Full output: ignored `tests/artifacts/design-preview-run-output.txt`.
- Latest latency from that run: 20 samples, p95 91 ms. This is local mock-environment evidence, not a production performance guarantee.
- Task 4 is complete. Next: prepare deployment documentation and Vercel config; do not deploy.

## Task 5: deployment preparation

- Added `docs/deployment.md` with provider categories (none selected), Vercel setup, environment variables, HTTPS SockJS/WSS proxying, strict CORS, private ML/database guidance, mock admin password handling, cold-start limitations, real-phone checklist, and rollback steps.
- Added `frontend/vercel.json` SPA fallback rewrite for Vite routes including `/v/{slug}` and `/o/{code}`.
- Recorded gaps accurately: `INVITE_CODE` is not implemented; SMS delivery and dedicated “Waking up the server…” behavior are absent. The production Compose template omits the mock service.
- `frontend/vercel.json` parsed successfully and contains one catch-all rewrite to `/index.html`.
- Verified with `pwsh -NoProfile -File tests/run-all.ps1` (exit 0): Compose and production image builds passed; Maven 22 tests, 0 failures/errors/skips; Docker/API smoke checks passed; Playwright 1 passed in 20.5s. Final lines: `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.` Full output: ignored `tests/artifacts/deploy-prep-run-output.txt`.
- A final full rerun after the design-preview palette correction also passed: 22 Maven tests, 0 failures/errors/skips; API smoke checks; Playwright 1 passed in 16.5s; final output `ALL TESTS PASSED.` Full transcript: ignored `tests/artifacts/final-run-output.txt`.
- Latest latency: 20 webhook-to-board samples, p95 93 ms. This is local mock-environment evidence only.
- Task 5 is complete. No host was selected and nothing was deployed. Stop here for approval of the design plan, preview, and deployment document. Do not implement UI work or planned features until approval.


## UI implementation after approval (2026-11-05)

- Approval: user said “looks good, continue” for the design plan/preview. This authorized implementing the approved UI. No deployment or provider/account action was taken.
- Implemented the live bilingual UI using the single shared English/Hindi dictionary in `frontend/src/i18n.js`: customer amount and order states, vendor board/counters/glance mode, login/register, shared components, and printable QR card. Added brand/status tokens, accessible status words/icons, focus visibility, reduced-motion behavior, and print styling.
- Did not implement planned quick-amount chips or Mark-as-served; those remain static-preview examples only. No backend/payment state or authorization rule was changed for this UI task.
- First full gate attempt failed in Playwright because the test expected the old “Payment verified” label while the approved design says “Verified”. Updated the assertion to the approved customer wording.
- Second full gate attempt failed because `getByText('Paid')` matched both the “Paid” status and “Paid at” metadata. Scoped the assertion to `.order-state--paid`; no test/security coverage was removed.
- Third full gate command: `pwsh -NoProfile -File tests/run-all.ps1`; exit 0. Maven: 22 tests, 0 failures/errors/skips; Docker/API smoke checks passed; Playwright: `1 passed (14.2s)`. Final runner lines: `PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples` and `ALL TESTS PASSED.` Raw full transcript: ignored `tests/artifacts/ui-final-output.txt`.
- Playwright completed scan → Scanned counter → amount → token and amber row → pay in mock → live green row without refresh → customer Verified → abandon → red expired row. It also verified the voice announcement.
- Latency: 20 local mock webhook-received-to-board-DOM samples; p95 86 ms in ignored `tests/artifacts/latency.json`. This is not a production performance result.
- Reviewed live screenshots with token/order references hidden; committed review copies are under `docs/test-evidence/`. The mock checkout screenshot was excluded because it can contain session values. Raw artifacts remain ignored.
- Static 360x820 preview screenshots remain under `docs/design-preview/screenshots/`.
- Remaining: run the full gate on the final documented tree, review/stage only intended files, commit, and create `ui-pass` only if green. Exact commands: `pwsh -NoProfile -File tests/run-all.ps1`; `git add <reviewed files>`; `git commit -m "feat: apply approved BolPay UI redesign"`; `git tag ui-pass`. Hosting provider, live gateway/SMS, real-phone checks, vendor survey, INVITE_CODE enforcement, and brand wordmark font-outline conversion remain open.

## Resume commands

```powershell
Set-Location 'C:\Users\shiva\CascadeProjects\bolpay'
git switch codex-takeover
git status --short --branch
pwsh -NoProfile -File tests/run-all.ps1
```
