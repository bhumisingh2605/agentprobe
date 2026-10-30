CREATE TABLE human_label (
                             id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             scenario_result_id UUID        NOT NULL REFERENCES scenario_result (id) ON DELETE CASCADE,
                             user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                             passed             BOOLEAN     NOT NULL,
                             score              INTEGER     NOT NULL CHECK (score BETWEEN 1 AND 5),
                             note               TEXT,
                             created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
                             UNIQUE (scenario_result_id, user_id)
);
CREATE INDEX idx_label_user ON human_label (user_id);

CREATE TABLE api_token (
                           id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                           name         VARCHAR(80) NOT NULL,
                           prefix       VARCHAR(16) NOT NULL,
                           token_hash   VARCHAR(64) NOT NULL UNIQUE,
                           revoked      BOOLEAN     NOT NULL DEFAULT FALSE,
                           created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                           last_used_at TIMESTAMPTZ
);
CREATE INDEX idx_api_token_user ON api_token (user_id);