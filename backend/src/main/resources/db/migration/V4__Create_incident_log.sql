-- Incident log table for unresolved expired orders, late payments, and flags
CREATE TABLE incident_log (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    incident_type VARCHAR(50) NOT NULL CHECK (incident_type IN ('TIMEOUT', 'LATE_PAYMENT', 'PATTERN_FLAG', 'HIGH_VALUE_PENDING', 'BULK_ORDER', 'RECONCILIATION_FAILURE')),
    description TEXT,
    resolved BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for incident log
CREATE INDEX idx_incident_log_vendor_id ON incident_log(vendor_id);
CREATE INDEX idx_incident_log_order_id ON incident_log(order_id);
CREATE INDEX idx_incident_log_resolved ON incident_log(resolved);
CREATE INDEX idx_incident_log_created_at ON incident_log(created_at);
CREATE INDEX idx_incident_log_incident_type ON incident_log(incident_type);
