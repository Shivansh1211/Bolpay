-- Add token field (1 letter + 2-3 digits)
ALTER TABLE orders ADD COLUMN token VARCHAR(5) NOT NULL DEFAULT '';

-- Add payment_session_id field
ALTER TABLE orders ADD COLUMN payment_session_id VARCHAR(100);

-- Add escalate_at timestamp for incident log
ALTER TABLE orders ADD COLUMN escalate_at TIMESTAMP;

-- V1 defines status as VARCHAR plus a CHECK constraint, not a PostgreSQL enum.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'FLAGGED', 'LATE_PAID'));

-- Create index on token for quick lookups
CREATE INDEX idx_orders_token ON orders(token);

-- Create index on escalate_at for incident log queries
CREATE INDEX idx_orders_escalate_at ON orders(escalate_at);
