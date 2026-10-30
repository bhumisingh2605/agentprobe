ALTER TABLE test_run        ADD COLUMN trials INTEGER NOT NULL DEFAULT 1;
ALTER TABLE scenario_result ADD COLUMN trial  INTEGER NOT NULL DEFAULT 1;

CREATE TABLE baseline (
                          id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          suite_id   UUID        NOT NULL REFERENCES test_suite (id) ON DELETE CASCADE,
                          agent_id   UUID        NOT NULL REFERENCES agent (id) ON DELETE CASCADE,
                          run_id     UUID        NOT NULL REFERENCES test_run (id) ON DELETE CASCADE,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                          UNIQUE (suite_id, agent_id)
);

CREATE TABLE suite_threshold (
                                 suite_id                 UUID PRIMARY KEY REFERENCES test_suite (id) ON DELETE CASCADE,
                                 max_pass_rate_drop       DOUBLE PRECISION NOT NULL,
                                 scenario_pass_rate_drop  DOUBLE PRECISION NOT NULL,
                                 score_drop               DOUBLE PRECISION NOT NULL,
                                 latency_increase         DOUBLE PRECISION NOT NULL,
                                 token_increase           DOUBLE PRECISION NOT NULL
);