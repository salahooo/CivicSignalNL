CREATE TABLE report_case (
 report_id varchar(200) PRIMARY KEY,
 current_status varchar(20) NOT NULL CHECK (current_status IN ('NEW','TRIAGED','IN_PROGRESS','RESOLVED','CLOSED','REJECTED')),
 version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 resolved_at timestamptz, closed_at timestamptz,
 reopen_count integer NOT NULL DEFAULT 0 CHECK (reopen_count >= 0),
 last_event_id uuid UNIQUE
);
CREATE TABLE report_audit (
 audit_id uuid PRIMARY KEY, report_id varchar(200) NOT NULL REFERENCES report_case(report_id),
 event_id uuid NOT NULL UNIQUE, event_type varchar(40) NOT NULL,
 actor varchar(200) NOT NULL, occurred_at timestamptz NOT NULL,
 aggregate_version bigint NOT NULL,
 metadata jsonb NOT NULL,
 UNIQUE (report_id, aggregate_version)
);
CREATE INDEX report_audit_page ON report_audit(report_id, aggregate_version DESC);
CREATE TABLE report_note (
 note_id uuid PRIMARY KEY, report_id varchar(200) NOT NULL REFERENCES report_case(report_id),
 note_text varchar(2000) NOT NULL CHECK (length(note_text) > 0),
 actor varchar(200) NOT NULL, created_at timestamptz NOT NULL,
 event_id uuid NOT NULL UNIQUE REFERENCES report_audit(event_id)
);
CREATE INDEX report_note_page ON report_note(report_id, created_at, note_id);
CREATE TABLE report_outbox (
 event_id uuid PRIMARY KEY REFERENCES report_audit(event_id),
 report_id varchar(200) NOT NULL REFERENCES report_case(report_id),
 aggregate_version bigint NOT NULL, event_type varchar(40) NOT NULL,
 payload jsonb NOT NULL, created_at timestamptz NOT NULL, published_at timestamptz,
 attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
 last_error varchar(80), next_attempt_at timestamptz NOT NULL,
 UNIQUE (report_id, aggregate_version)
);
CREATE INDEX report_outbox_due ON report_outbox(next_attempt_at, created_at) WHERE published_at IS NULL;
