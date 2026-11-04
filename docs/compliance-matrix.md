# Project brief compliance matrix

Scope basis: the user-facing project brief and feature/security claims in `README.md`, plus the explicit continuation gates. A “present” status means repository code exists; it does not imply production validation.

| Brief item | Status | Proof / boundary |
|---|---|---|
| Create an order before checkout and display payment state from server-side gateway events | Implemented for mock flow | `backend/src/main/java/com/bolpay/service/OrderService.java`; `AttackSuiteTest.testForgedWebhook_BadSignature`, `testWrongAmountWebhook_Rejected`, `testLostWebhook_ReconciliationFixes`; full current runner is blocked at Docker image build. |
| Short token for vendor quick check | Implemented | `VendorBoardController`; `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest.usesThreeDigitTokenOnlyAfterEveryTwoDigitTokenIsOccupied`. |
| Real-time vendor dashboard via WebSocket | Implemented; browser test pending | `frontend/src/pages/VendorDashboard.jsx`, `OrderService`; current Playwright test `tests/e2e/specs/vendor-flow.spec.js` is unverified. |
| Glance/high-contrast dashboard | Present in UI | `frontend/src/pages/VendorDashboard.jsx` and `frontend/src/index.css`; no dedicated visual regression test. |
| Risk scoring / ML anomaly scoring | Partial prototype only | `ml-service/app.py`, `ml-service/train_models.py`; no current full-runner integration/evaluation evidence. Synthetic-data model is not production-grade. |
| Research mode/data collection | Present, not fully validated in this run | `ResearchModeService`, `ResearchModeController`, `ResearchSession`; no dedicated coverage identified in the unified test evidence. |
| Escalate unresolved expired orders to incident log | Implemented in source; verification not shown by current runner attempt | `ReconciliationService`, `IncidentLogService`, `IncidentLog`; no passing current-run output due unavailable Docker. |
| OTP authentication and verified registration | Implemented; response secrecy tested | `AttackSuiteTest.registrationWithoutOtpProofIsRejected`, `otpIsNotReturnedToClientByDefault`; no test proves OTP is absent from captured backend logs. Production SMS delivery is not configured. |
| Webhook HMAC/timestamp/event-id/amount validation | Partial test proof | `AttackSuiteTest.testForgedWebhook_BadSignature`, `webhookWithoutSignatureIsRejected`, `webhookMissingEventIdOrAmountIsRejected`, `testDuplicateWebhook_Idempotent`, `testWrongAmountWebhook_Rejected`. No stale-timestamp replay test exists. |
| Vendor data isolation | HTTP order/token isolation tested | `AttackSuiteTest.vendorCannotReadAnotherVendorsOrderByCode`; public DTO privacy in `publicVendorResponseOmitsPhoneAndUpi`. |
| WebSocket vendor-topic isolation | Interceptor unit-tested | `WebSocketSecurityConfigTest.rejectsSubscriptionToAnotherVendorTopic`; no end-to-end cross-topic socket test. |
| Persistent scan counts on vendor board | Implemented and tested in prior green Step 3 run | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard`; smoke proof recorded in `docs/PROGRESS.md`. |
| Unique active tokens per vendor and 2→3 digit fallback | Implemented and tested in prior green Step 3 run | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest.usesThreeDigitTokenOnlyAfterEveryTwoDigitTokenIsOccupied` and `usesTwoDigitTokenWhenOneIsAvailable`. |
| Fake payment screen without a webhook expires | Test waits for scheduled expiry | `AttackSuiteTest.testFakeModdedAppScreen_NoWebhook` polls DB until status is `EXPIRED` after the configured five-second expiry, up to a 12-second test deadline. |
| Lost webhook recovered without manual order status mutation | Mock drop plus reconciliation | `AttackSuiteTest.testLostWebhook_ReconciliationFixes` calls mock `scenario=drop`, asserts order remains `PENDING`, invokes `reconcilePendingOrders()`, then asserts `PAID`. |
| Voice announcement | Implemented; opt-in | `frontend/src/pages/VendorDashboard.jsx`; commit `df64391`, tag `voice-pass`; browser behavior not yet verified. |
| Provider-neutral deployment preparation | Prepared, not deployed | `docker-compose.production.yml`, `frontend/nginx.production.conf`, `docs/deployment-prep.md`; commit `d7f2e68`, tag `deploy-prep-pass`. Hosting provider and credentials require user action. |
| Playwright browser workflow, screenshots and webhook-to-board p95 | In progress, unverified | `tests/e2e/`; current full runner stopped before Maven/smoke due unavailable Docker engine. No screenshots/p95 are claimed. |
| UI redesign | Waiting at required design approval gate | No redesign implementation. Create `docs/design-plan.md`, run gate, then wait for user approval before building. |
| Real-phone test, vendor survey, live gateway validation, deployment | Not performed; user/manual work | Explicitly out of agent scope in the supplied gates. See `docs/deployment-prep.md` and `docs/limitations.md`. |

## Not fixed / evidence gaps

- No test proves rejection of a correctly HMAC-signed webhook carrying an expired/replayed timestamp. `testReplayOldOrderCode_Invalid` concerns reuse of an old order code and is not timestamp replay coverage.
- OTP response omission is tested, but OTP non-logging is not proven by a log-capture test.
- WebSocket cross-topic rejection is interceptor-level unit coverage only, not an end-to-end socket test.
- Duplicate event coverage does not count WebSocket announcements or test simultaneous duplicate deliveries.
- The current full runner is blocked because the Docker Desktop Linux engine named pipe is unavailable; Maven and smoke checks did not execute in this run.
- npm reported 15 audit findings (14 moderate, 1 high); these remain untriaged.
- Production SMS, live gateway verification, hosting provider selection, deployment, phone testing, and vendor survey remain outstanding.
- No verified Playwright screenshots or webhook-to-board p95 latency are available yet.
