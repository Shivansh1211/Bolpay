-- Fictional demo data only. Idempotent by phone, slug and sample order code.
INSERT INTO vendors (phone_number, upi_id, business_name, slug, description, is_active)
VALUES ('0000000000', 'demo.vendor@examplebank', 'Demo Chai Cart', 'demo-chai-cart',
        'Fictional BolPay seed vendor', TRUE)
ON CONFLICT (phone_number) DO UPDATE SET
    upi_id = EXCLUDED.upi_id,
    business_name = EXCLUDED.business_name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    is_active = TRUE;

INSERT INTO orders (vendor_id, status, amount, order_code, token, expires_at)
SELECT v.id, sample.status, sample.amount, sample.order_code, sample.token, sample.expires_at
FROM vendors v
CROSS JOIN (VALUES
    ('PENDING', 35.00::numeric, 'DEMO0001', 'D21', now() + interval '10 minutes'),
    ('PAID',    50.00::numeric, 'DEMO0002', 'D22', now() + interval '10 minutes')
) AS sample(status, amount, order_code, token, expires_at)
WHERE v.slug = 'demo-chai-cart'
ON CONFLICT (order_code) DO UPDATE SET
    vendor_id = EXCLUDED.vendor_id,
    status = EXCLUDED.status,
    amount = EXCLUDED.amount,
    token = EXCLUDED.token,
    expires_at = EXCLUDED.expires_at;
