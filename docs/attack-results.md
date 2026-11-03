# Attack test results

## Latest verified run

Command: `pwsh -File tests/run-all.ps1` (Docker Compose, PostgreSQL 15, Spring Boot test profile, mock payment gateway).

Result: **14 Maven tests passed, 0 failed, 0 errored, 0 skipped**, followed by passing API/Compose smoke checks ending in `ALL TESTS PASSED.` This is local automated evidence, not proof of production security or fraud prevention.

## Coverage in the verified suite

- Invalid and missing webhook signatures are rejected; payloads missing event ID or amount are rejected.
- Duplicate webhook event IDs are idempotent; a wrong amount does not mark the order paid.
- Registration without OTP proof is rejected; OTP is omitted from the default response.
- Public vendor responses omit phone and UPI values; a vendor cannot read another vendor's order by code or quick-check token.
- A WebSocket subscription to another vendor's topic is rejected.
- Other suite cases exercise payment state/replay scenarios.

## Test limitations

- The fake-screen test waits two seconds, then directly changes the row to EXPIRED. It does not prove that the real expiry scheduler fires.
- The lost-webhook test directly marks the order PAID. It does not call a reconciliation adapter or prove recovery after a dropped gateway webhook.
- The duplicate test checks the final paid state but does not count emitted WebSocket announcements or exercise simultaneous duplicate deliveries.
- This run did not include Playwright browser coverage, a sustained load test, real-phone use, or a live payment gateway.
- Webhook signature verification uses the local mock secret. Cashfree account behavior is not validated here.

Do not interpret these test results as preventing scams. See [threat-model.md](threat-model.md) and [limitations.md](limitations.md).
