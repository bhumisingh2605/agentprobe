ALTER TABLE evaluation_result
    ADD COLUMN judge_model          VARCHAR(120),
    ADD COLUMN judge_prompt_version VARCHAR(40),
    ADD COLUMN judge_tokens         INTEGER,
    ADD COLUMN judge_latency_ms     BIGINT;