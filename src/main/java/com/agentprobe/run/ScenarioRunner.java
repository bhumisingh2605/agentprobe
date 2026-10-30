
package com.agentprobe.run;

import com.agentprobe.adapter.AgentAdapter;
import com.agentprobe.adapter.AgentReply;
import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.api.ApiException;
import com.agentprobe.evaluation.EvaluationResult;
import com.agentprobe.evaluation.Evaluator;
import com.agentprobe.evaluation.EvaluatorRegistry;
import com.agentprobe.evaluation.Params;
import com.agentprobe.evaluation.PiiRedactor;
import com.agentprobe.evaluation.ScenarioContext;
import com.agentprobe.simulator.UserSimulator;
import com.agentprobe.suite.Scenario;
import com.agentprobe.util.Json;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class ScenarioRunner {

    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_MAX_TURNS = 6;
    private static final int HARD_MAX_TURNS = 20;

    public record TurnData(
            int number,
            String role,
            String content,
            String toolCallsJson,
            long latencyMs
    ) {}

    public record Weighted(EvaluationResult result, double weight) {}

    /**
     * infraFailure = agent unreachable / timing out / 5xx,
     * used by the circuit breaker.
     */
    public record Outcome(
            ScenarioResult result,
            List<TurnData> turns,
            List<Weighted> evals,
            boolean infraFailure,
            long tokensUsed
    ) {}

    private static final class Work {
        final List<TurnData> turns = new ArrayList<>();
        final List<Weighted> evals = new ArrayList<>();
        long tokens;
        boolean infra;
    }

    private final AgentAdapter adapter;
    private final UserSimulator simulator;
    private final EvaluatorRegistry registry;
    private final ScenarioResultRepository results;
    private final TurnRecordRepository turnRepo;
    private final EvaluationRecordRepository evalRepo;
    private final long backoffMs;

    private PiiRedactor redactor;

    /** Setter injection keeps the constructor stable for unit tests. */
    @Autowired(required = false)
    public void setRedactor(PiiRedactor redactor) {
        this.redactor = redactor;
    }

    public ScenarioRunner(
            AgentAdapter adapter,
            UserSimulator simulator,
            EvaluatorRegistry registry,
            ScenarioResultRepository results,
            TurnRecordRepository turnRepo,
            EvaluationRecordRepository evalRepo,
            @Value("${agentprobe.runs.retry-backoff-ms:1000}") long backoffMs
    ) {
        this.adapter = adapter;
        this.simulator = simulator;
        this.registry = registry;
        this.results = results;
        this.turnRepo = turnRepo;
        this.evalRepo = evalRepo;
        this.backoffMs = backoffMs;
    }

    public Outcome execute(
            UUID runId,
            Scenario s,
            AgentTarget target,
            int position
    ) {
        ScenarioResult r =
                new ScenarioResult(runId, s.getId(), s.getExternalId(), position);

        Work w = new Work();
        String session = "run-" + runId.toString().substring(0, 8) + "-" + position;

        try {
            if ("single_turn".equals(s.getType())) {
                runSingle(r, s, target, session, w);
            } else if ("multi_turn".equals(s.getType())) {
                runMulti(r, s, target, session, w);
            } else {
                r.fail("Unknown scenario type '" + s.getType() + "'");
            }
        } catch (ApiException e) {
            r.fail(e.getMessage());
            r.setStopReason("agent_error");
            w.infra = e.getStatus() == HttpStatus.BAD_GATEWAY;
        } catch (IllegalStateException e) {
            r.fail(e.getMessage());
            r.setStopReason("simulator_error");
        }

        return new Outcome(r, w.turns, w.evals, w.infra, w.tokens);
    }

    public Outcome failed(
            UUID runId,
            Scenario s,
            int position,
            String reason,
            String stopReason,
            boolean infra
    ) {
        ScenarioResult r =
                new ScenarioResult(runId, s.getId(), s.getExternalId(), position);

        r.fail(reason);
        r.setStopReason(stopReason);

        return new Outcome(
                r,
                new ArrayList<TurnData>(),
                new ArrayList<Weighted>(),
                infra,
                0
        );
    }

    // ------------------------------------------------------------------
    // Save results with optional PII redaction
    // ------------------------------------------------------------------

    public ScenarioResult save(Outcome o) {
        boolean redact = redactor != null && redactor.isEnabled();

        if (redact) {
            o.result().setUserMessage(
                    redactor.apply(o.result().getUserMessage())
            );
            o.result().setAgentAnswer(
                    redactor.apply(o.result().getAgentAnswer())
            );
        }

        ScenarioResult saved = results.save(o.result());

        List<TurnRecord> turnRecords = new ArrayList<TurnRecord>();

        for (TurnData t : o.turns()) {
            String content = redact
                    ? redactor.apply(t.content())
                    : t.content();

            turnRecords.add(
                    new TurnRecord(
                            saved.getId(),
                            t.number(),
                            t.role(),
                            content,
                            t.toolCallsJson(),
                            t.latencyMs()
                    )
            );
        }

        turnRepo.saveAll(turnRecords);

        List<EvaluationRecord> evalRecords =
                new ArrayList<EvaluationRecord>();

        for (Weighted wt : o.evals()) {
            EvaluationResult er = wt.result();

            if (redact) {
                er = new EvaluationResult(
                        er.type(),
                        er.passed(),
                        er.score(),
                        redactor.apply(er.reasoning()),
                        er.judgeModel(),
                        er.judgePromptVersion(),
                        er.judgeTokens(),
                        er.judgeLatencyMs()
                );
            }

            evalRecords.add(
                    new EvaluationRecord(saved.getId(), er, wt.weight())
            );
        }

        evalRepo.saveAll(evalRecords);
        return saved;
    }

    // ------------------------------------------------------------------
    // Single turn
    // ------------------------------------------------------------------

    private void runSingle(
            ScenarioResult r,
            Scenario s,
            AgentTarget target,
            String session,
            Work w
    ) {
        String input = s.getInput();

        r.setUserMessage(input);
        r.setStopReason("single_turn");
        r.setTurnCount(1);
        w.turns.add(new TurnData(1, "customer", input, "[]", 0));

        AgentReply reply = callWithRetry(target, input, session);

        r.setLatencyMs(reply.latencyMs());
        r.setHttpStatus(reply.httpStatus());
        r.setTokens(reply.tokens());
        r.setAgentAnswer(reply.answer());
        r.setToolCalls(Json.write(reply.toolCalls()));

        if (reply.tokens() != null) {
            w.tokens += reply.tokens();
        }

        if (reply.httpStatus() < 200 || reply.httpStatus() >= 300) {
            w.infra = isTransient(reply.httpStatus());
            r.fail("Agent returned HTTP " + reply.httpStatus());
            return;
        }

        if (reply.answer() == null) {
            r.fail(
                    "Could not find the answer in the response. "
                            + "Check the responseMapping answerPath."
            );
            return;
        }

        w.turns.add(
                new TurnData(
                        2,
                        "agent",
                        reply.answer(),
                        Json.write(reply.toolCalls()),
                        reply.latencyMs()
                )
        );

        ScenarioContext ctx = new ScenarioContext(
                input,
                reply.answer(),
                reply.toolCalls(),
                reply.tokens(),
                reply.latencyMs(),
                reply.httpStatus(),
                "",
                1,
                "single_turn"
        );

        evaluate(s, ctx, w);
        score(r, w.evals);
    }

    // ------------------------------------------------------------------
    // Multi turn
    // ------------------------------------------------------------------

    private void runMulti(
            ScenarioResult r,
            Scenario s,
            AgentTarget target,
            String session,
            Work w
    ) {
        int maxTurns = Math.min(
                s.getMaxTurns() == null
                        ? DEFAULT_MAX_TURNS
                        : s.getMaxTurns(),
                HARD_MAX_TURNS
        );

        List<UserSimulator.Line> history = new ArrayList<>();
        List<Object> toolCalls = new ArrayList<>();
        StringBuilder agentText = new StringBuilder();

        String firstMessage = null;
        String lastAnswer = null;

        int repeats = 0;
        int agentTurns = 0;
        int messageNo = 0;
        int tokenSum = 0;

        boolean sawTokens = false;
        long maxLatency = 0;
        String stop = "max_turns";

        try {
            for (int t = 0; t < maxTurns; t++) {
                UserSimulator.Step step =
                        simulator.next(s.getPersona(), s.getGoal(), history);

                w.tokens += step.tokensEstimate();

                if (!"CONTINUE".equals(step.status())) {
                    stop = step.status().toLowerCase(Locale.ROOT);
                    break;
                }

                if (firstMessage == null) {
                    firstMessage = step.message();
                }

                history.add(
                        new UserSimulator.Line("CUSTOMER", step.message())
                );

                w.turns.add(
                        new TurnData(
                                ++messageNo,
                                "customer",
                                step.message(),
                                "[]",
                                0
                        )
                );

                AgentReply reply =
                        callWithRetry(target, step.message(), session);

                if (reply.httpStatus() < 200 || reply.httpStatus() >= 300) {
                    w.infra = isTransient(reply.httpStatus());
                    r.setStopReason("agent_error");
                    r.fail(
                            "Agent returned HTTP " + reply.httpStatus()
                                    + " on turn " + (agentTurns + 1)
                    );
                    return;
                }

                if (reply.answer() == null) {
                    r.setStopReason("agent_error");
                    r.fail(
                            "Could not find the answer in the response on turn "
                                    + (agentTurns + 1)
                                    + ". Check the responseMapping answerPath."
                    );
                    return;
                }

                agentTurns++;

                history.add(
                        new UserSimulator.Line("ASSISTANT", reply.answer())
                );

                w.turns.add(
                        new TurnData(
                                ++messageNo,
                                "agent",
                                reply.answer(),
                                Json.write(reply.toolCalls()),
                                reply.latencyMs()
                        )
                );

                agentText.append(reply.answer()).append('\n');
                toolCalls.addAll(reply.toolCalls());
                maxLatency = Math.max(maxLatency, reply.latencyMs());

                if (reply.tokens() != null) {
                    tokenSum += reply.tokens();
                    sawTokens = true;
                    w.tokens += reply.tokens();
                }

                if (reply.answer().equals(lastAnswer)) {
                    repeats++;
                } else {
                    repeats = 0;
                }

                lastAnswer = reply.answer();

                if (repeats >= 2) {
                    stop = "agent_looping";
                    break;
                }
            }

            r.setStopReason(stop);

            if (agentTurns == 0) {
                r.fail(
                        "No agent turns were executed "
                                + "(the simulated customer ended the conversation immediately)"
                );
                return;
            }

            ScenarioContext ctx = new ScenarioContext(
                    firstMessage,
                    agentText.toString().trim(),
                    toolCalls,
                    sawTokens ? Integer.valueOf(tokenSum) : null,
                    maxLatency,
                    200,
                    transcript(history),
                    agentTurns,
                    stop
            );

            evaluate(s, ctx, w);
            score(r, w.evals);

        } finally {
            r.setUserMessage(firstMessage);
            r.setAgentAnswer(agentText.toString().trim());
            r.setToolCalls(Json.write(toolCalls));
            r.setTurnCount(agentTurns);
            r.setLatencyMs(maxLatency);
            r.setHttpStatus(agentTurns > 0 ? 200 : 0);

            if (sawTokens) {
                r.setTokens(tokenSum);
            }
        }
    }

    private static String transcript(List<UserSimulator.Line> history) {
        StringBuilder sb = new StringBuilder();

        for (UserSimulator.Line l : history) {
            sb.append(
                    "CUSTOMER".equals(l.role())
                            ? "Customer: "
                            : "Agent: "
            ).append(l.text()).append('\n');
        }

        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Agent call with retry
    // ------------------------------------------------------------------

    static boolean isTransient(int status) {
        return status == 429
                || status == 502
                || status == 503
                || status == 504;
    }

    /** Retries connection errors and 429/502/503/504 up to 3 attempts. */
    private AgentReply callWithRetry(
            AgentTarget target,
            String message,
            String session
    ) {
        ApiException lastError = null;
        AgentReply lastReply = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AgentReply reply = adapter.send(target, message, session);

                if (!isTransient(reply.httpStatus())) {
                    return reply;
                }

                lastReply = reply;
                lastError = null;

            } catch (ApiException e) {
                if (e.getStatus() != HttpStatus.BAD_GATEWAY) {
                    throw e;
                }

                lastError = e;
                lastReply = null;
            }

            if (attempt < MAX_ATTEMPTS) {
                pause(backoffMs * attempt);
            }
        }

        if (lastReply != null) {
            return lastReply;
        }

        if (lastError != null) {
            throw lastError;
        }

        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Agent call failed"
        );
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Interrupted while waiting to retry"
            );
        }
    }

    // ------------------------------------------------------------------
    // Evaluators and scoring
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void evaluate(Scenario s, ScenarioContext ctx, Work w) {
        for (Object o : Json.list(s.getEvaluators())) {
            if (!(o instanceof Map)) {
                continue;
            }

            Map<String, Object> cfg = (Map<String, Object>) o;
            String type = String.valueOf(cfg.get("type"));
            double weight = Params.numberOr(cfg, "weight", 1.0);

            EvaluationResult result;
            Evaluator ev = registry.find(type).orElse(null);

            if (ev == null) {
                result = EvaluationResult.fail(
                        type,
                        "Unknown evaluator '" + type + "'"
                );
            } else {
                try {
                    result = ev.evaluate(
                            ctx,
                            new HashMap<String, Object>(cfg)
                    );
                } catch (RuntimeException e) {
                    result = EvaluationResult.fail(
                            type,
                            "Evaluator crashed: " + e.getMessage()
                    );
                }
            }

            if (result.judgeTokens() != null) {
                w.tokens += result.judgeTokens();
            }

            w.evals.add(new Weighted(result, weight));
        }
    }

    /** Scenario passes only if every evaluator passes. */
    private static void score(
            ScenarioResult r,
            List<Weighted> evals
    ) {
        if (evals.isEmpty()) {
            r.setPassed(true);
            r.setScore(1.0);
            return;
        }

        double weightSum = 0;
        double scoreSum = 0;
        boolean allPassed = true;

        for (Weighted wt : evals) {
            weightSum += wt.weight();
            scoreSum += wt.result().score() * wt.weight();

            if (!wt.result().passed()) {
                allPassed = false;
            }
        }

        r.setPassed(allPassed);
        r.setScore(weightSum == 0 ? 0.0 : scoreSum / weightSum);
    }
}
