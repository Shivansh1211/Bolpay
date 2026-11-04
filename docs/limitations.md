# Known limitations

- Payments use the local mock gateway by default. Live Cashfree credentials, account settings, webhook registration, and callback verification have not been completed.
- OTP delivery is a development flow. Production SMS delivery and provider controls are not configured.
- OTP rate limits and failed-attempt tracking are process-local and reset on restart.
- `AttackSuiteTest.otpIsNotReturnedToClientByDefault` verifies the default API response and captured logs. Local E2E explicitly enables development OTP behavior for test registration.
- STOMP authentication and vendor topic isolation are verified locally over a signed JWT connection. This does not provide load, browser compatibility, or production infrastructure validation.
- Duplicate webhook handling is not exercised under concurrent delivery, and emitted WebSocket announcement counts are not asserted.
- Playwright covers the mock flow, live board updates, voice, and scheduled expiry. It is not a real-phone or live-provider test. The measured local webhook-received-to-board-DOM p95 was 90 ms across 20 samples; this is not a production performance guarantee.
- No hosting provider has been selected and no deployment has occurred.
- UI redesign has not started. Prepare `docs/design-plan.md` and wait for approval before implementation.
- Frontend npm audit reported 15 findings (14 moderate, 1 high); they remain untriaged.
- The prototype is not production-ready and does not claim to prevent scams.
