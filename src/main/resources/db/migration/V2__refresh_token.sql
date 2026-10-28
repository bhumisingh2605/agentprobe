CREATE TABLE refresh_token (
                               id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                               token_hash VARCHAR(64) NOT NULL UNIQUE,
                               expires_at TIMESTAMPTZ NOT NULL,
                               revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);