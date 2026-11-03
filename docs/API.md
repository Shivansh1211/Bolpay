# BolPay API Documentation

## Base URL
- Production: `https://api.bolpay.com`
- Development: `http://localhost:8080`

## Authentication

Most endpoints require JWT authentication. Include the token in the Authorization header:
```
Authorization: Bearer <token>
```

## Public Endpoints

### Get Vendor by Slug
```http
GET /api/public/vendors/{slug}
```

Returns vendor information for the public customer page.

**Response:**
```json
{
  "id": 1,
  "businessName": "Tea Stall",
  "slug": "tea-stall",
  "description": "Fresh tea and snacks",
  "bannerUrl": "https://example.com/banner.jpg"
}
```

### Create Order
```http
POST /api/orders/create
Content-Type: application/json

{
  "vendorSlug": "tea-stall",
  "amount": 50.00
}
```

Creates a new order and returns order details with token.

**Response:**
```json
{
  "id": 1,
  "orderCode": "ABC123",
  "token": "K72",
  "paymentSessionId": "sess_123",
  "amount": 50.00,
  "status": "PENDING",
  "expiresAt": "2025-01-01T10:05:00",
  "upiIntentLink": "upi://pay?..."
}
```

### Quick Check Order
```http
GET /api/orders/quick-check/{orderCode}
```

Check order status by order code (requires authentication).

**Response:**
```json
{
  "found": true,
  "status": "PAID",
  "amount": 50.00,
  "paidAt": "2025-01-01T10:02:00"
}
```

## Authentication Endpoints

### Send OTP
```http
POST /api/auth/send-otp
Content-Type: application/json

{
  "phoneNumber": "9876543210"
}
```

Sends OTP to the phone number (mock: logs to console).

**Rate Limit:** 5 requests per hour per phone number.

### Verify OTP
```http
POST /api/auth/verify-otp
Content-Type: application/json

{
  "phoneNumber": "9876543210",
  "otpCode": "123456"
}
```

Verifies OTP and returns JWT token.

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "vendorId": 1
}
```

## Vendor Endpoints (Authenticated)

### Get Vendor Board
```http
GET /api/vendor/board
Authorization: Bearer <token>
```

Returns dashboard data including paying now, recent paid orders, waiting orders, and incidents.

**Response:**
```json
{
  "payingNow": 3,
  "paidLast10Min": 5,
  "paidOrders": [...],
  "waitingOrders": [...],
  "allOrders": [...],
  "unresolvedIncidents": [...]
}
```

### Quick Check by Token
```http
GET /api/vendor/orders/check/{token}
Authorization: Bearer <token>
```

Check order status by token for the authenticated vendor.

**Response:**
```json
{
  "found": true,
  "token": "K72",
  "status": "PAID",
  "amount": 50.00,
  "paidAt": "2025-01-01T10:02:00"
}
```

### Get Incidents
```http
GET /api/vendor/incidents
Authorization: Bearer <token>
```

Returns incident log entries for the vendor.

### Create Research Session
```http
POST /api/vendor/research/sessions
Authorization: Bearer <token>
Content-Type: application/json

{
  "label": "honest"
}
```

Creates a research session for data collection. Label can be "honest" or "simulated_fake".

### Get Research Report
```http
GET /api/vendor/research/report
Authorization: Bearer <token>
```

Returns research data summary.

### Export Research Data
```http
GET /api/vendor/research/export
Authorization: Bearer <token>
```

Exports research data as CSV.

## Webhook Endpoints

### Payment Webhook
```http
POST /api/webhook/payment
X-Webhook-Signature: <hmac_sha256_signature>
X-Webhook-Timestamp: <unix_timestamp>
X-Webhook-Version: 2025-01-01
Content-Type: application/json

{
  "event_id": "evt_123",
  "order_code": "ABC123",
  "amount": 50.00,
  "status": "PAID",
  "gateway_ref": "gw_123"
}
```

Webhook endpoint for payment gateway notifications. Requires signature verification and timestamp validation (max 5 minutes old).

## ML Service Endpoints

### Health Check
```http
GET http://localhost:5000/health
```

**Response:**
```json
{
  "status": "healthy",
  "model_loaded": true
}
```

### Model Info
```http
GET http://localhost:5000/model-info
```

Returns model information and feature list.

### Score Order
```http
POST http://localhost:5000/score
Content-Type: application/json

{
  "features": {
    "seconds_to_first_attempt": 45.0,
    "expired_orders_last_1h": 0,
    "expired_orders_last_24h": 1,
    "expired_orders_last_15min": 0,
    "pending_orders_last_10min": 2,
    "amount_z_score": 0.5,
    "hour_of_day": 14,
    "time_between_repeat_orders": 300.0,
    "expiry_ratio": 0.1,
    "time_pending": 60.0,
    "bulk_order_count_per_device": 1
  }
}
```

Returns fraud detection score.

**Response:**
```json
{
  "anomaly_score": 0.15,
  "is_suspicious": false,
  "reasons": [],
  "model_version": "1.0.0"
}
```

## Error Responses

All endpoints may return error responses:

```json
{
  "error": "Error message",
  "status": 400
}
```

Common status codes:
- `400`: Bad Request (validation error)
- `401`: Unauthorized (missing or invalid token)
- `403`: Forbidden (insufficient permissions)
- `404`: Not Found
- `429`: Too Many Requests (rate limit exceeded)
- `500`: Internal Server Error
