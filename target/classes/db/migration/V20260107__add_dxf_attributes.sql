ALTER TABLE dxf_analysis
    ADD COLUMN steel_type VARCHAR(64),
    ADD COLUMN vinco_type VARCHAR(64),
    ADD COLUMN vinco_height_mm DOUBLE PRECISION,
    ADD COLUMN serrilha_codes JSONB,
    ADD COLUMN pertinax BOOLEAN DEFAULT FALSE,
    ADD COLUMN destacador VARCHAR(16),
    ADD COLUMN raw_attrs JSONB;

-- checksum: ZgYCMO
