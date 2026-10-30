package com.agentprobe.run;

import com.agentprobe.evaluation.EvaluationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Prometheus metrics. Names become agentprobe_*_total / agentprobe_*_seconds in Prometheus. */
@Component
public class RunMetrics {

    private final MeterRegistry registry;

    public RunMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void scenarioFinished(ScenarioResult r, ScenarioRunner.Outcome o) {
        Counter.builder("agentprobe.scenarios").description("Scenario executions")
                .tag("result", r.isPassed() ? "pass" : "fail").register(registry).increment();
        if (r.getLatencyMs() > 0) {
            timer("agentprobe.agent.latency", "Latency of calls to the agent under test", r.getLatencyMs());
        }
        if (o.infraFailure()) {
            Counter.builder("agentprobe.infra.failures").description("Agent unreachable, timeout or 5xx")
                    .register(registry).increment();
        }
        if (o.tokensUsed() > 0) {
            Counter.builder("agentprobe.tokens").description("Estimated tokens used (agent + judge + simulator)")
                    .register(registry).increment(o.tokensUsed());
        }
        for (ScenarioRunner.Weighted w : o.evals()) {
            EvaluationResult e = w.result();
            if (e.judgeLatencyMs() != null) {
                timer("agentprobe.judge.latency", "Latency of LLM judge calls", e.judgeLatencyMs());
                Counter.builder("agentprobe.judge.calls").tag("passed", String.valueOf(e.passed()))
                        .register(registry).increment();
            }
            if (e.judgeTokens() != null) {
                Counter.builder("agentprobe.judge.tokens").description("Estimated judge tokens")
                        .register(registry).increment(e.judgeTokens());
            }
        }
    }

    void runFinished(TestRun run) {
        Counter.builder("agentprobe.runs").description("Finished runs").tag("status", run.getStatus())
                .register(registry).increment();
        if (run.getFinishedAt() != null && run.getStartedAt() != null) {
            Timer.builder("agentprobe.run.duration").publishPercentileHistogram().register(registry)
                    .record(Duration.between(run.getStartedAt(), run.getFinishedAt()));
        }
    }

    private void timer(String name, String description, long millis) {
        Timer.builder(name).description(description).publishPercentileHistogram().register(registry)
                .record(Duration.ofMillis(millis));
    }
}