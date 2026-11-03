# BolPay Deployment Guide

## Docker Compose Deployment

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Environment Setup

1. Copy the environment example:
```bash
cp .env.example .env
```

2. Edit `.env` with your configuration:
```bash
# Database
POSTGRES_DB=bolpay
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password

# JWT
JWT_SECRET=your_minimum_256_bit_secret_key_here

# Payment Gateway
PAYMENT_GATEWAY_TYPE=mock
CASHFREE_APP_ID=your_app_id
CASHFREE_APP_SECRET=your_app_secret
CASHFREE_API_URL=https://sandbox.cashfree.com
CASHFREE_WEBHOOK_SECRET=your_webhook_secret

# Order Timers (seconds)
ORDER_SLOW_AFTER=60
ORDER_EXPIRE_AFTER=150
ORDER_ESCALATE_AFTER=240

# Vendor Thresholds
VENDOR_HIGH_VALUE_THRESHOLD=10000
VENDOR_BULK_THRESHOLD=10

# Rate Limiting
RATE_LIMIT_OTP_PER_HOUR=5
RATE_LIMIT_ORDERS_PER_MINUTE=20

# ML Service
ML_SERVICE_URL=http://ml-service:5000

# Logging
LOG_LEVEL=INFO
```

### Start Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Backend (port 8080)
- ML Service (port 5000, internal only)
- Mock Cashfree (port 9000, internal only)
- Frontend (port 5173)

### Check Service Health

```bash
# Backend health
curl http://localhost:8080/actuator/health

# ML service health
curl http://localhost:5000/health

# Mock Cashfree health
curl http://localhost:9000/health
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f postgres
```

### Stop Services

```bash
docker-compose down
```

### Database Migrations

Flyway migrations run automatically on backend startup. To manually trigger:

```bash
docker-compose exec backend mvn flyway:migrate
```

## Production Considerations

### Security

1. **Change all default passwords** in production
2. **Use strong JWT secrets** (minimum 256 bits)
3. **Enable HTTPS** using a reverse proxy (nginx, traefik)
4. **Restrict database access** to internal network only
5. **Use secrets management** (AWS Secrets Manager, HashiCorp Vault)

### Scaling

1. **Backend**: Deploy multiple instances behind a load balancer
2. **Database**: Use managed PostgreSQL (RDS, Cloud SQL)
3. **ML Service**: Scale based on request volume
4. **Frontend**: Serve via CDN (Cloudflare, AWS CloudFront)

### Monitoring

1. **Application logs**: Centralize with ELK stack or CloudWatch
2. **Metrics**: Expose via Actuator endpoints
3. **Alerting**: Set up alerts for errors, latency, and failed webhooks
4. **Health checks**: Configure load balancer health checks

### Backups

1. **Database backups**: Automated daily backups with point-in-time recovery
2. **Code backups**: Git repository with proper branching strategy
3. **Configuration backups**: Version control for all config files

### Real Payment Gateway

To use real Cashfree instead of mock:

1. Update `.env`:
```bash
PAYMENT_GATEWAY_TYPE=cashfree
CASHFREE_API_URL=https://api.cashfree.com
```

2. Configure Cashfree webhook URL in Cashfree dashboard:
```
https://your-domain.com/api/webhook/payment
```

3. Update webhook secret in `.env`:
```bash
CASHFREE_WEBHOOK_SECRET=your_real_webhook_secret
```

## Troubleshooting

### Backend fails to start

Check database connection:
```bash
docker-compose logs backend | grep "Connection"
```

Verify database is ready:
```bash
docker-compose exec postgres psql -U postgres -d bolpay -c "SELECT 1"
```

### Webhook not received

Check webhook signature:
```bash
docker-compose logs backend | grep "Webhook"
```

Verify Cashfree webhook configuration points to correct URL.

### ML service unreachable

Check ML service is running:
```bash
docker-compose logs ml-service
```

Verify ML_SERVICE_URL in backend configuration.

### Frontend cannot connect to backend

Check CORS configuration in `application.yml`:
```yaml
cors:
  allowed-origins: https://your-domain.com
```

## Maintenance

### Database Cleanup

Clean up old webhook events and OTP sessions:
```sql
DELETE FROM webhook_events WHERE created_at < NOW() - INTERVAL '30 days';
DELETE FROM otp_sessions WHERE expires_at < NOW() - INTERVAL '7 days';
```

### Log Rotation

Configure log rotation in `docker-compose.yml`:
```yaml
backend:
  logging:
    driver: "json-file"
    options:
      max-size: "10m"
      max-file: "3"
```

### Updates

To update the application:

1. Pull latest code
2. Build new images:
```bash
docker-compose build
```

3. Restart services:
```bash
docker-compose up -d
```

4. Run migrations (if any):
```bash
docker-compose exec backend mvn flyway:migrate
```
