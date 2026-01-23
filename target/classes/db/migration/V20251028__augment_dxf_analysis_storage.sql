ALTER TABLE dxf_analysis
    ADD COLUMN score_stars NUMERIC(3,1),
    ADD COLUMN image_bucket VARCHAR(128),
    ADD COLUMN image_key VARCHAR(512),
    ADD COLUMN image_uri TEXT,
    ADD COLUMN image_checksum VARCHAR(128),
    ADD COLUMN image_size_bytes BIGINT,
    ADD COLUMN image_content_type VARCHAR(64),
    ADD COLUMN image_upload_status VARCHAR(32),
    ADD COLUMN image_upload_message TEXT,
    ADD COLUMN image_uploaded_at TIMESTAMPTZ,
    ADD COLUMN image_etag VARCHAR(128);
