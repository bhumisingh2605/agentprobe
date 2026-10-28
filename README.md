# AgentProbe

Automated testing and evaluation platform for AI agents.

## Problem

Teams ship chatbots and agents but change prompts, models and tools without knowing whether quality got worse. Manual testing is slow, and LLM outputs are non-deterministic, so a single test run can't be trusted.

## What AgentProbe does

Register an agent endpoint, define single- or multi-turn scenarios, and run them with deterministic, tool-call, semantic and LLM-as-judge evaluators. It tracks regressions across versions and blocks pull requests when quality drops.

Three questions it answers:

1. Did the agent do the right thing? (correctness, tool use, safety)
2. Did anything get worse than the last version? (regression)
3. What did it cost in latency and tokens?

## Success criteria

- Live demo link and demo video
- GitHub Action that blocks a pull request when agent quality drops
- LLM judge calibrated on 50 hand-labeled transcripts (agreement %, Cohen's kappa)
- 4 ShopBot versions compared inside AgentProbe
- 3 deliberate regressions injected, with the catch rate reported honestly

## Tech stack

Spring Boot, Spring AI, PostgreSQL + PGVector, Redis, RabbitMQ, React, Docker, GitHub Actions

## Quick start

Requirements: JDK 21, Docker Desktop, Ollama.
```bash
# 1. Start infrastructure (Postgres + PGVector, Redis, RabbitMQ)
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run

# 3. Check it is up
curl http://localhost:8081/actuator/health

```

Expected response: `{"status":"UP"}`

Postgres is exposed on port 5433 and the app on 8081 to avoid clashing with locally installed services.

## Status

Phase 0 complete (infrastructure and health endpoint). Week 1 in progress.
