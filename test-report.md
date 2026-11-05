# Test report

## Final local gate

- Command: `pwsh -NoProfile -File tests/run-all.ps1`
- Result: exit code 0.
- Maven: 22 tests, 0 failures, 0 errors, 0 skipped.
- Docker/API smoke: passed, including migrations on an empty temporary database, persisted scan session, mock checkout, and mock-admin route denial at the frontend edge.
- Playwright: 1 passed in 14.2 seconds. The live app flow covered scan, Scanned counter, amount entry, token and amber row, mock payment, live green board row without refresh, customer Verified state, abandon, and red expired row.
- Final runner output excerpt: see [docs/test-run-output.txt](docs/test-run-output.txt). Full raw transcript: ignored `tests/artifacts/ui-final-output.txt`.

## Webhook-to-board latency

The latest local run recorded 20 samples from the backend webhook-controller received timestamp until the paid state appeared in the vendor board DOM. The measured p95 was **86 ms**. Samples and method label are in ignored `tests/artifacts/latency.json`. This is a local mock-stack measurement, not a production performance result.

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

## Known test limits

The Playwright run uses the local mock gateway. No live gateway, real phone, production hosting, or vendor survey was used. Current known gaps are listed in [docs/limitations.md](docs/limitations.md) and [docs/compliance-matrix.md](docs/compliance-matrix.md).
