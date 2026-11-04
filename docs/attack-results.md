# Attack test results

## Last recorded green run

Command: `pwsh -File tests/run-all.ps1` (Docker Compose, PostgreSQL 15, Spring Boot test profile, mock payment gateway).

The green result recorded in `docs/PROGRESS.md` is from the deploy-preparation tagged commit, not the current uncommitted Playwright work. It reported **18 Maven tests passed, 0 failed, 0 errored, 0 skipped**, followed by passing API/Compose smoke checks ending in `ALL TESTS PASSED.` This is local automated evidence, not proof of production security or fraud prevention.

The latest attempted command on the current working tree was `pwsh -NoProfile -File tests/run-all.ps1`. Compose configuration checks passed, then the production frontend build failed because the Docker Desktop Linux engine pipe was unavailable. Maven and smoke checks were not reached. Full output is recorded in `docs/PROGRESS.md`.

## Coverage in the verified suite

- Invalid and missing webhook signatures are rejected; payloads missing event ID or amount are rejected.
- A correctly signed webhook with an expired/replayed timestamp is not covered by a test. `testReplayOldOrderCode_Invalid` tests an old order code, not webhook timestamp replay.
- Duplicate webhook event IDs are idempotent; a wrong amount does not mark the order paid.
- Registration without OTP proof is rejected; OTP is omitted from the default response.
- No test captures application logs to verify OTP values are never logged.
- Public vendor responses omit phone and UPI values; a vendor cannot read another vendor's order by code or quick-check token.
- A WebSocket subscription to another vendor's topic is rejected.
- Concurrent order creation is checked for distinct active tokens; token fallback is checked after all 2-digit candidates are occupied.
- `AttackSuiteTest.testFakeModdedAppScreen_NoWebhook` polls persisted order status until scheduled expiry marks it `EXPIRED`; it does not set the status manually.
- `AttackSuiteTest.testLostWebhook_ReconciliationFixes` uses mock `scenario=drop`, verifies no webhook changed the order (`PENDING`), then invokes reconciliation against the gateway's retained `PAID` state. It does not manually edit order status.
- Other suite cases exercise payment state/replay scenarios.

## Test limitations

- The duplicate test checks the final paid state but does not count emitted WebSocket announcements or exercise simultaneous duplicate deliveries.
- This run did not include Playwright browser coverage, a sustained load test, real-phone use, or a live payment gateway.
- Webhook signature verification uses the local mock secret. Cashfree account behavior is not validated here.

Do not interpret these test results as preventing scams. See [threat-model.md](threat-model.md) and [limitations.md](limitations.md).
