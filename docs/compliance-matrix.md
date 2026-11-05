# Project brief compliance matrix

Statuses describe repository implementation and current local test evidence; they do not imply production validation.

| Brief item | Status | Proof |
|---|---|---|
| Create order before checkout; payment state from server-side gateway events | Implemented for mock; locally verified | `OrderService`; `AttackSuiteTest.testWrongAmountWebhook_Rejected`, `testLostWebhook_ReconciliationFixes`; Playwright mock payment flow in `tests/e2e/specs/vendor-flow.spec.js` |
| Short token for vendor quick check | Implemented | `VendorBoardController`; `OrderTokenAllocationTest` |
| Real-time vendor dashboard | Implemented and browser verified | `WebSocketSecurityConfig`; `WebSocketStompIntegrationTest`; Playwright live paid-green and expired-red rows without refresh |
| High-contrast dashboard | Present | `frontend/src/pages/VendorDashboard.jsx`; no dedicated visual regression suite |
| Risk/ML anomaly scoring | Partial prototype | `ml-service/app.py`, `ml-service/train_models.py`; no production-grade evaluation |
| Research mode/data collection | Present, limited validation | `ResearchModeService`, `ResearchModeController`, `ResearchSession`; full-runner coverage not identified |
| Escalate unresolved expired orders to incident log | Implemented; limited automated proof | `ReconciliationService`, `IncidentLogService`, `IncidentLog`; not specifically asserted by current browser flow |
| OTP authentication and verified registration | Implemented; local proof | `AttackSuiteTest.registrationWithoutOtpProofIsRejected`, `otpIsNotReturnedToClientByDefault`; production SMS is not configured |
| Webhook signature, timestamp, event ID, amount validation | Tested | `webhookWithoutSignatureIsRejected`, `testForgedWebhook_BadSignature`, `correctlySignedWebhookWithStaleTimestampIsRejected`, `webhookMissingEventIdOrAmountIsRejected`, `testDuplicateWebhook_Idempotent`, `testWrongAmountWebhook_Rejected` |
| Vendor data isolation | Tested | `AttackSuiteTest.vendorCannotReadAnotherVendorsOrderByCode`; `publicVendorResponseOmitsPhoneAndUpi` |
| WebSocket topic isolation | Tested over real STOMP/JWT | `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic`; own topic receives event, other vendor topic denied |
| Persisted scan counts | Tested | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard`; Docker smoke confirms scan persistence |
| Unique active tokens; 2→3 digit fallback | Tested | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest` two/three-digit cases |
| Fake payment screen expires without webhook | Tested using real expiry | `AttackSuiteTest.testFakeModdedAppScreen_NoWebhook` waits for scheduled expiry and checks EXPIRED |
| Lost webhook recovery | Tested via drop + reconciliation | `AttackSuiteTest.testLostWebhook_ReconciliationFixes`; uses mock `scenario=drop`, checks PENDING, invokes reconciliation, checks PAID; no manual status edits |
| Voice announcement | Implemented and browser verified | `frontend/src/pages/VendorDashboard.jsx`; full Playwright gate reports voice announcement pass; `voice-pass` tag |
| Provider-neutral deployment preparation | Prepared, not deployed | `docker-compose.production.yml`, `frontend/nginx.production.conf`, `docs/deployment-prep.md`; `deploy-prep-pass` tag |
| Browser flow, screenshots, webhook-to-board p95 | Passed locally | `tests/e2e/specs/vendor-flow.spec.js`; `docs/test-evidence/`; latest 20 samples, p95 86 ms in ignored `tests/artifacts/latency.json` |
| BolPay brand system | Partial; wired and documented | `frontend/public/brand/`, `frontend/index.html`, vendor headers, `VendorQRPage.jsx`, `docs/brand.md`; original SVG wordmark paths are custom geometry, not paths converted from the selected OFL font |
| Checkout/POS/queue pattern research | Completed as pattern notes | `docs/ui-research.md`; 10 linked sources, no third-party assets copied |
| Design plan and static preview | Approved; implementation followed approval | `docs/design-plan.md`, `docs/design-preview/`, 10 360x820 screenshots |
| Vercel frontend routes and deployment preparation | Prepared, not deployed | `frontend/vercel.json`, `docs/deployment.md`; JSON parsed and full local runner passed |
| UI redesign | Implemented after approval; locally verified | `frontend/src/i18n.js`, `frontend/src/pages/`, `frontend/src/index.css`; full Playwright flow in `tests/e2e/specs/vendor-flow.spec.js`; screenshots in `docs/test-evidence/` |
| Live gateway, real-phone test, vendor survey, deployment | Not performed; manual/user work | `docs/deployment-prep.md`, `docs/limitations.md` |

## Security coverage

| Security item | Exact proof |
|---|---|
| Unsigned webhook | `AttackSuiteTest.webhookWithoutSignatureIsRejected` |
| Bad signature | `AttackSuiteTest.testForgedWebhook_BadSignature` |
| Stale timestamp | `AttackSuiteTest.correctlySignedWebhookWithStaleTimestampIsRejected` |
| Duplicate event | `AttackSuiteTest.testDuplicateWebhook_Idempotent` |
| Wrong amount | `AttackSuiteTest.testWrongAmountWebhook_Rejected` |
| Missing event ID/amount | `AttackSuiteTest.webhookMissingEventIdOrAmountIsRejected` |
| Vendor A vs vendor B | `AttackSuiteTest.vendorCannotReadAnotherVendorsOrderByCode`; `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic` |
| WebSocket cross-topic / missing or invalid JWT | `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic`; `missingAndInvalidJwtAreDeniedDuringStompConnect` |
| Registration without OTP | `AttackSuiteTest.registrationWithoutOtpProofIsRejected` |
| OTP absent from API and logs | `AttackSuiteTest.otpIsNotReturnedToClientByDefault` |
| Scan sessions counted | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard` |
| Token uniqueness | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest` fallback cases |

## Not fixed / remaining gaps

- Duplicate event handling is not tested under concurrent delivery, and the test does not count emitted announcements.
- npm reported 15 dependency audit findings (14 moderate, 1 high); they remain untriaged.
- Live gateway/account configuration, production SMS, hosting choice, deployment, real-phone testing, and vendor survey remain outstanding.
- Historical `step2-pass` and `step3-pass` test reruns in temporary worktrees remain optional and outstanding.
- No quick-amount chips or Mark-as-served action; both remain planned preview examples.
- Public registration invite-code enforcement is not implemented; setting `INVITE_CODE` would have no effect.
- The outlined logo was hand-drawn as original vector geometry, not converted from the selected Noto Sans font; the OFL type pairing is documented and used as the live text stack.
- Dedicated cold-start “Waking up the server…” messaging/retry behavior is not implemented; current app uses generic loading states.
- Production SMS delivery, hosting selection, real-phone checks, and vendor survey remain outstanding.
- Local mock tests do not prove production security and do not establish scam prevention.
