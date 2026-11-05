# BolPay brand usage

## Color anchors

| Use | Color | Hex |
|---|---|---|
| Chai maroon | Primary brand anchor | `#4A1D1F` |
| Paid state on dark surfaces | Paid green | `#35C46B` |
| Paid state on light surfaces | Paid green | `#17803F` |
| Cream | Light background anchor | `#FFF8EC` |

Paid green is a status color. Pair it with a check icon and the word “Paid”; never communicate payment state by color alone. The full interface status palette will be set in the approved design plan.

## Logo and mark rules

- Use the BolPay logo and mark without changing their colors, proportions, or letterforms.
- Preserve each SVG's aspect ratio. Do not stretch, skew, add effects, or recolor the assets.
- Keep clear space around each asset equal to the height of the “B” in the wordmark; do not place text, borders, or other marks in that area.
- Minimum digital size: full logo 120 CSS px wide; mark 24 CSS px square. Minimum print size: full logo 30 mm wide; mark 8 mm square. Use the mark if the full logo would fall below its legible minimum.
- The app header exposes “BolPay” as live, accessible text. The logo SVG contains original outlined vector lettering so it does not depend on a font being installed.

## Typeface decision

Use **Noto Sans** for Latin text with **Noto Sans Devanagari** as its Devanagari companion. Google Fonts metadata identifies Noto Sans as SIL Open Font License (OFL), and the Noto Devanagari repository distributes its font under SIL OFL 1.1. See [Noto Sans metadata](https://github.com/google/fonts/blob/main/ofl/notosans/METADATA.pb) and [Noto Devanagari licensing](https://github.com/notofonts/devanagari).

The pair is selected for coordinated sans-serif forms across English and Hindi. The app references these names in its system font stack but does not bundle, download, or remotely load third-party font files. The custom SVG lettering is outlined path geometry, not copied font software; it was hand-drawn and was not converted from Noto Sans outlines. Live accessible text remains available to assistive technology.

## Files

- `frontend/public/brand/bolpay-logo.svg` — original lockup for the vendor and customer app headers.
- `frontend/public/brand/bolpay-mark.svg` — original mark used on the printable QR card.
- `frontend/public/brand/favicon.svg` — favicon built from the same mark.

These SVGs were created from the supplied palette and the user's authorization to make simple replacement artwork because the expected assets were absent from the checkout. They are original geometric vector drawings and do not reuse third-party logos, fonts, icons, or layouts.
