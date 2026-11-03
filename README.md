# BolPay - Payment Verification Platform

A payment verification dashboard prototype for small Indian vendors (paratha stalls, tea carts, autos) to help review payment status against server-side gateway events.

## Core Problem

BolPay displays payment state for an order created before checkout. The local mock gateway and automated tests exercise this flow; a production gateway, operational controls, and real-world validation are not established by this repository. The UI is not a guarantee against fraud.

## System Overview

BolPay provides:
- **Token-based verification**: Short tokens (e.g., K72) for quick vendor checks
- **Real-time dashboard**: WebSocket-powered live payment notifications
- **Glance mode**: High-contrast simplified view for busy vendors
- **Risk scoring prototype**: ML service integration for anomaly scoring
- **Research mode**: Data collection for ML model training
- **Incident logging**: Automatic escalation of unresolved expired orders

## Architecture

```mermaid
graph TB
    subgraph "Customer Side"
        C[Customer] -->|Scans QR| QR[QR Code]
        QR -->|Opens| VP[Vendor Page /v/{slug}]
        VP -->|Taps Pay| BE[Backend API]
    end
    
    subgraph "Backend - Spring Boot"
        BE -->|Creates Order| PG[Payment Gateway]
        BE -->|UPI Intent Link| VP
        PG -->|Webhook| WH[Webhook Handler]
        WH -->|Verifies| DB[(PostgreSQL)]
        WH -->|Pushes Event| WS[WebSocket]
    end
    
    subgraph "Vendor Side"
        V[Vendor] -->|Logs In| BE
        BE -->|OTP| V
        V -->|Dashboard| VD[Vendor Dashboard]
        WS -->|Live Updates| VD
        VD -->|Quick Check| BE
    end
    
    subgraph "ML Service - Flask"
        BE -->|Score Order| ML[ML Service]
        ML -->|Fraud Score| BE
    end
    
    subgraph "Frontend - React"
        VP -->|React + Vite| FE[Frontend]
        VD -->|React + Vite| FE
    end
    
    subgraph "Infrastructure"
        DB
        DC[Docker Compose]
    end
    
    DC -->|Orchestrates| PG
    DC -->|Orchestrates| BE
    DC -->|Orchestrates| ML
    DC -->|Orchestrates| FE
    DC -->|Orchestrates| DB
```

## Tech Stack

- **Backend**: Java 17, Spring Boot 3, Spring Security, Spring Data JPA, PostgreSQL, Flyway migrations, WebSocket (STOMP)
- **ML Service**: Python Flask microservice (scikit-learn, pandas, joblib)
- **Frontend**: React + Vite + Tailwind
- **Payment Gateway**: CashfreeGateway (test/sandbox) and MockGateway (default, demo mode)
- **Infrastructure**: Docker Compose

## Data Model

- **vendors**: Phone number, UPI ID, business name, slug, description, banner, menu items
- **orders**: Status (PENDING/PAID/EXPIRED/LATE_PAID/FLAGGED), amount, order code, token, payment_session_id, gateway ref, timestamps, device/payer hashes, escalate_at
- **webhook_events**: Event ID (unique for idempotency), order ID, gateway, payload, status
- **fraud_flags**: Order ID, anomaly score, is_suspicious, reasons, model version
- **otp_sessions**: Phone number, OTP code (SHA-256 hashed), expiry, usage tracking
- **incident_log**: Order ID, vendor ID, incident type, description, resolved status
- **research_session**: Vendor ID, label (honest/simulated_fake), start/end time, order counts

## Order Timers

Configurable timers (via environment variables):
- **slow-after**: 60 seconds - marks order as "slow" for vendor attention
- **expire-after**: 150 seconds - order expires if unpaid
- **escalate-after**: 240 seconds - unresolved expired orders escalate to incident log

## Project status

This repository is a prototype under active verification. See [docs/PROGRESS.md](docs/PROGRESS.md), [docs/limitations.md](docs/limitations.md), and [docs/attack-results.md](docs/attack-results.md) for verified status and gaps.

## Setup Instructions

### Prerequisites

- Docker and Docker Compose installed
- Java 17 (for local development)
- Node.js 20 (for local development)
- Python 3.11 (for local development)

### Quick Start with Docker

1. Clone the repository and navigate to the project directory:
```bash
cd bolpay
```

2. Copy the environment example file:
```bash
cp .env.example .env
```

3. Start all services with Docker Compose:
```bash
docker-compose up -d
```

4. The services will be available at:
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- ML Service: http://localhost:5000
- PostgreSQL: localhost:5432

### Local Development

#### Backend (Spring Boot)
```bash
cd backend
mvn spring-boot:run
```

#### ML Service (Flask)
```bash
cd ml-service
pip install -r requirements.txt
python app.py
```

#### Frontend (React)
```bash
cd frontend
npm install
npm run dev
```

#### Database (PostgreSQL)
The database is managed by Docker Compose. To start only the database:
```bash
docker-compose up -d postgres
```

## Environment Variables

See `.env.example` for all available configuration options:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: Database credentials
- `JWT_SECRET`: Secret key for JWT authentication (minimum 256 bits for production)
- `PAYMENT_GATEWAY_TYPE`: `mock` (default) or `cashfree`
- `CASHFREE_APP_ID`, `CASHFREE_APP_SECRET`: Cashfree credentials (if using Cashfree)
- `ML_SERVICE_URL`: URL of the ML service
- `CORS_ALLOWED_ORIGINS`: Allowed CORS origins

## Database Migrations

Flyway migrations are located in `backend/src/main/resources/db/migration/`. They run automatically on application startup.

Current migrations:
- `V1__Create_initial_tables.sql`: Creates vendors, orders, webhook_events, fraud_flags, and otp_sessions tables
- `V2__Add_webhook_event_unique_index.sql`: Adds unique index on event_id for idempotency
- `V3__Add_token_and_late_paid.sql`: Adds token, payment_session_id, escalate_at fields to orders, and LATE_PAID status
- `V4__Create_incident_log.sql`: Creates incident_log table for unresolved expired orders
- `V5__Create_research_session.sql`: Creates research_session table for data collection

## Project Structure

```
bolpay/
├── backend/                 # Spring Boot backend
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/bolpay/
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/    # Flyway migrations
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── ml-service/             # Flask ML service
│   ├── app.py
│   ├── requirements.txt
│   ├── models/             # Trained models (gitignored)
│   └── Dockerfile
├── frontend/               # React frontend
│   ├── src/
│   │   ├── main.jsx
│   │   ├── App.jsx
│   │   └── index.css
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── Dockerfile
├── docs/                   # Documentation
├── mock-cashfree/          # Mock Cashfree payment gateway (Flask)
│   ├── app.py
│   ├── requirements.txt
│   └── Dockerfile
├── attack_suite/           # Security testing scripts
│   └── attack_suite.py
├── load_test/              # Load testing scripts
│   └── load_test.py
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

## Security Features

- **IDOR Prevention**: Vendor ID extracted from JWT, not request parameters
- **OTP Hashing**: OTP codes stored as SHA-256 hashes, not plaintext
- **CORS Tightening**: Specific allowed headers, no wildcards
- **Security Headers**: CSP, X-Frame-Options, X-Content-Type-Options, etc.
- **Rate Limiting**: 5 OTP/hour per phone, 20 orders/minute per IP
- **WebSocket JWT Auth**: JWT verification before WebSocket connection
- **Input Validation**: DTO validation on all endpoints
- **Input Sanitization**: HTML entity escaping for user inputs
- **Webhook Security**: Signature verification, timestamp validation, replay attack prevention

## Testing

### Attack Suite
Run the security attack suite to test security controls:
```bash
cd attack_suite
pip install requests
python attack_suite.py
```

### Load Test
Run load tests to measure performance:
```bash
cd load_test
pip install requests
python load_test.py
```

### ML Evaluation
Generate synthetic data and evaluate ML models:
```bash
cd ml-service
pip install -r requirements.txt
python generate_synthetic_data.py
python evaluate_model.py
```

## Limitations

This is a demonstration system. Limitations include:
- Mock payment gateway (not real money movement)
- ML models trained on synthetic data (not production-grade)
- No real-time fraud prevention (scoring only)
- No multi-region deployment
- No automated backups
- No monitoring/alerting system
- No audit trail for admin actions

## Security Notes

- No secrets are stored in the repository
- All sensitive data uses environment variables
- Device and payer identifiers are hashed before storage
- Webhook signatures are verified
- Idempotent webhook handling via unique event_id
- Rate limiting on OTP and order creation
- Input validation everywhere

## License

MIT
