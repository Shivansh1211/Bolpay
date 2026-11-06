# Attack test results

## Latest full gate (2026-11-06)

`pwsh -NoProfile -File tests/run-all.ps1` completed successfully: Maven 26 tests, 0 failures/errors/skips; Docker/API smoke checks passed; Playwright `2 passed (25.5s)`; final output included `ALL TESTS PASSED.` Raw transcript is ignored at `tests/artifacts/customer-token-fix-output.txt`. The Playwright flow now also checks the paid public status page's queue token, vendor quick lookup by Order ID, delayed five-second payment classification, and live scan/pending events. Twenty local webhook-received-to-board-DOM samples measured p95 **71 ms** in ignored `tests/artifacts/latency.json`; no production performance claim is implied.

## Latest payment-flow run status (2026-11-05)

The latest `pwsh -NoProfile -File tests/run-all.ps1` run is **not green**: Maven reported 26 tests with 0 failures/errors/skips and the API smoke checks passed, but Playwright reported `1 failed`, `1 passed (9.5s)` at the vendor quick-check assertion. Browser assertions before that point verified live scan count, live pending order, normal mock payment, green vendor row, customer Verified state, and visible Order ID. Three browser attempts reached the same strict-mode error because the quick-check result renders the Order ID twice; the stop gate was reached. The newly added five-second “Slow Pay” late-payment assertion occurs after that failure and is unverified. Raw full output is ignored at `tests/artifacts/payment-flow-fix-output.txt`. Historical p95 values below are from earlier successful runs and do not describe this failed run.

Latest command: `pwsh -NoProfile -File tests/run-all.ps1`. Result: **ALL TESTS PASSED**; Maven reported 22 tests, 0 failures, 0 errors, 0 skipped, and Chromium Playwright passed. This is local automated evidence, not production security validation.

## WebSocket evidence

Before the interceptor fix, Chromium received STOMP `ERROR` header `message:Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]`. At `2026-11-04T21:44:20.300Z`, the backend logged `vendorTopic=true, numericDestination=true, principalPresent=false, destinationMatchesPrincipal=false`; Spring logged the matching inbound-channel failure at `21:44:20.301Z`. Raw captures are in the ignored `tests/artifacts/ws-error-frame.log` and `tests/artifacts/backend-websocket-cause.log`.

The interceptor reads native STOMP `Authorization: Bearer <JWT>` and names the principal from the JWT `vendorId` claim. The prior unit test fabricated a principal, so `WebSocketStompIntegrationTest` now covers a real signed JWT over SockJS/STOMP: `signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic` proves own-topic delivery and cross-vendor denial; `missingAndInvalidJwtAreDeniedDuringStompConnect` proves absent and invalid credentials are denied. Playwright's current live flow receives STOMP CONNECTED/MESSAGE and updates the paid row without refresh.

## Security test mapping

| Item | Exact automated proof |
|---|---|
| Unsigned webhook | `AttackSuiteTest.webhookWithoutSignatureIsRejected` |
| Bad signature | `AttackSuiteTest.testForgedWebhook_BadSignature` |
| Replayed/stale timestamp | `AttackSuiteTest.correctlySignedWebhookWithStaleTimestampIsRejected` |
| Duplicate event | `AttackSuiteTest.testDuplicateWebhook_Idempotent` |
| Wrong amount | `AttackSuiteTest.testWrongAmountWebhook_Rejected` |
| Missing event ID/amount | `AttackSuiteTest.webhookMissingEventIdOrAmountIsRejected` |
| Vendor A vs vendor B | `AttackSuiteTest.vendorCannotReadAnotherVendorsOrderByCode`; `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic` |
| WebSocket cross-topic | `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic` |
| Registration without OTP | `AttackSuiteTest.registrationWithoutOtpProofIsRejected` |
| OTP absent from API/logs | `AttackSuiteTest.otpIsNotReturnedToClientByDefault` |
| Scan sessions counted | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard` |
| Token uniqueness | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest` two-digit and three-digit fallback tests |
| Fake screen, no webhook | `AttackSuiteTest.testFakeModdedAppScreen_NoWebhook` waits for scheduled expiry and asserts EXPIRED |
| Lost webhook | `AttackSuiteTest.testLostWebhook_ReconciliationFixes` uses mock `scenario=drop`, confirms PENDING, then reconciliation marks PAID; no manual status mutation |

## Current browser and latency proof

Playwright covers scan, Scanned count, amount/token, amber pending, mock payment, live green row without refresh, customer verified state, abandon without a webhook, and live red row after real expiry. The latest 20 webhook-received-to-board-DOM samples measured **p95 86 ms** (local mock environment; not a production performance claim). Reviewed screenshots are in `docs/test-evidence/`; raw frames/logs and full output are ignored under `tests/artifacts/`.

## Remaining limits

Duplicate events are not exercised concurrently and emitted WebSocket announcements are not counted. No live provider, production SMS, hosting/deployment, real-phone test, or vendor survey was run. npm audit findings remain untriaged. These results do not establish production security or scam prevention.
