-- Converte os campos de apelidos para jsonb de forma tolerante.
-- Preserva valores já em JSON, converte listas separadas por vírgula para array e normaliza vazios para [].

ALTER TABLE clientes
    ALTER COLUMN apelidos DROP DEFAULT,
    ALTER COLUMN apelidos TYPE jsonb USING (
        CASE
            WHEN apelidos IS NULL OR trim(apelidos) = '' THEN '[]'::jsonb
            WHEN apelidos ~ '^\s*\[.*\]\s*$' THEN apelidos::jsonb
            ELSE to_jsonb(regexp_split_to_array(apelidos, '\s*,\s*'))
        END
    );

ALTER TABLE clientes
    ALTER COLUMN apelidos SET DEFAULT '[]'::jsonb;

UPDATE clientes
SET apelidos = '[]'::jsonb
WHERE apelidos IS NULL;

ALTER TABLE transportadoras
    ALTER COLUMN apelidos DROP DEFAULT,
    ALTER COLUMN apelidos TYPE jsonb USING (
        CASE
            WHEN apelidos IS NULL OR trim(apelidos) = '' THEN '[]'::jsonb
            WHEN apelidos ~ '^\s*\[.*\]\s*$' THEN apelidos::jsonb
            ELSE to_jsonb(regexp_split_to_array(apelidos, '\s*,\s*'))
        END
    );

ALTER TABLE transportadoras
    ALTER COLUMN apelidos SET DEFAULT '[]'::jsonb;

UPDATE transportadoras
SET apelidos = '[]'::jsonb
WHERE apelidos IS NULL;
