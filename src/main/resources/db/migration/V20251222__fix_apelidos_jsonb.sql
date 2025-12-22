-- Corrige o tipo dos campos apelidos para jsonb em clientes e transportadoras.
-- Usa conversão tolerante e só executa se a coluna ainda não estiver em jsonb.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'clientes'
          AND column_name = 'apelidos'
          AND data_type <> 'jsonb'
    ) THEN
        ALTER TABLE clientes
            ALTER COLUMN apelidos DROP DEFAULT,
            ALTER COLUMN apelidos TYPE jsonb USING (
                CASE
                    WHEN apelidos IS NULL OR trim(apelidos) = '' THEN '[]'::jsonb
                    WHEN apelidos ~ '^\s*\[.*\]\s*$' THEN apelidos::jsonb
                    ELSE to_jsonb(regexp_split_to_array(apelidos, '\s*,\s*'))
                END
            ),
            ALTER COLUMN apelidos SET DEFAULT '[]'::jsonb;

        UPDATE clientes
        SET apelidos = '[]'::jsonb
        WHERE apelidos IS NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'transportadoras'
          AND column_name = 'apelidos'
          AND data_type <> 'jsonb'
    ) THEN
        ALTER TABLE transportadoras
            ALTER COLUMN apelidos DROP DEFAULT,
            ALTER COLUMN apelidos TYPE jsonb USING (
                CASE
                    WHEN apelidos IS NULL OR trim(apelidos) = '' THEN '[]'::jsonb
                    WHEN apelidos ~ '^\s*\[.*\]\s*$' THEN apelidos::jsonb
                    ELSE to_jsonb(regexp_split_to_array(apelidos, '\s*,\s*'))
                END
            ),
            ALTER COLUMN apelidos SET DEFAULT '[]'::jsonb;

        UPDATE transportadoras
        SET apelidos = '[]'::jsonb
        WHERE apelidos IS NULL;
    END IF;
END $$;
