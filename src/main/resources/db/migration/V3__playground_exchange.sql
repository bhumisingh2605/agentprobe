CREATE TABLE playground_exchange (
                                     id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     agent_version_id UUID        NOT NULL REFERENCES agent_version (id) ON DELETE CASCADE,
                                     session_id       VARCHAR(64) NOT NULL,
                                     user_message     TEXT        NOT NULL,
                                     agent_answer     TEXT,
                                     tool_calls       JSONB       NOT NULL DEFAULT '[]'::jsonb,
                                     tokens           INTEGER,
                                     latency_ms       BIGINT      NOT NULL,
                                     http_status      INTEGER     NOT NULL,
                                     created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_playground_version ON playground_exchange (agent_version_id, created_at DESC);