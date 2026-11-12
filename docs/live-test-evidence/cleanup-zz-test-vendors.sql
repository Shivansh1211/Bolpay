-- Run this preview separately first if you want to inspect matching vendors.
-- SELECT id, business_name, slug FROM vendors WHERE business_name LIKE 'ZZ-TEST%';
-- The transaction below affects only vendors whose business name starts exactly with ZZ-TEST.
BEGIN;

CREATE TEMP TABLE zz_test_vendors ON COMMIT DROP AS
SELECT id, phone_number
FROM vendors
WHERE business_name LIKE 'ZZ-TEST%';

CREATE TEMP TABLE zz_test_orders ON COMMIT DROP AS
SELECT id
FROM orders
WHERE vendor_id IN (SELECT id FROM zz_test_vendors);

-- The editor reports how many matching vendors and orders are selected.
SELECT COUNT(*) AS vendors_to_delete FROM zz_test_vendors;
SELECT COUNT(*) AS orders_to_delete FROM zz_test_orders;

DELETE FROM webhook_events WHERE order_id IN (SELECT id FROM zz_test_orders);
DELETE FROM fraud_flags WHERE order_id IN (SELECT id FROM zz_test_orders);
DELETE FROM incident_log
WHERE vendor_id IN (SELECT id FROM zz_test_vendors)
   OR order_id IN (SELECT id FROM zz_test_orders);
DELETE FROM research_session WHERE vendor_id IN (SELECT id FROM zz_test_vendors);
DELETE FROM scan_sessions WHERE vendor_id IN (SELECT id FROM zz_test_vendors);
DELETE FROM otp_sessions WHERE phone_number IN (SELECT phone_number FROM zz_test_vendors);
DELETE FROM orders WHERE id IN (SELECT id FROM zz_test_orders);
DELETE FROM vendors WHERE id IN (SELECT id FROM zz_test_vendors);

COMMIT;
