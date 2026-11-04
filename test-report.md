# Test report

Command: `pwsh -NoProfile -File tests/run-all.ps1`

Result: **PASS** (exit code 0).

- Docker Compose and production Compose validation passed; production frontend/Nginx image built.
- Backend Maven suite: **22 tests, 0 failures, 0 errors, 0 skipped**.
- API and Compose smoke tests passed, including Flyway V1–V7 on disposable databases, scan persistence, mock checkout routing, and admin/attack route 404s.
- Chromium Playwright: **1 passed**. Flow: scan → Scanned counter → amount/token → amber pending row → pay in mock → green row updated live without refresh → customer Payment verified → abandon without webhook → real scheduled expiry turns row red. Voice announcement passed.
- Browser STOMP observed CONNECTED and MESSAGE frames during the live flow, with no ERROR frame.
- Webhook-received timestamp to paid board DOM update: **20 samples; p95 90 ms** (`tests/artifacts/latency.json`, ignored).

## Reviewed screenshots

- [Vendor board initial](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/01-vendor-board-initial.png)
- [Customer scan](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/02-customer-scan.png)
- [Scanned counter](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/03-vendor-scanned-counter.png)
- [Amount entry](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/04-customer-amount.png)
- [Redacted token view](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/05-customer-token-redacted.png)
- [Amber pending row](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/06-vendor-pending-amber.png)
- [Green paid row](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/08-vendor-paid-green.png)
- [Customer verified](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/09-customer-verified.png)
- [Red expired row](/C:/Users/shiva/CascadeProjects/bolpay/docs/test-evidence/10-vendor-abandoned-red.png)

The checkout screenshot containing a visible order reference remains only in ignored raw artifacts and is not committed. The full runner transcript is kept in ignored `tests/artifacts/post-merge-run-output.txt`; `docs/test-run-output.txt` contains a safe excerpt.

## Test boundaries

The test uses the local mock gateway and local Docker environment. It does not validate a live provider, production deployment, real-phone behavior, or sustained load. See `docs/limitations.md`.
