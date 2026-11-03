# Attack test results

## Latest verified run

Command: `pwsh -File tests/run-all.ps1` (Docker Compose, PostgreSQL 15, Spring Boot test profile, mock payment gateway).

Result: **18 Maven tests passed, 0 failed, 0 errored, 0 skipped**, followed by passing API/Compose smoke checks ending in `ALL TESTS PASSED.` This is local automated evidence, not proof of production security or fraud prevention.

## Coverage in the verified suite

- Invalid and missing webhook signatures are rejected; payloads missing event ID or amount are rejected.
- Duplicate webhook event IDs are idempotent; a wrong amount does not mark the order paid.
- Registration without OTP proof is rejected; OTP is omitted from the default response.
- Public vendor responses omit phone and UPI values; a vendor cannot read another vendor's order by code or quick-check token.
- A WebSocket subscription to another vendor's topic is rejected.
- Concurrent order creation is checked for distinct active tokens; token fallback is checked after all 2-digit candidates are occupied.
- The fake-screen test now waits for the short test expiry timer and observes the scheduled expiry job.
- The lost-webhook test uses the mock gateway's drop option, then calls reconciliation against the gateway's retained PAID state.
- Other suite cases exercise payment state/replay scenarios.

## Test limitations

- The duplicate test checks the final paid state but does not count emitted WebSocket announcements or exercise simultaneous duplicate deliveries.
- This run did not include Playwright browser coverage, a sustained load test, real-phone use, or a live payment gateway.
- Webhook signature verification uses the local mock secret. Cashfree account behavior is not validated here.

Do not interpret these test results as preventing scams. See [threat-model.md](threat-model.md) and [limitations.md](limitations.md).
