# Test report

## Final local gate

- Command: `pwsh -NoProfile -File tests/run-all.ps1`
- Result: exit code 0.
- Maven: latest gate, 25 tests, 0 failures, 0 errors, 0 skipped.
- Docker/API smoke: passed, including migrations on an empty temporary database, persisted scan session, mock checkout, and mock-admin route denial at the frontend edge.
- Playwright: final gate, 2 passed in 29.6 seconds. The live app flow covered scan, Scanned counter, amount entry, token and amber row, mock payment, live green board row without refresh, customer Verified state, abandon, and red expired row.
- Final runner output excerpt: see [docs/test-run-output.txt](docs/test-run-output.txt). Full raw transcript: ignored `tests/artifacts/close-gaps-final-run-1.txt`.

## Browser-driven authentication

The full Playwright gate also registers a vendor through the browser UI, verifies the OTP, submits a profile with a UPI ID, logs out, logs in with a newly issued OTP, checks wrong-code and lockout messages, and switches the visible auth screen to Hindi. OTP values and phone fields are hidden in the saved screenshots. In addition, `AttackSuiteTest.otpIsNotReturnedToClientByDefault` checks the default API response and captured application logs. The full-runner local E2E explicitly enables the dev-only OTP response for the automated browser test. The latest full gate reported 25 Maven tests and 2 Playwright tests passing; the full raw output is ignored at `tests/artifacts/close-gaps-final-run-1.txt`.

- [Registration form (redacted)](docs/test-evidence/auth-01-registration-details-redacted.png)
- [Wrong OTP message](docs/test-evidence/auth-02-wrong-code.png)
- [OTP attempt limit](docs/test-evidence/auth-03-rate-limited.png)
- [Hindi auth screen](docs/test-evidence/auth-04-hindi-rate-limit.png)

## Registration invite-code coverage

`VendorInviteCodeTest` runs with a test-only configured code and verifies missing and incorrect values return 403 with a clear message, while the correct code registers successfully. The UI registration journey passes with `INVITE_CODE` deliberately unset by the full-runner test environment, preserving local-development registration. Production configuration is documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Webhook-to-board latency

The latest local run recorded 20 samples from the backend webhook-controller received timestamp until the paid state appeared in the vendor board DOM. The measured p95 was **99 ms**. Samples and method label are in ignored `tests/artifacts/latency.json`. This is a local mock-stack measurement, not a production performance result.

## Auth redesign review

The comparison against the pre-redesign files found no lost authentication or profile capability. The only removed item was the decorative security badge, restored as localized one-time-code guidance. See [docs/auth-redesign-review.md](docs/auth-redesign-review.md).

## Screenshots

Live application flow screenshots from the latest UI run (reviewed and token/order references redacted):

- [Initial vendor board](docs/test-evidence/01-vendor-board-initial.png)
- [Customer scan](docs/test-evidence/02-customer-scan.png)
- [Scanned counter](docs/test-evidence/03-vendor-scanned-counter.png)
- [Amount page](docs/test-evidence/04-customer-amount.png)
- [Token page](docs/test-evidence/05-customer-token-redacted.png)
- [Pending amber row](docs/test-evidence/06-vendor-pending-amber.png)
- [Paid green row](docs/test-evidence/08-vendor-paid-green.png)
- [Customer verified](docs/test-evidence/09-customer-verified.png)
- [Abandoned order red row](docs/test-evidence/10-vendor-abandoned-red.png)

Static design preview screenshots at 360x820 are in [docs/design-preview/screenshots](docs/design-preview/screenshots/): customer amount, token waiting, Verified, Expired, Late payment, vendor board light/dark, glance mode, printable QR card, and Hindi header. These use fake data; the QR is a placeholder only. Quick amount and Mark-as-served remain planned examples and were not implemented.

## Dependency audit

The current lockfile audit reports four vulnerable package groups and ten advisory records. Runtime React Router and dev/build-only Vite/esbuild exposure, severities, and major-upgrade rationale are listed in [docs/npm-audit.md](docs/npm-audit.md). No major dependency update was applied; no force fix was run.

## Known test limits

The Playwright run uses the local mock gateway. No live gateway, real phone, production hosting, or vendor survey was used. Current known gaps are listed in [docs/limitations.md](docs/limitations.md) and [docs/compliance-matrix.md](docs/compliance-matrix.md).
