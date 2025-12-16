CREATE TABLE order_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    user_id BIGINT REFERENCES users(id),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    field_name VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT
);

CREATE INDEX idx_order_history_order_id ON order_history(order_id);
