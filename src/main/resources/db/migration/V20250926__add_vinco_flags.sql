-- Marcações automáticas de vinco provenientes da OP
ALTER TABLE op_import
    ADD COLUMN IF NOT EXISTS vai_vinco BOOLEAN;

ALTER TABLE op_import
    ADD COLUMN IF NOT EXISTS manual_lock_vai_vinco BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS vai_vinco BOOLEAN NOT NULL DEFAULT FALSE;
