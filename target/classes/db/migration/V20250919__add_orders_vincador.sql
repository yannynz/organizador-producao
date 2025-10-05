DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'vincador'
    ) THEN
        ALTER TABLE orders ADD COLUMN vincador VARCHAR(255);
    ELSE
        ALTER TABLE orders ALTER COLUMN vincador TYPE VARCHAR(255);
        ALTER TABLE orders ALTER COLUMN vincador DROP DEFAULT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders' AND column_name = 'data_vinco'
    ) THEN
        ALTER TABLE orders ADD COLUMN data_vinco timestamptz;
    ELSE
        ALTER TABLE orders ALTER COLUMN data_vinco DROP DEFAULT;
        BEGIN
            ALTER TABLE orders
                ALTER COLUMN data_vinco TYPE timestamptz
                USING data_vinco::timestamptz;
        EXCEPTION WHEN others THEN
            ALTER TABLE orders
                ALTER COLUMN data_vinco TYPE timestamptz
                USING NULL;
        END;
    END IF;
END $$;

