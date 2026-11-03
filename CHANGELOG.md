# Changelog

- Added a unified PowerShell test entry point for Compose validation, backend Maven tests, and Step 1 end-to-end smoke checks.
- Enforced HMAC webhook verification, required payload fields, amount matching, and unique-event idempotency; added an additive OTP hash column widening migration.
- Required verified OTP proof for vendor registration, removed OTP values from default responses/logs, added OTP throttling, and exposed vendor public DTOs without phone or UPI identifiers.
- Scoped order reads and vendor WebSocket topics to the authenticated vendor; limited forwarded-IP trust to configured proxies and protected mock admin/attack routes.
- Added regression coverage for forged, unsigned, malformed, duplicate, and wrong-amount webhooks; OTP registration and secrecy; vendor data isolation; public DTO privacy; and WebSocket topic isolation.
- Replaced misleading Step 2/attack documentation claims with the tested behavior and known test limitations.
