# BolPay Security Documentation

## Security Architecture

### Authentication
- JWT-based authentication for vendors
- OTP-based login with SHA-256 hashed storage
- Token expiration: 24 hours (configurable)
- WebSocket connections require JWT verification

### Authorization
- Role-based access control (ROLE_VENDOR)
- Vendor ID extracted from JWT, not request parameters (IDOR prevention)
- Public endpoints limited to vendor lookup and order creation

### Data Protection
- Phone numbers hashed for device fingerprinting
- OTP codes stored as SHA-256 hashes
- Device fingerprints hashed before storage
- Payer phone numbers hashed before storage
- No plaintext sensitive data in database

### Network Security
- ML service on internal network only (no external access)
- Mock Cashfree on internal network only
- Database on internal network only
- CORS restricted to specific origins
- Specific allowed headers (no wildcards)

### Webhook Security
- HMAC-SHA256 signature verification
- Timestamp validation (reject > 5 minutes old)
- Replay attack prevention via unique event_id
- Idempotent webhook processing

### Rate Limiting
- OTP: 5 requests per hour per phone number
- Order creation: 20 requests per minute per IP
- Implemented using Bucket4j

### Input Validation
- DTO validation on all endpoints
- Bean Validation annotations (@NotNull, @Pattern, @Size)
- Custom validators for phone numbers and amounts

### Input Sanitization
- HTML entity escaping for user inputs
- Removal of dangerous characters
- Sanitizer utility class for vendor registration

### Security Headers
- Content-Security-Policy
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block
- Referrer-Policy: strict-origin-when-cross-origin
- Permissions-Policy

## Threat Model

### IDOR (Insecure Direct Object Reference)
**Mitigation**: Vendor ID extracted from JWT authentication object, not from request parameters.

### Replay Attacks
**Mitigation**: Unique event_id for webhooks, timestamp validation (max 5 minutes old).

### XSS (Cross-Site Scripting)
**Mitigation**: Input sanitization, HTML entity escaping, CSP headers.

### CSRF (Cross-Site Request Forgery)
**Mitigation**: SameSite cookie attribute, JWT in Authorization header (not cookies).

### SQL Injection
**Mitigation**: JPA/Hibernate parameterized queries, no raw SQL.

### Brute Force
**Mitigation**: Rate limiting on OTP and order creation endpoints.

### Webhook Forgery
**Mitigation**: HMAC-SHA256 signature verification, timestamp validation.

### Data Exfiltration
**Mitigation**: Internal network for sensitive services, no direct database access from internet.

## Security Testing

### Attack Suite
Run the attack suite to verify security controls:
```bash
cd attack_suite
pip install requests
python attack_suite.py
```

Tests include:
- IDOR attack
- Unauthorized quick check
- Forged webhook
- Webhook missing headers
- Webhook old timestamp (replay)
- Rate limiting OTP
- Rate limiting orders
- XSS injection
- SQL injection

### Manual Testing Checklist
- [ ] Verify OTP is hashed in database
- [ ] Verify vendor ID cannot be modified in requests
- [ ] Verify webhook without signature is rejected
- [ ] Verify webhook with old timestamp is rejected
- [ ] Verify rate limiting works
- [ ] Verify CORS blocks unauthorized origins
- [ ] Verify security headers are present
- [ ] Verify WebSocket requires JWT

## Security Best Practices

### Development
1. Never commit secrets to repository
2. Use environment variables for all sensitive data
3. Enable security headers in all environments
4. Keep dependencies updated
5. Run attack suite before deployments

### Operations
1. Rotate secrets regularly
2. Monitor failed authentication attempts
3. Monitor webhook failures
4. Review incident logs regularly
5. Keep database backups encrypted

### Incident Response
1. Document all security incidents
2. Use incident_log table for tracking
3. Escalate unresolved incidents automatically
4. Review patterns for fraud detection
5. Update ML models with new fraud patterns

## Known Limitations

1. **No MFA**: Only OTP-based authentication (no second factor)
2. **No IP Whitelisting**: Vendors can access from any IP
3. **No Device Fingerprinting**: Basic localStorage-based device ID
4. **No Real-time Fraud Prevention**: ML scoring only, no blocking
5. **No Session Revocation**: JWT cannot be revoked before expiration
6. **No Audit Logging**: No comprehensive audit trail for admin actions

## Future Security Enhancements

1. **MFA**: Add TOTP-based second factor
2. **IP Whitelisting**: Restrict vendor access by IP
3. **Device Binding**: Stronger device fingerprinting
4. **Real-time Blocking**: Block suspicious orders based on ML score
5. **JWT Revocation**: Add token blacklist for revocation
6. **Audit Logging**: Comprehensive audit trail
7. **Webhook Signing**: Use Ed25519 instead of HMAC-SHA256
8. **Rate Limiting**: Per-vendor rate limits in addition to per-IP
9. **CAPTCHA**: Add CAPTCHA for suspicious patterns
10. **Biometric**: Support biometric authentication for vendors
