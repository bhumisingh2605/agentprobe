# AgentProbe

Automated testing and evaluation platform for AI agents. Register an agent endpoint, write test scenarios
(single turn, or multi turn with a simulated customer), run them with repeated trials, score them with
deterministic checks, tool-call checks, safety checks and an LLM judge, and block pull requests when quality drops.

![Dashboard](docs/dashboard.png)

## Why
Teams change prompts and models and cannot tell whether the agent got better or worse. AgentProbe answers:
did the agent do the right thing, did anything get worse than the last version, and what did it cost.

## Features
- **Agent registry**: versions with labels, encrypted auth headers, request template and JSONPath response mapping
- **Scenarios as code**: YAML import and export, tags, 15+ evaluator types
- **Evaluators**: contains, regex, latency, token limit, tool_call, tool_call_forbidden, goal_reached,
  llm_judge (anchored rubrics, majority vote), similarity (embeddings), pii_leak, system_prompt_leak, refusal
- **Multi-turn**: LLM-simulated customer with persona, hidden facts and stop conditions
- **Run engine**: async queue, parallel scenarios, timeouts, retries, circuit breaker, token budget, cancel, SSE progress
- **Regression detection**: baselines, N trials per scenario, noise-aware thresholds, PASS / WARN / FAIL with reasons
- **Judge calibration**: blind human labeling, percent agreement and Cohen's kappa against the judge
- **CI**: CLI and GitHub Action that comments a report on the pull request and fails the check on regression
- **Security**: SSRF guard, AES-GCM secrets, per-user isolation, revocable API tokens, Redis rate limits,
  judge prompt-injection defense, optional PII redaction of stored transcripts
- **Observability**: Micrometer, Prometheus, Grafana dashboard, structured logs with run and scenario ids

## Architecture
```
React dashboard / CLI / GitHub Action
            |
     Spring Boot API (JWT, API tokens, rate limit)
            |
      Run queue ---> orchestrator ---> scenario workers (virtual threads)
                                          |-> REST adapter -> agent under test
                                          |-> user simulator (Spring AI)
                                          '-> evaluator pipeline (deterministic, tool-call, safety, judge, embeddings)
            |
   PostgreSQL | Redis | Micrometer -> Prometheus -> Grafana
```

## Quick start (local)
```bash
docker compose up -d                 # postgres+pgvector, redis, rabbitmq
ollama pull llama3.2 && ollama pull nomic-embed-text
mvn spring-boot:run                  # API on :8081, Swagger at /swagger-ui.html
cd ui && npm install && npm run dev  # dashboard on :5173
```
ShopBot (the sample agent under test) lives in its own repo and runs on :8082.

## Configuration
| Variable | Purpose |
|---|---|
| `JWT_SECRET` | signing key for access tokens (32+ characters) |
| `ENCRYPTION_KEY` | base64 32-byte key for stored agent secrets (`openssl rand -base64 32`) |
| `ALLOW_PRIVATE_TARGETS` | `true` only for local development |
| `AGENTPROBE_SECURITY_ALLOWED_HOSTS` | host names allowed to resolve to private addresses (for example `shopbot`) |
| `AGENTPROBE_PRIVACY_REDACT_TRANSCRIPTS` | redact emails, phones and cards in stored transcripts |
| `SPRING_AI_OLLAMA_BASE_URL` | model server |

## CI gate
```bash
java -jar agentprobe-cli.jar run --project <id> --suite shopbot-regression --agent <id> \
  --agent-version pr-candidate --trials 3 --fail-on FAIL
```
Exit code 0 passes, 1 means a regression was detected. See `.github/workflows/agentprobe-gate.yml` in the ShopBot repo.

## Evaluation results
(Fill in from your study: judge agreement with N hand-labeled transcripts, regressions caught out of N, false alarms.)

## Limitations and future work
- The run queue is in memory. A restart marks active runs as failed. A durable queue (RabbitMQ) is future work.
- SSRF checks resolve DNS before connecting. Pinning the resolved IP would close the DNS rebinding gap.
- Token counts for the judge and simulator are estimates (characters / 4).
- Cost in money is not tracked for local models.