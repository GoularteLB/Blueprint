CREATE TABLE import_job (
    id               UUID PRIMARY KEY,
    file_name        TEXT NOT NULL,
    file_path        TEXT NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    bytes_read       BIGINT NOT NULL DEFAULT 0,
    status           TEXT NOT NULL,
    processed_lines  BIGINT NOT NULL DEFAULT 0,
    success_lines    BIGINT NOT NULL DEFAULT 0,
    error_lines      BIGINT NOT NULL DEFAULT 0,
    error_sample     JSONB,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ
);

CREATE TABLE transactions (
    id           BIGSERIAL PRIMARY KEY,
    job_id       UUID NOT NULL REFERENCES import_job(id),
    external_id  TEXT NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    category     TEXT NOT NULL,
    description  TEXT,
    amount       NUMERIC(14,2) NOT NULL,
    source       TEXT
);
