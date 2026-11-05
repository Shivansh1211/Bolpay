# Known limitations

- Payments use the local mock gateway by default. Live Cashfree credentials, account settings, webhook registration, and callback verification have not been completed.
- OTP delivery is a development flow. Production SMS delivery and provider controls are not configured.
- OTP rate limits and failed-attempt tracking are process-local and reset on restart.
- `AttackSuiteTest.otpIsNotReturnedToClientByDefault` verifies the default API response and captured logs. Local E2E explicitly enables development OTP behavior for test registration.
- STOMP authentication and vendor topic isolation are verified locally over a signed JWT connection. This does not provide load, browser compatibility, or production infrastructure validation.
- Duplicate webhook handling is not exercised under concurrent delivery, and emitted WebSocket announcement counts are not asserted.
- Playwright covers the mock flow, live board updates, voice, and scheduled expiry. It is not a real-phone or live-provider test. The latest measured local webhook-received-to-board-DOM p95 was 110 ms across 20 samples; this is not a production performance guarantee.
- No hosting provider has been selected and no deployment has occurred.
- The user approved the design plan on 2026-11-05 and the live UI redesign is implemented. Quick amount and Mark as served remain preview-only examples.
- The wordmark uses Noto Sans Bold converted to SVG paths; Noto Sans Devanagari is the OFL companion. No font binaries are bundled.
- Registration supports an optional server-side `INVITE_CODE` gate. When unset, registration is open; when set, possession of the code is required but does not establish the invitee’s identity. Production must store/manage the code securely if this gate is desired.
- A dedicated “Waking up the server…” state and safe retry/backoff behavior for sleeping hosts are not implemented.
- The current frontend lockfile audit reports 4 vulnerable package groups (npm package-group summary: 3 moderate, 1 high) across 11 advisory records. The direct React Router runtime package has two moderate advisories; Vite/esbuild findings are development/build-only. All audit-suggested remediations require major upgrades, so no dependency was changed to chase green. See [docs/npm-audit.md](npm-audit.md).
- The prototype is not production-ready and does not claim to prevent scams.
