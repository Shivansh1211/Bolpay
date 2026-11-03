-- Vendors table
CREATE TABLE vendors (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    upi_id VARCHAR(100),
    business_name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    banner_url VARCHAR(500),
    menu_items JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Orders table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'FLAGGED')),
    amount DECIMAL(10, 2) NOT NULL,
    order_code VARCHAR(20) UNIQUE NOT NULL,
    gateway_ref VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    device_fingerprint_hash VARCHAR(64),
    payer_hash VARCHAR(64),
    upi_intent_link TEXT
);

-- Indexes for orders
CREATE INDEX idx_orders_vendor_id ON orders(vendor_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_order_code ON orders(order_code);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_device_fingerprint_hash ON orders(device_fingerprint_hash);
CREATE INDEX idx_orders_payer_hash ON orders(payer_hash);

-- Webhook events table for idempotency
CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    gateway VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSED', 'REJECTED', 'FLAGGED'))
);

-- Index for webhook events
CREATE INDEX idx_webhook_events_event_id ON webhook_events(event_id);
CREATE INDEX idx_webhook_events_order_id ON webhook_events(order_id);

-- Fraud flags table
CREATE TABLE fraud_flags (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    anomaly_score DECIMAL(10, 4),
    is_suspicious BOOLEAN NOT NULL,
    reasons JSONB,
    model_version VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for fraud flags
CREATE INDEX idx_fraud_flags_order_id ON fraud_flags(order_id);
CREATE INDEX idx_fraud_flags_is_suspicious ON fraud_flags(is_suspicious);

-- OTP sessions table
CREATE TABLE otp_sessions (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(15) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for OTP sessions
CREATE INDEX idx_otp_sessions_phone_number ON otp_sessions(phone_number);
CREATE INDEX idx_otp_sessions_expires_at ON otp_sessions(expires_at);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for vendors table
CREATE TRIGGER update_vendors_updated_at BEFORE UPDATE ON vendors
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
