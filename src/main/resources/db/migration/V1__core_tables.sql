-- V1: core tables for users, projects, agents and agent versions

CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE project (
                         id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                         name        VARCHAR(120) NOT NULL,
                         description TEXT,
                         created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         UNIQUE (owner_id, name)
);

CREATE INDEX idx_project_owner ON project (owner_id);

CREATE TABLE agent (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       project_id  UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
                       name        VARCHAR(120) NOT NULL,
                       description TEXT,
                       created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       UNIQUE (project_id, name)
);

CREATE INDEX idx_agent_project ON agent (project_id);

CREATE TABLE agent_version (
                               id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               agent_id              UUID          NOT NULL REFERENCES agent (id) ON DELETE CASCADE,
                               version_label         VARCHAR(60)   NOT NULL,
                               endpoint_url          VARCHAR(2048) NOT NULL,
                               auth_header_name      VARCHAR(120),
                               auth_header_value_enc TEXT,
                               request_template      JSONB         NOT NULL DEFAULT '{}'::jsonb,
                               response_mapping      JSONB         NOT NULL DEFAULT '{}'::jsonb,
                               model                 VARCHAR(120),
                               prompt_hash           VARCHAR(128),
                               labels                JSONB         NOT NULL DEFAULT '{}'::jsonb,
                               created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
                               UNIQUE (agent_id, version_label)
);

CREATE INDEX idx_agent_version_agent ON agent_version (agent_id);