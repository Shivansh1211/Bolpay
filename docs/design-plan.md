> Status: approved by the user on 2026-11-05. The plan has since been implemented in the live frontend. Quick-amount chips and Mark-as-served remain preview-only and were not implemented.

# BolPay static design plan

This plan was approved and then implemented in the live app. The static preview remains a reference artifact. Quick amount chips and “Mark as served” are shown only to discuss future work; neither is implemented.

## Design direction

Give the order's live state the strongest visual weight on customer and vendor screens. Use a restrained maroon-and-cream frame, crisp type, and direct state labels. Keep payment entry calm and amount-first. The customer should be able to read the amount and payee while standing in a queue; the vendor board should remain legible in bright sun and noisy surroundings.

## Color tokens

| Token | Hex | Use |
|---|---|---|
| Chai maroon | `#4A1D1F` | Brand anchor and restrained section framing |
| Cream | `#FFF8EC` | Light canvas and reverse text on dark |
| Paying teal | `#006B73` | Clock state; separate from maroon and paid green |
| Paid green | `#17803F` on light / `#35C46B` on dark | Check state in both themes |
| Waiting amber | `#7A4500` | Clock state on the vendor board |

The dark preview uses chai maroon as its canvas and cream state labels and icons, retaining the same shapes and words. Expired uses the chai maroon anchor with a cross icon on light surfaces and cream on dark surfaces. Late payment uses an outlined triangle plus a cream/maroon diagonal stripe pattern and “Late payment — not confirmed yet”; it does not introduce another orange or rely on color. Status is always communicated by an icon and a word as well as its visual treatment.

Contrast targets: normal text at least 4.5:1; glance-mode text at least 7:1. Calculated against the preview surfaces: chai maroon on cream 13.38:1; paying teal on cream 5.94:1; paid green on cream 4.74:1 and on maroon 6.23:1; waiting amber on cream 7.43:1; cream on maroon 13.38:1. Glance mode uses cream/maroon text (13.38:1) and the state icon/word, rather than relying on the lower-contrast green alone. Recheck final contrast on implementation before release.

## Type, space, and interaction

- Typeface: Noto Sans with Noto Sans Devanagari fallback; use local/system fallback and no remote font request.
- Type scale: 12 px supporting labels, 14 px metadata, 16 px body and controls, 20 px section title, 28 px amount, 36 px live status; glance mode uses at least 24 px for row state and 18 px for amount.
- Spacing: 4, 8, 12, 16, 24, 32 px. Radius: 4 px for controls, 8 px for grouped panels, 0 px for status rails. Avoid a stack of identical rounded cards.
- Tap targets: at least 48 by 48 CSS px, with visible keyboard focus.
- Status changes use a polite `aria-live` region. Respect `prefers-reduced-motion`; use no essential motion. Keep the preview plain HTML/CSS with no framework or runtime dependency.
- English and Hindi strings live together in one `i18n.js` dictionary. Language selection is explicit; no machine translation at runtime.
- Light and dark themes preserve the same hierarchy, labels, and state shapes.

## Wording rules

Use sentence case, active verbs, and the word “token”. Explain an error and the action that fixes it. If payment has not arrived, say “Payment not confirmed yet.” Never say “lying”. Keep technical terms such as webhook, signature, and reconciliation out of customer and vendor screens.

## 360 px wireframes

```text
CUSTOMER / AMOUNT                 CUSTOMER / TOKEN
+----------------------------+   +----------------------------+
| BolPay                 हिन्दी|   | BolPay                 हिन्दी|
| Pay [vendor name]            |   | Payment status             |
| Amount                       |   | ◷  Waiting for payment     |
| ₹ 0                           |   | Token                      |
| [₹50] [₹100] [₹200] planned  |   |  42                        |
| [Continue]                   |   | Pay before 02:30           |
+----------------------------+   +----------------------------+

VENDOR BOARD / 360                PRINTABLE QR CARD
+----------------------------+   +----------------------------+
| BolPay                 हिन्दी|   |        [large QR]          |
| 3 waiting   1 paid   1 late  |   | Scan to pay BolPay         |
| ◷ 42  ₹100   Waiting         |   | बोलपे से भुगतान करें       |
| ✓ 43  ₹80    Paid            |   | [vendor name]              |
| △ 44  ₹60    Late payment    |   +----------------------------+
+----------------------------+
```

## Screen behavior and states

- Amount: show the payee before amount entry. Preview chips are labeled “planned”; they are not active functionality. One primary action per screen.
- Waiting: clock icon, teal outline and “Waiting for payment”; show token and expiry.
- Verified: check icon, “Verified”, time, and reference. No confetti or ambiguous animation.
- Expired: cross icon and “Expired”; say the customer can ask the vendor for a new token.
- Late payment: striped triangular marker and “Late payment — not confirmed yet”; explain to wait or ask the vendor to check.
- Vendor board: counters plus amber waiting, green paid, red expired, and striped late-payment examples with status word/icon. The hypothetical “Mark as served” control is labeled “planned” and disabled in the preview.
- Glance mode: rows show large token/amount/state, high contrast, no small metadata, 7:1 text contrast target.
- QR card: large high-contrast QR placeholder, BolPay mark, and a short English/Hindi scan instruction.

## Critique and revisions

- Generic blue/purple fintech gradients: replaced with solid cream, ink, and the established maroon anchor; teal appears only as the clock state.
- Identical rounded cards with one soft shadow: replaced with flat sections, square status rails, and limited 4/8 px radii so rows have a clear working-board feel.
- All-caps eyebrow labels: use sentence case and ordinary field labels.
- One accented word in a headline: use plain headlines and make the live state itself the focal point.
- Middle-dot metadata strings: put time, token, and reference on separate labeled lines.
- Arrows on every button: use direct verbs without decorative arrows.

## Preview files

Open `docs/design-preview/index.html` locally. Add `#amount`, `#waiting`, `#verified`, `#expired`, `#late`, `#board`, `#glance`, or `#qr` to view each static scene. `i18n.js` contains the English and Hindi preview strings. Screenshots are in `docs/design-preview/screenshots/`.
