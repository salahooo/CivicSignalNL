CREATE TABLE source_sync_cursor (
    source_name VARCHAR(100) PRIMARY KEY,
    cursor_timestamp TIMESTAMPTZ NULL,
    cursor_record_id VARCHAR(255) NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE source_sync_run (
    run_id UUID PRIMARY KEY,
    source_name VARCHAR(100) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    cursor_before_timestamp TIMESTAMPTZ NULL,
    cursor_before_record_id VARCHAR(255) NULL,
    cursor_after_timestamp TIMESTAMPTZ NULL,
    cursor_after_record_id VARCHAR(255) NULL,
    fetched INTEGER NOT NULL DEFAULT 0,
    published INTEGER NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    failure_category VARCHAR(50) NULL,
    failure_message VARCHAR(500) NULL
);

CREATE INDEX source_sync_run_recent_idx ON source_sync_run (source_name, started_at DESC);
