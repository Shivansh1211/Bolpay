# Known limitations

- The payment integration defaults to a local mock gateway. Live Cashfree credentials, account configuration, webhook registration, and production callback verification have not been completed.
- OTP delivery is a development flow; production SMS delivery and provider controls are not configured.
- OTP rate limiting and failed-attempt tracking are process-local, not shared across replicas.
- The fake-screen expiry and lost-webhook tests currently use direct database/service state changes instead of exercising real expiry and gateway reconciliation.
- Playwright browser coverage, attack automation beyond the current JUnit cases, load measurements, and real-phone checks remain outstanding.
- No hosting provider has been chosen; no deployment has occurred.
- UI redesign is pending the required review of `docs/design-plan.md`.
- The prototype is not a guarantee against fraud and is not represented as production-ready.
