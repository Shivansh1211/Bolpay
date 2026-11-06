# Project brief compliance matrix

Statuses describe repository implementation and current local test evidence; they do not imply production validation.

| Brief item | Status | Proof |
|---|---|---|
| Create order before checkout; payment state from server-side gateway events | Implemented for mock; locally verified | `OrderService`; `AttackSuiteTest.testWrongAmountWebhook_Rejected`, `testLostWebhook_ReconciliationFixes`; Playwright mock payment flow in `tests/e2e/specs/vendor-flow.spec.js` |
| Short token for vendor quick check | Implemented | `VendorBoardController`; `OrderTokenAllocationTest` |
| Real-time vendor dashboard | Implemented and browser verified | `WebSocketSecurityConfig`; `WebSocketStompIntegrationTest`; Playwright live paid-green and expired-red rows without refresh |
| High-contrast dashboard | Present | `frontend/src/pages/VendorDashboard.jsx`; no dedicated visual regression suite |
| Asynchronous review scoring with numeric hashed-identifier features; payment state independent | Implemented and locally tested; informational prototype | `FraudScoringService`, `MlScoringClient`, and all five `FraudScoringServiceTest` cases passed in the latest 31-test Maven run; 500 ms HTTP timeout and numeric-only request verified; payment status unchanged in success/fallback tests |
| Persist and show review flags in vendor board using non-accusatory wording | Source implemented; schema applied; end-to-end flag-to-board flow remains unverified | V8 applied on empty DB in smoke test; `FraudScoringServiceTest` verifies a successful HTTP scoring response is saved to the flag repository mock; `VendorBoardController.fraudFlags` and `VendorDashboard.jsx`; Playwright tests the live board payment states, not a generated flag |
| Reproducible synthetic model comparison with full requested metrics | Generated and reviewed; synthetic-only conclusion | `ml-service/generate_data.py`, `ml-service/train_models.py`; `ml/reports/evaluation.md`, `metrics.json`, and three confusion-matrix JSON files. Random Forest F1 0.995 > rule F1 0.883, but production endpoint uses Isolation Forest (F1 0.801 on this synthetic split). |
| Research mode/data collection | Present, limited validation | `ResearchModeService`, `ResearchModeController`, `ResearchSession`; full-runner coverage not identified |
| Escalate unresolved expired orders to incident log | Implemented; limited automated proof | `ReconciliationService`, `IncidentLogService`, `IncidentLog`; not specifically asserted by current browser flow |
| OTP authentication and verified registration | Implemented; local browser/API proof | UI registration/login flow in `tests/e2e/specs/vendor-flow.spec.js`; `AttackSuiteTest.registrationWithoutOtpProofIsRejected`; `otpIsNotReturnedToClientByDefault`; production SMS is not configured |
| Webhook signature, timestamp, event ID, amount validation | Tested | `webhookWithoutSignatureIsRejected`, `testForgedWebhook_BadSignature`, `correctlySignedWebhookWithStaleTimestampIsRejected`, `webhookMissingEventIdOrAmountIsRejected`, `testDuplicateWebhook_Idempotent`, `testWrongAmountWebhook_Rejected` |
| Vendor data isolation | Tested | `AttackSuiteTest.vendorCannotReadAnotherVendorsOrderByCode`; `publicVendorResponseOmitsPhoneAndUpi` |
| WebSocket topic isolation | Tested over real STOMP/JWT | `WebSocketStompIntegrationTest.signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic`; own topic receives event, other vendor topic denied |
| Persisted scan counts | Tested | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard`; Docker smoke confirms scan persistence |
| Unique active tokens; 2→3 digit fallback | Tested | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest` two/three-digit cases |
| Fake payment screen expires without webhook | Tested using real expiry | `AttackSuiteTest.testFakeModdedAppScreen_NoWebhook` waits for scheduled expiry and checks EXPIRED |
| Lost webhook recovery | Tested via drop + reconciliation | `AttackSuiteTest.testLostWebhook_ReconciliationFixes`; uses mock `scenario=drop`, checks PENDING, invokes reconciliation, checks PAID; no manual status edits |
| Voice announcement | Implemented and browser verified | `frontend/src/pages/VendorDashboard.jsx`; full Playwright gate reports voice announcement pass; `voice-pass` tag |
| Provider-neutral deployment preparation | Prepared, not deployed | `docker-compose.production.yml`, `frontend/nginx.production.conf`, `docs/deployment-prep.md`; `deploy-prep-pass` tag |
| Browser flow, screenshots, webhook-to-board p95 | Passed locally | `tests/e2e/specs/vendor-flow.spec.js`; `docs/test-evidence/`; latest 20 samples, p95 70 ms in ignored `tests/artifacts/latency.json` |
| BolPay brand system | Implemented and documented | `frontend/public/brand/`, `frontend/index.html`, vendor headers, `VendorQRPage.jsx`, `docs/brand.md`; the BolPay wordmark is outlined from Noto Sans Bold, with Noto Sans Devanagari as its OFL companion |
| Checkout/POS/queue pattern research | Completed as pattern notes | `docs/ui-research.md`; 10 linked sources, no third-party assets copied |
| Design plan and static preview | Approved; implementation followed approval | `docs/design-plan.md`, `docs/design-preview/`, 10 360x820 screenshots |
| Vercel frontend routes and deployment preparation | Prepared, not deployed | `frontend/vercel.json`, `docs/deployment.md`; JSON parsed and full local runner passed |
| Frontend dependency audit | Current lockfile audited; findings triaged, no major upgrade applied | `docs/npm-audit.md`; `frontend/package-lock.json` |
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
| Configured invite code missing/wrong/correct | `VendorInviteCodeTest.configuredInviteCodeRejectsMissingCodeWithClearMessage`; `configuredInviteCodeRejectsIncorrectCodeWithClearMessage`; `configuredInviteCodeAllowsRegistrationWithCorrectCode` |
| OTP absent from API and logs | `AttackSuiteTest.otpIsNotReturnedToClientByDefault` |
| Scan sessions counted | `AttackSuiteTest.scanSessionsArePersistedAndCountedForVendorBoard` |
| Token uniqueness | `AttackSuiteTest.concurrentOrderCreationAllocatesDistinctActiveTokens`; `OrderTokenAllocationTest` fallback cases |
| ML unavailable fallback and status independence | Passed in latest Maven run: `FraudScoringServiceTest.unavailableMlUsesThreeTimeoutRuleAndLeavesPaymentFlowUntouched` |
| ML slow request timeout and fallback | Passed in latest Maven run: `FraudScoringServiceTest.scoringClientEnforcesItsShortRequestTimeout`; `FraudScoringServiceTest.slowMlTimesOutIntoRuleFallback` |
| ML available creates persisted review flag without changing status | Passed in latest Maven run: `FraudScoringServiceTest.availableMlCreatesReviewFlagWithoutChangingOrderStatus` |
| No PII in scorer request | Passed in latest Maven run: `FraudScoringServiceTest.scoringRequestContainsOnlyNumericFeaturesAndUsesApiKeyHeader` |

## Not fixed / remaining gaps

- Duplicate event handling is not tested under concurrent delivery, and the test does not count emitted announcements.
- The current npm audit reports 4 vulnerable package groups, including a runtime React Router issue and development-only Vite/esbuild issues; all advisories and major-version-only remediation paths are detailed in `docs/npm-audit.md`.
- Live gateway/account configuration, production SMS, hosting choice, deployment, real-phone testing, and vendor survey remain outstanding.
- Historical `step2-pass` and `step3-pass` test reruns in temporary worktrees remain optional and outstanding.
- No quick-amount chips or Mark-as-served action; both remain planned preview examples.
- `INVITE_CODE` is optional; if unset, registration remains open. When set, the code gates registration but is not identity verification.
- The logo mark remains original geometric artwork; the BolPay wordmark is outlined from Noto Sans Bold. Noto Sans Devanagari is the OFL companion; the font binary is not bundled.
- Dedicated cold-start “Waking up the server…” messaging/retry behavior is not implemented; current app uses generic loading states.
- Production SMS delivery, hosting selection, real-phone checks, and vendor survey remain outstanding.
- Local mock tests do not prove production security and do not establish scam prevention.
- Synthetic metrics are not representative of real incidents or vendor populations; there is no labeled field dataset. ML is only a review aid and must not be used as a payment decision.
- The latest full local gate passed backend ML tests, V1-V8 migration on an empty disposable database, Docker/API smoke, and the Playwright payment lifecycle. The running Flask-model-to-database-flag-to-browser-board path remains unverified end to end; the ML unit tests use a local HTTP fixture and mocked flag repository.
