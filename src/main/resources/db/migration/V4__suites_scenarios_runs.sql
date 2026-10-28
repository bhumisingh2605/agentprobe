CREATE TABLE test_suite (
                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            project_id  UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
                            name        VARCHAR(120) NOT NULL,
                            description TEXT,
                            created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                            UNIQUE (project_id, name)
);
CREATE INDEX idx_suite_project ON test_suite (project_id);

CREATE TABLE scenario (
                          id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          suite_id    UUID         NOT NULL REFERENCES test_suite (id) ON DELETE CASCADE,
                          external_id VARCHAR(120) NOT NULL,
                          type        VARCHAR(20)  NOT NULL,
                          input       TEXT,
                          persona     TEXT,
                          goal        TEXT,
                          max_turns   INTEGER,
                          tags        JSONB        NOT NULL DEFAULT '[]'::jsonb,
                          evaluators  JSONB        NOT NULL DEFAULT '[]'::jsonb,
                          created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          UNIQUE (suite_id, external_id)
);
CREATE INDEX idx_scenario_suite ON scenario (suite_id);

CREATE TABLE test_run (
                          id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          project_id       UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
                          suite_id         UUID        NOT NULL REFERENCES test_suite (id) ON DELETE CASCADE,
                          agent_version_id UUID        NOT NULL REFERENCES agent_version (id) ON DELETE CASCADE,
                          status           VARCHAR(20) NOT NULL,
                          total_scenarios  INTEGER     NOT NULL DEFAULT 0,
                          passed_count     INTEGER     NOT NULL DEFAULT 0,
                          failed_count     INTEGER     NOT NULL DEFAULT 0,
                          avg_latency_ms   BIGINT      NOT NULL DEFAULT 0,
                          total_tokens     BIGINT      NOT NULL DEFAULT 0,
                          started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                          finished_at      TIMESTAMPTZ,
                          created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_run_suite ON test_run (suite_id, created_at DESC);

CREATE TABLE scenario_result (
                                 id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 run_id       UUID             NOT NULL REFERENCES test_run (id) ON DELETE CASCADE,
                                 scenario_id  UUID             NOT NULL REFERENCES scenario (id) ON DELETE CASCADE,
                                 external_id  VARCHAR(120)     NOT NULL,
                                 position     INTEGER          NOT NULL,
                                 passed       BOOLEAN          NOT NULL DEFAULT FALSE,
                                 score        DOUBLE PRECISION NOT NULL DEFAULT 0,
                                 latency_ms   BIGINT           NOT NULL DEFAULT 0,
                                 tokens       INTEGER,
                                 http_status  INTEGER          NOT NULL DEFAULT 0,
                                 user_message TEXT,
                                 agent_answer TEXT,
                                 tool_calls   JSONB            NOT NULL DEFAULT '[]'::jsonb,
                                 error        TEXT,
                                 created_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_result_run ON scenario_result (run_id, position);

CREATE TABLE evaluation_result (
                                   id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   scenario_result_id UUID             NOT NULL REFERENCES scenario_result (id) ON DELETE CASCADE,
                                   evaluator_type     VARCHAR(60)      NOT NULL,
                                   passed             BOOLEAN          NOT NULL,
                                   score              DOUBLE PRECISION NOT NULL,
                                   weight             DOUBLE PRECISION NOT NULL,
                                   reasoning          TEXT
);
CREATE INDEX idx_eval_result ON evaluation_result (scenario_result_id);