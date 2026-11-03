-- Research session table for data collection in research mode
CREATE TABLE research_session (
    id BIGSERIAL PRIMARY KEY,
    vendor_id BIGINT NOT NULL REFERENCES vendors(id),
    label VARCHAR(50) NOT NULL CHECK (label IN ('honest', 'simulated_fake')),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    total_orders INTEGER DEFAULT 0,
    paid_orders INTEGER DEFAULT 0,
    expired_orders INTEGER DEFAULT 0,
    flagged_orders INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for research session
CREATE INDEX idx_research_session_vendor_id ON research_session(vendor_id);
CREATE INDEX idx_research_session_label ON research_session(label);
CREATE INDEX idx_research_session_start_time ON research_session(start_time);
