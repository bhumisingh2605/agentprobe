
package com.agentprobe.run;

import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.api.ApiException;
import com.agentprobe.suite.Scenario;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory run queue.
 *
 * One orchestrator thread per active run; scenarios run on virtual threads,
 * limited per run and globally.
 *
 * Protections:
 * - Per-scenario timeout (watchdog)
 * - Retries in ScenarioRunner
 * - Circuit breaker after consecutive infrastructure failures
 * - Token budget
 * - Cancellation
 * - MDC context for run and scenario logging
 */
@Component
public class RunExecutor implements SmartInitializingSingleton {

    private static final Logger log =
            LoggerFactory.getLogger(RunExecutor.class);

    private static final int BREAKER_THRESHOLD = 3;

    static final class RunControl {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
    }

    record Job(
            TestRun run,
            List<Scenario> scenarios,
            AgentTarget target,
            RunControl control
    ) {
    }

    private static final class Pending {
        final Scenario scenario;
        final int position;
        final AtomicBoolean claim = new AtomicBoolean(false);
        final CompletableFuture<Void> done = new CompletableFuture<>();

        volatile Future<?> future;
        volatile ScheduledFuture<?> watchdog;

        Pending(Scenario scenario, int position) {
            this.scenario = scenario;
            this.position = position;
        }
    }

    private static final class Counters {
        private int passed;
        private int failed;
        private int latencyCount;
        private long latencySum;
        private long agentTokens;

        final AtomicLong budgetUsed = new AtomicLong();
        final AtomicInteger consecutiveInfra = new AtomicInteger();
        final AtomicBoolean breaker = new AtomicBoolean(false);

        synchronized void record(ScenarioResult r) {
            if (r.isPassed()) {
                passed++;
            } else {
                failed++;
            }

            if (r.getLatencyMs() > 0) {
                latencySum += r.getLatencyMs();
                latencyCount++;
            }

            if (r.getTokens() != null) {
                agentTokens += r.getTokens();
            }
        }

        synchronized int passed() {
            return passed;
        }

        synchronized int failed() {
            return failed;
        }

        synchronized int completed() {
            return passed + failed;
        }

        synchronized long avgLatency() {
            return latencyCount == 0 ? 0 : latencySum / latencyCount;
        }

        synchronized long agentTokens() {
            return agentTokens;
        }
    }

    private final ScenarioRunner runner;
    private final TestRunRepository runs;
    private final RunEvents events;
    private final int timeoutSeconds;
    private final Semaphore global;
    private final ThreadPoolExecutor orchestrators;

    private final ExecutorService tasks =
            Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService watchdogs;

    private final Map<UUID, RunControl> controls =
            new ConcurrentHashMap<>();

    private RunMetrics metrics;

    @Autowired(required = false)
    void setMetrics(RunMetrics metrics) {
        this.metrics = metrics;
    }

    public RunExecutor(
            ScenarioRunner runner,
            TestRunRepository runs,
            RunEvents events,
            @Value("${agentprobe.runs.max-concurrent-runs:2}") int maxRuns,
            @Value("${agentprobe.runs.queue-size:20}") int queueSize,
            @Value("${agentprobe.runs.global-concurrency:6}") int globalConcurrency,
            @Value("${agentprobe.runs.scenario-timeout-seconds:300}") int timeoutSeconds
    ) {
        this.runner = runner;
        this.runs = runs;
        this.events = events;
        this.timeoutSeconds = timeoutSeconds;

        this.global = new Semaphore(Math.max(1, globalConcurrency));

        int threads = Math.max(1, maxRuns);

        this.orchestrators = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueSize)),
                daemonFactory("run-orchestrator")
        );

        this.watchdogs =
                Executors.newSingleThreadScheduledExecutor(
                        daemonFactory("run-watchdog")
                );
    }

    private static ThreadFactory daemonFactory(String name) {
        AtomicInteger counter = new AtomicInteger();

        return r -> {
            Thread t = new Thread(
                    r,
                    name + "-" + counter.incrementAndGet()
            );
            t.setDaemon(true);
            return t;
        };
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void submit(
            TestRun run,
            List<Scenario> scenarios,
            AgentTarget target
    ) {
        RunControl control = new RunControl();
        controls.put(run.getId(), control);

        Job job = new Job(run, scenarios, target, control);

        try {
            orchestrators.execute(() -> orchestrate(job));
        } catch (RejectedExecutionException e) {
            controls.remove(run.getId());
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many runs are queued. Try again later."
            );
        }
    }

    public boolean requestCancel(UUID runId) {
        RunControl control = controls.get(runId);

        if (control == null) {
            return false;
        }

        control.cancelled.set(true);
        return true;
    }

    /**
     * After a restart, queued or running runs cannot finish.
     * Mark them failed.
     */
    @Override
    public void afterSingletonsInstantiated() {
        try {
            for (TestRun r :
                    runs.findAllByStatusIn(List.of("QUEUED", "RUNNING"))) {

                r.end(
                        "FAILED",
                        "Interrupted by application restart",
                        r.getPassedCount(),
                        r.getFailedCount(),
                        r.getAvgLatencyMs(),
                        r.getTotalTokens()
                );

                runs.save(r);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Could not clean up interrupted runs: {}",
                    e.getMessage()
            );
        }
    }

    @PreDestroy
    void shutdown() {
        orchestrators.shutdownNow();
        tasks.shutdownNow();
        watchdogs.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Orchestration
    // ------------------------------------------------------------------

    void orchestrate(Job job) {
        TestRun run = job.run();
        Counters c = new Counters();
        List<Pending> pending = new ArrayList<>();

        String status = "COMPLETED";

        try {
            if (job.control().cancelled.get()) {
                finish(
                        job,
                        c,
                        "CANCELLED",
                        "Cancelled before it started"
                );
                return;
            }

            synchronized (run) {
                run.markRunning();
                runs.save(run);
            }

            events.publish(
                    run.getId(),
                    "progress",
                    snapshot(job, c, "RUNNING")
            );

            Semaphore perRun =
                    new Semaphore(Math.max(1, run.getConcurrency()));

            int position = 0;

            for (Scenario s : job.scenarios()) {
                String stop = acquire(perRun, job, c);

                if (stop != null) {
                    status = stop;
                    break;
                }

                Pending p = new Pending(s, position++);
                pending.add(p);

                dispatch(job, p, perRun, c);
            }

            if ("CANCELLED".equals(status)) {
                for (Pending p : pending) {
                    cancelNow(p);
                }
            } else {
                for (Pending p : pending) {
                    try {
                        p.done.get();
                    } catch (ExecutionException ignored) {
                        // The done future is normally completed without errors.
                    }
                }
            }

            finish(job, c, status, errorFor(status, job, c));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            for (Pending p : pending) {
                cancelNow(p);
            }

            finish(job, c, "FAILED", "Interrupted");

        } catch (RuntimeException e) {
            log.error("Run {} crashed", run.getId(), e);

            for (Pending p : pending) {
                cancelNow(p);
            }

            finish(
                    job,
                    c,
                    "FAILED",
                    "Run crashed: " + e.getMessage()
            );
        }
    }

    /**
     * Waits for a per-run and a global slot.
     * Returns a final status if the run must stop; otherwise returns null.
     */
    private String acquire(
            Semaphore perRun,
            Job job,
            Counters c
    ) throws InterruptedException {

        while (!perRun.tryAcquire(300, TimeUnit.MILLISECONDS)) {
            String stop = shouldStop(job, c);

            if (stop != null) {
                return stop;
            }
        }

        while (!global.tryAcquire(300, TimeUnit.MILLISECONDS)) {
            String stop = shouldStop(job, c);

            if (stop != null) {
                perRun.release();
                return stop;
            }
        }

        String stop = shouldStop(job, c);

        if (stop != null) {
            global.release();
            perRun.release();
            return stop;
        }

        return null;
    }

    private String shouldStop(Job job, Counters c) {
        if (job.control().cancelled.get()) {
            return "CANCELLED";
        }

        if (c.breaker.get()) {
            return "FAILED";
        }

        Integer budget = job.run().getTokenBudget();

        if (budget != null && c.budgetUsed.get() >= budget) {
            return "BUDGET_EXCEEDED";
        }

        return null;
    }

    private String errorFor(
            String status,
            Job job,
            Counters c
    ) {
        if ("FAILED".equals(status)) {
            return "Agent appears to be down: "
                    + BREAKER_THRESHOLD
                    + " consecutive scenarios failed with connection errors, "
                    + "timeouts or 5xx responses. Remaining scenarios were not run.";
        }

        if ("BUDGET_EXCEEDED".equals(status)) {
            return "Token budget of "
                    + job.run().getTokenBudget()
                    + " reached (used about "
                    + c.budgetUsed.get()
                    + "). Remaining scenarios were not run.";
        }

        if ("CANCELLED".equals(status)) {
            return "Cancelled by user";
        }

        return null;
    }

    private void dispatch(
            Job job,
            Pending p,
            Semaphore perRun,
            Counters c
    ) {
        UUID runId = job.run().getId();

        p.future = tasks.submit(() -> {
            MDC.put("runId", runId.toString().substring(0, 8));
            MDC.put("scenarioId", p.scenario.getExternalId());

            try {
                ScenarioRunner.Outcome o = runner.execute(
                        runId,
                        p.scenario,
                        job.target(),
                        p.position
                );

                finishTask(job, p, o, c);

            } catch (Throwable t) {
                finishTask(
                        job,
                        p,
                        runner.failed(
                                runId,
                                p.scenario,
                                p.position,
                                "Scenario crashed: " + t.getMessage(),
                                "crashed",
                                false
                        ),
                        c
                );

            } finally {
                MDC.clear();
                perRun.release();
                global.release();
            }
        });

        p.watchdog = watchdogs.schedule(
                () -> onTimeout(job, p, c),
                timeoutSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Normal completion. Does nothing if the watchdog or cancellation
     * has already claimed this scenario.
     */
    private void finishTask(
            Job job,
            Pending p,
            ScenarioRunner.Outcome o,
            Counters c
    ) {
        if (!p.claim.compareAndSet(false, true)) {
            return;
        }

        ScheduledFuture<?> wd = p.watchdog;

        if (wd != null) {
            wd.cancel(false);
        }

        complete(job, p, o, c);
    }

    private void onTimeout(
            Job job,
            Pending p,
            Counters c
    ) {
        if (!p.claim.compareAndSet(false, true)) {
            return;
        }

        Future<?> f = p.future;

        if (f != null) {
            f.cancel(true);
        }

        ScenarioRunner.Outcome o = runner.failed(
                job.run().getId(),
                p.scenario,
                p.position,
                "Scenario timed out after " + timeoutSeconds + " s",
                "timeout",
                true
        );

        complete(job, p, o, c);
    }

    private void cancelNow(Pending p) {
        if (p.claim.compareAndSet(false, true)) {
            Future<?> f = p.future;

            if (f != null) {
                f.cancel(true);
            }

            ScheduledFuture<?> wd = p.watchdog;

            if (wd != null) {
                wd.cancel(false);
            }

            p.done.complete(null);
        }
    }

    private void complete(
            Job job,
            Pending p,
            ScenarioRunner.Outcome o,
            Counters c
    ) {
        try {
            o.result().setTrial(trialOf(job, p));

            ScenarioResult saved = runner.save(o);
            c.record(saved);

            if (metrics != null) {
                metrics.scenarioFinished(saved, o);
            }

            log.info(
                    "run={} scenario={} trial={} passed={} stop={} latencyMs={}",
                    job.run().getId().toString().substring(0, 8),
                    saved.getExternalId(),
                    saved.getTrial(),
                    saved.isPassed(),
                    saved.getStopReason(),
                    saved.getLatencyMs()
            );

            c.budgetUsed.addAndGet(o.tokensUsed());

            if (o.infraFailure()) {
                if (c.consecutiveInfra.incrementAndGet()
                        >= BREAKER_THRESHOLD) {
                    c.breaker.set(true);
                }
            } else {
                c.consecutiveInfra.set(0);
            }

            TestRun run = job.run();

            synchronized (run) {
                run.progress(
                        c.passed(),
                        c.failed(),
                        c.avgLatency(),
                        c.agentTokens()
                );

                runs.save(run);
            }

            Map<String, Object> ev = snapshot(job, c, "RUNNING");
            ev.put("scenarioId", saved.getExternalId());
            ev.put("scenarioPassed", saved.isPassed());
            ev.put("scenarioScore", saved.getScore());
            ev.put("stopReason", saved.getStopReason());

            events.publish(run.getId(), "scenario", ev);

        } catch (RuntimeException e) {
            log.warn(
                    "Could not record result for scenario {}: {}",
                    p.scenario.getExternalId(),
                    e.getMessage()
            );
        } finally {
            p.done.complete(null);
        }
    }

    private void finish(
            Job job,
            Counters c,
            String status,
            String error
    ) {
        TestRun run = job.run();

        try {
            synchronized (run) {
                run.end(
                        status,
                        error,
                        c.passed(),
                        c.failed(),
                        c.avgLatency(),
                        c.agentTokens()
                );

                runs.save(run);
            }

            if (metrics != null) {
                metrics.runFinished(run);
            }

            log.info(
                    "run={} finished status={} passed={} failed={}",
                    run.getId().toString().substring(0, 8),
                    status,
                    c.passed(),
                    c.failed()
            );

            Map<String, Object> snap = snapshot(job, c, status);
            snap.put("error", error);

            events.publish(run.getId(), "done", snap);

        } finally {
            events.complete(run.getId());
            controls.remove(run.getId());
        }
    }

    private Map<String, Object> snapshot(
            Job job,
            Counters c,
            String status
    ) {
        Map<String, Object> m = new LinkedHashMap<>();

        m.put(
                "runId",
                job.run().getId() == null
                        ? null
                        : job.run().getId().toString()
        );

        m.put("status", status);
        m.put("total", job.scenarios().size());
        m.put("completed", c.completed());
        m.put("passed", c.passed());
        m.put("failed", c.failed());
        m.put("tokensUsedEstimate", c.budgetUsed.get());

        return m;
    }

    /**
     * Scenarios are queued trial by trial:
     * [all scenarios], [all scenarios again], ...
     */
    private static int trialOf(Job job, Pending p) {
        TestRun run = job.run();

        int trials = Math.max(1, run.getTrials());
        int perTrial = Math.max(
                1,
                run.getTotalScenarios() / trials
        );

        return p.position / perTrial + 1;
    }
}
