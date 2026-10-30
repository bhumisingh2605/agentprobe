ALTER TABLE test_run
    ADD COLUMN error        TEXT,
    ADD COLUMN token_budget INTEGER,
    ADD COLUMN concurrency  INTEGER NOT NULL DEFAULT 3;

ALTER TABLE scenario_result
    ADD COLUMN stop_reason VARCHAR(40),
    ADD COLUMN turn_count  INTEGER NOT NULL DEFAULT 1;

CREATE TABLE turn (
                      id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                      scenario_result_id UUID        NOT NULL REFERENCES scenario_result (id) ON DELETE CASCADE,
                      turn_number        INTEGER     NOT NULL,
                      role               VARCHAR(20) NOT NULL,
                      content            TEXT        NOT NULL,
                      tool_calls         JSONB       NOT NULL DEFAULT '[]'::jsonb,
                      latency_ms         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_turn_result ON turn (scenario_result_id, turn_number);