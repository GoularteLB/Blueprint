CREATE TABLE category_month_summary (
    job_id       UUID NOT NULL REFERENCES import_job(id),
    month        DATE NOT NULL,
    category     TEXT NOT NULL,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tx_count     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (job_id, month, category)
);
