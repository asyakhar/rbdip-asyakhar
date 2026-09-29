DROP TRIGGER trg_sync_customer_name_versions ON customers;
DROP FUNCTION sync_customer_name_versions();
ALTER TABLE customers DROP COLUMN full_name;

DROP TRIGGER trg_sync_order_customer_full_name ON orders;
DROP FUNCTION sync_order_customer_full_name();
ALTER TABLE orders DROP COLUMN customer_full_name;
