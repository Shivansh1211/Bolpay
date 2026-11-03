# Known limitations

- The payment integration defaults to a local mock gateway. Live Cashfree credentials, account configuration, webhook registration, and production callback verification have not been completed.
- OTP delivery is a development flow; production SMS delivery and provider controls are not configured.
- OTP rate limiting and failed-attempt tracking are process-local, not shared across replicas.
- The duplicate webhook test does not count WebSocket announcements or exercise simultaneous duplicate deliveries; the late-payment case still directly changes the order to EXPIRED.
- Playwright browser coverage, attack automation beyond the current JUnit cases, load measurements, and real-phone checks remain outstanding.
- No hosting provider has been chosen; no deployment has occurred.
- UI redesign is pending the required review of `docs/design-plan.md`.
- The prototype is not a guarantee against fraud and is not represented as production-ready.
- Frontend `npm install` reported 15 audit findings (14 moderate, 1 high) during production image builds. The dependency-level advisories have not yet been triaged or resolved.
