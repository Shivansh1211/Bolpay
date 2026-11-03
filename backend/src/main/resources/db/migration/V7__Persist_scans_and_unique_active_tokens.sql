CREATE TABLE scan_sessions (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    device_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scan_sessions_vendor_created
    ON scan_sessions (vendor_id, created_at);

CREATE UNIQUE INDEX uq_orders_active_vendor_token
    ON orders (vendor_id, token)
    WHERE status = 'PENDING';
