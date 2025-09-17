ALTER TABLE op_import
    ADD COLUMN manual_lock_emborrachada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_lock_pertinax BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_lock_poliester BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_lock_papel_calibrado BOOLEAN NOT NULL DEFAULT FALSE;
