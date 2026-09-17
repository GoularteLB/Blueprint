CREATE INDEX idx_tx_occurred_id
    ON transactions (occurred_at DESC, id DESC);

CREATE INDEX idx_tx_category_occurred_id
    ON transactions (category, occurred_at DESC, id DESC)
    INCLUDE (amount);

CREATE INDEX idx_tx_job_occurred_id
    ON transactions (job_id, occurred_at DESC, id DESC);
