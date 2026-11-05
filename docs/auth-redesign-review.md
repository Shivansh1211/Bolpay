# Login and registration redesign review

Reviewed the auth-page changes in commit `7cb8e75` against its parent `8542903` (`git diff 8542903..7cb8e75 -- frontend/src/pages/LoginPage.jsx frontend/src/pages/RegisterPage.jsx`).

| Flow or field | Before and after | Finding |
|---|---|---|
| Login phone and one-time-code steps | Both versions request a code, verify it, and disable phone editing after sending. | Preserved. Current browser test proves the real sequence. |
| Existing-vendor login / new-vendor routing | Both versions store the auth response and route new users to registration and existing vendors to the dashboard. | Preserved. Current browser test proves registration, logout, and fresh login. |
| Registration phone, business name, UPI ID | Present in both versions. | Preserved. The UI test submits phone, business name, and UPI ID. |
| Optional slug, description, banner URL, menu JSON | Present in both versions. | Preserved. The blank optional slug previously failed backend validation; the UI now sends `null` so backend auto-generation works. |
| Local demo OTP display | Present in both versions, driven only by the API response when the dev flag is enabled. | Preserved and marked in tests; saved screenshots hide the code. Default-mode API/log protection remains covered by `AttackSuiteTest.otpIsNotReturnedToClientByDefault`. |
| Phone prefill from auth context | Before, a `useState` initializer performed a state update as a side effect; after, `useEffect` performs that prefill. | Behavior preserved with the correct lifecycle hook. |
| `updateUser` auth-context method | Imported/destructured in old RegisterPage but never called. | No behavior was lost; removed unused reference. |
| “Secured by BolPay” lock badge | Decorative copy and icon at the bottom of both old pages. | It was removed by the redesign. Equivalent localized one-time-code security guidance has been restored on both screens. |

**Conclusion:** No login or registration capability was lost in the redesign. The only removed behavior-adjacent content was the decorative security badge; it has been restored as localized guidance. The browser proof is in `tests/e2e/specs/vendor-flow.spec.js` and redacted screenshots are in `docs/test-evidence/auth-*.png`.
