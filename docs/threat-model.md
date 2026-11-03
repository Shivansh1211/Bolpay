# Threat model

## Assets

- Correct association between a vendor, order, amount, and payment event.
- Vendor authentication credentials and OTP verification state.
- Vendor phone and UPI identifiers.
- Payment status and the vendor-specific live board.

## Trust boundaries

- Browser and customer-supplied order details are untrusted.
- Webhook requests are untrusted until their timestamp and HMAC signature validate.
- Vendor API requests require a JWT; vendor-scoped endpoints must use its vendor identity.
- WebSocket connections and topic subscriptions require vendor identity and same-vendor topic matching.
- Forwarded client IP headers are only trusted when the direct peer is configured as a trusted proxy.
- The local mock gateway is for development/testing and is not a production payment provider.

## Threats addressed by current code and tests

- Forged, missing-signature, malformed, duplicate, and wrong-amount webhook payloads.
- Unverified vendor registration, default OTP disclosure, and repeated OTP requests/verification attempts.
- Cross-vendor order lookup and WebSocket topic access.
- Public vendor DTO disclosure of phone and UPI fields.
- Unprotected mock administration endpoints.
- Scan count fabrication through an unpersisted client request, and accidental reuse of the same pending vendor token.

## Known limits

OTP rate limits are process-local and reset on restart; they are not distributed controls. Automated tests cover only specified cases and do not establish production security. See [attack-results.md](attack-results.md) and [limitations.md](limitations.md).
