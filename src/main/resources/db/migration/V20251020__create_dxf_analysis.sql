CREATE TABLE dxf_analysis (
    id BIGSERIAL PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    order_nr VARCHAR(64),
    order_id BIGINT,
    file_name TEXT NOT NULL,
    file_hash VARCHAR(128),
    image_path TEXT,
    image_width INTEGER,
    image_height INTEGER,
    score NUMERIC(8,3),
    score_label VARCHAR(32),
    total_cut_length_mm NUMERIC(18,3),
    curve_count INTEGER,
    intersection_count INTEGER,
    min_radius_mm NUMERIC(10,3),
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    analyzed_at TIMESTAMPTZ NOT NULL,
    metrics_json JSONB,
    explanations_json JSONB,
    raw_payload_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

ALTER TABLE dxf_analysis
    ADD CONSTRAINT fk_dxf_analysis_order
    FOREIGN KEY (order_id) REFERENCES orders (id);

CREATE UNIQUE INDEX ux_dxf_analysis_analysis_id ON dxf_analysis (analysis_id);
CREATE INDEX idx_dxf_analysis_order_nr ON dxf_analysis (order_nr);
CREATE INDEX idx_dxf_analysis_order_id ON dxf_analysis (order_id);
CREATE INDEX idx_dxf_analysis_analyzed_at ON dxf_analysis (analyzed_at);
