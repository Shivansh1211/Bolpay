# BolPay continuation log

## Current state

- Branch: `codex/wip-playwright-evidence`.
- Step 2, Step 3, voice, and deployment preparation are complete at tags `step2-pass`, `step3-pass`, `voice-pass`, and `deploy-prep-pass`.
- WebSocket fix and full test gate pass on the current workspace. UI redesign has not started; wait for approval of `docs/design-plan.md` before building UI.
- No migration or database volume was edited/deleted.

## WebSocket reproduction and root cause

- Before the fix, Chromium received raw STOMP `ERROR` with exact header `message:Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]`; the frame is preserved in ignored `tests/artifacts/ws-error-frame.log`.
- Matching backend diagnostic at `2026-11-04T21:44:20.300Z`: `vendorTopic=true, numericDestination=true, principalPresent=false, destinationMatchesPrincipal=false`; the same session logged Spring's inbound channel failure at `21:44:20.301Z`. Raw evidence is in ignored `tests/artifacts/backend-websocket-cause.log`.
- The CONNECT interceptor reads native STOMP `Authorization: Bearer ...`, validates the JWT, and uses the JWT `vendorId` claim as the principal name (decimal string); it separately logs the `phoneNumber` claim. Existing `WebSocketSecurityConfigTest` built `Principal("41")` by hand and did not exercise a real signed JWT.
- Root cause: `StompHeaderAccessor.wrap(message)` mutated a detached wrapper while `preSend` returned the original message, so later SUBSCRIBE frames had no principal. Fixed by obtaining the accessor attached to the inbound message with `MessageHeaderAccessor.getAccessor`. Vendor-topic exact-match authorization remains unchanged.
- `WebSocketStompIntegrationTest` now uses a signed JWT and real SockJS/STOMP socket: own vendor topic receives a message; another vendor's topic is denied; absent or invalid JWT is denied during CONNECT.

## Latest full verification

- Command: `pwsh -NoProfile -File tests/run-all.ps1` (exit 0).
- Compose configuration, provider-neutral production Compose validation, production frontend build, backend tests, Docker/API smoke checks, and Chromium Playwright all passed.
- Maven: 22 tests, 0 failures, 0 errors, 0 skipped. The real-STOMP integration test passed.
- Playwright: scan -> Scanned counter -> amount/token -> pending amber row -> mock pay -> paid green row updated live without refresh -> customer Payment verified -> Abandon without webhook -> server expiry turns row red. Captures also showed browser STOMP `CONNECTED` and `MESSAGE` frames; no `ERROR` frame occurred in the live-flow run.
- Latency: 20 webhook-received-to-board-DOM samples; p95 **116 ms**, recorded in ignored `tests/artifacts/latency.json`.
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
git switch codex/wip-playwright-evidence
git status --short --branch
pwsh -NoProfile -File tests/run-all.ps1
```

- WIP commit: cc8a96c on codex/wip-playwright-evidence; 	ests-pass tag created after the successful full run. Raw runner output and latency samples remain git-ignored.
