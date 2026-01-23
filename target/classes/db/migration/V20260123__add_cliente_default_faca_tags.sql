ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS default_emborrachada BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS default_destacador VARCHAR(10);

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS default_pertinax BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS default_poliester BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS default_papel_calibrado BOOLEAN NOT NULL DEFAULT false;

UPDATE clientes
SET
    default_emborrachada = false,
    default_pertinax = false,
    default_poliester = false,
    default_papel_calibrado = false
WHERE
    default_emborrachada IS NULL
    OR default_pertinax IS NULL
    OR default_poliester IS NULL
    OR default_papel_calibrado IS NULL;
