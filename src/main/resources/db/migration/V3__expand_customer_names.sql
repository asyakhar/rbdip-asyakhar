ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

UPDATE customers
SET first_name = split_part(btrim(full_name), ' ', 1),
    last_name = CASE
        WHEN position(' ' IN btrim(full_name)) = 0 THEN ''
        ELSE substring(btrim(full_name) FROM position(' ' IN btrim(full_name)) + 1)
    END;

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;

CREATE FUNCTION sync_customer_name_versions() RETURNS trigger AS $$
BEGIN
    IF NEW.first_name IS NULL OR NEW.last_name IS NULL THEN
        IF NEW.full_name IS NULL OR btrim(NEW.full_name) = '' THEN
            RAISE EXCEPTION 'customer name is required';
        END IF;

        NEW.first_name := split_part(btrim(NEW.full_name), ' ', 1);
        NEW.last_name := CASE
            WHEN position(' ' IN btrim(NEW.full_name)) = 0 THEN ''
            ELSE substring(btrim(NEW.full_name) FROM position(' ' IN btrim(NEW.full_name)) + 1)
        END;
    ELSE
        NEW.full_name := concat_ws(
            ' ', NULLIF(btrim(NEW.first_name), ''), NULLIF(btrim(NEW.last_name), '')
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_customer_name_versions
    BEFORE INSERT OR UPDATE OF first_name, last_name, full_name ON customers
    FOR EACH ROW EXECUTE FUNCTION sync_customer_name_versions();

CREATE FUNCTION sync_order_customer_full_name() RETURNS trigger AS $$
BEGIN
    IF NEW.customer_id IS NOT NULL THEN
        SELECT full_name INTO NEW.customer_full_name
        FROM customers
        WHERE id = NEW.customer_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_order_customer_full_name
    BEFORE INSERT OR UPDATE OF customer_id ON orders
    FOR EACH ROW EXECUTE FUNCTION sync_order_customer_full_name();
