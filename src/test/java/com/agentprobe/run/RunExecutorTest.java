package com.agentprobe.run;

import com.agentprobe.suite.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunExecutorTest {

    private final ScenarioRunner runner = mock(ScenarioRunner.class);
    private final TestRunRepository runs = mock(TestRunRepository.class);
    private final RunEvents events = mock(RunEvents.class);
    private final RunExecutor executor = new RunExecutor(runner, runs, events, 1, 5, 4, 30);

    @BeforeEach
    void setUp() {
        when(runs.save(any(TestRun.class))).thenAnswer(i -> i.getArgument(0));
        when(runner.save(any(ScenarioRunner.Outcome.class)))
                .thenAnswer(i -> ((ScenarioRunner.Outcome) i.getArgument(0)).result());
    }

    private TestRun newRun(int total, int concurrency, Integer budget) {
        TestRun run = new TestRun(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), total, concurrency, budget);
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        return run;
    }

    private List<Scenario> scenarios(int n) {
        List<Scenario> list = new ArrayList<Scenario>();
        for (int i = 0; i < n; i++) {
            list.add(new Scenario(UUID.randomUUID(), "s" + i, "single_turn"));
        }
        return list;
    }

    private ScenarioRunner.Outcome outcome(boolean passed, boolean infra, long tokens) {
        ScenarioResult r = new ScenarioResult(UUID.randomUUID(), null, "s", 0);
        r.setPassed(passed);
        if (!passed) {
            r.fail("failed");
        }
        return new ScenarioRunner.Outcome(r, List.of(), List.of(), infra, tokens);
    }

    @Test
    void allScenariosPassAndRunCompletes() {
        TestRun run = newRun(4, 2, null);
        when(runner.execute(any(), any(), any(), anyInt())).thenReturn(outcome(true, false, 10));

        executor.orchestrate(new RunExecutor.Job(run, scenarios(4), null, new RunExecutor.RunControl()));

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getPassedCount()).isEqualTo(4);
        assertThat(run.getFailedCount()).isEqualTo(0);
        assertThat(run.getFinishedAt()).isNotNull();
        verify(events).complete(run.getId());
    }

    @Test
    void failingScenariosAreCountedButRunStillCompletes() {
        TestRun run = newRun(3, 1, null);
        when(runner.execute(any(), any(), any(), anyInt()))
                .thenReturn(outcome(true, false, 1), outcome(false, false, 1), outcome(true, false, 1));

        executor.orchestrate(new RunExecutor.Job(run, scenarios(3), null, new RunExecutor.RunControl()));

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getPassedCount()).isEqualTo(2);
        assertThat(run.getFailedCount()).isEqualTo(1);
    }

    @Test
    void circuitBreakerStopsTheRunAfterThreeConsecutiveInfrastructureFailures() {
        TestRun run = newRun(6, 1, null);
        when(runner.execute(any(), any(), any(), anyInt())).thenReturn(outcome(false, true, 0));

        executor.orchestrate(new RunExecutor.Job(run, scenarios(6), null, new RunExecutor.RunControl()));

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getError()).contains("consecutive");
        verify(runner, times(3)).execute(any(), any(), any(), anyInt());
    }

    @Test
    void tokenBudgetStopsTheRun() {
        TestRun run = newRun(5, 1, 100);
        when(runner.execute(any(), any(), any(), anyInt())).thenReturn(outcome(true, false, 60));

        executor.orchestrate(new RunExecutor.Job(run, scenarios(5), null, new RunExecutor.RunControl()));

        assertThat(run.getStatus()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(run.getError()).contains("Token budget");
        verify(runner, times(2)).execute(any(), any(), any(), anyInt());
    }

    @Test
    void runCancelledBeforeItStartsNeverCallsTheAgent() {
        TestRun run = newRun(3, 1, null);
        RunExecutor.RunControl control = new RunExecutor.RunControl();
        control.cancelled.set(true);

        executor.orchestrate(new RunExecutor.Job(run, scenarios(3), null, control));

        assertThat(run.getStatus()).isEqualTo("CANCELLED");
        verify(runner, never()).execute(any(), any(), any(), anyInt());
    }
}