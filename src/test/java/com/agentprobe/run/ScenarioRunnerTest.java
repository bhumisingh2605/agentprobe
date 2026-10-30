package com.agentprobe.run;

import com.agentprobe.adapter.AgentAdapter;
import com.agentprobe.adapter.AgentReply;
import com.agentprobe.api.ApiException;
import com.agentprobe.evaluation.EvaluatorRegistry;
import com.agentprobe.evaluation.Evaluators;
import com.agentprobe.evaluation.GoalReachedEvaluator;
import com.agentprobe.simulator.UserSimulator;
import com.agentprobe.suite.Scenario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioRunnerTest {

    private final AgentAdapter adapter = mock(AgentAdapter.class);
    private final UserSimulator simulator = mock(UserSimulator.class);
    private final EvaluatorRegistry registry = new EvaluatorRegistry(List.of(
            new Evaluators.Contains(), new Evaluators.NotContains(), new Evaluators.ToolCall(),
            new GoalReachedEvaluator()));
    private final ScenarioRunner runner = new ScenarioRunner(adapter, simulator, registry,
            mock(ScenarioResultRepository.class), mock(TurnRecordRepository.class),
            mock(EvaluationRecordRepository.class), 1L);
    private final UUID runId = UUID.randomUUID();

    private static AgentReply reply(String answer, List<Object> tools) {
        return new AgentReply(answer, tools, null, 5, 200, "{}");
    }

    private static AgentReply status(int code) {
        return new AgentReply(null, List.of(), null, 5, code, "{}");
    }

    private static UserSimulator.Step step(String status, String message) {
        return new UserSimulator.Step(status, message, 10);
    }

    private Scenario multi(String evaluatorsJson) {
        Scenario s = new Scenario(UUID.randomUUID(), "cancel-flow", "multi_turn");
        s.setPersona("Polite customer");
        s.setGoal("Cancel order 1042");
        s.setMaxTurns(5);
        s.setEvaluators(evaluatorsJson);
        return s;
    }

    private Scenario single(String evaluatorsJson) {
        Scenario s = new Scenario(UUID.randomUUID(), "single", "single_turn");
        s.setInput("Hello");
        s.setEvaluators(evaluatorsJson);
        return s;
    }

    @Test
    void multiTurnHappyPathIsStoredAndEvaluated() {
        when(simulator.next(any(), any(), any())).thenReturn(
                step("CONTINUE", "Cancel order 1042"), step("CONTINUE", "Yes please"), step("GOAL_REACHED", ""));
        when(adapter.send(any(), any(), any())).thenReturn(
                reply("Please confirm the cancellation", List.of()),
                reply("Order 1042 has been cancelled.",
                        List.<Object>of(Map.of("name", "cancelOrder", "arguments", Map.of("orderId", 1042)))));

        ScenarioRunner.Outcome o = runner.execute(runId, multi(
                        "[{\"type\":\"goal_reached\"},{\"type\":\"tool_call\",\"name\":\"cancelOrder\",\"args\":{\"orderId\":1042}}]"),
                null, 0);

        assertThat(o.result().isPassed()).isTrue();
        assertThat(o.result().getStopReason()).isEqualTo("goal_reached");
        assertThat(o.result().getTurnCount()).isEqualTo(2);
        assertThat(o.turns()).hasSize(4);
        assertThat(o.evals()).hasSize(2);
        assertThat(o.infraFailure()).isFalse();
        assertThat(o.result().getAgentAnswer()).contains("Please confirm").contains("has been cancelled");
    }

    @Test
    void customerGivingUpFailsTheGoalEvaluator() {
        when(simulator.next(any(), any(), any())).thenReturn(step("CONTINUE", "Cancel it"), step("GIVE_UP", ""));
        when(adapter.send(any(), any(), any())).thenReturn(reply("I cannot help with that", List.of()));

        ScenarioRunner.Outcome o = runner.execute(runId, multi("[{\"type\":\"goal_reached\"}]"), null, 0);

        assertThat(o.result().isPassed()).isFalse();
        assertThat(o.result().getStopReason()).isEqualTo("give_up");
    }

    @Test
    void loopingAgentIsStoppedAfterThreeIdenticalReplies() {
        when(simulator.next(any(), any(), any())).thenReturn(step("CONTINUE", "again"));
        when(adapter.send(any(), any(), any())).thenReturn(reply("Same reply", List.of()));

        ScenarioRunner.Outcome o = runner.execute(runId, multi("[]"), null, 0);

        assertThat(o.result().getStopReason()).isEqualTo("agent_looping");
        assertThat(o.result().getTurnCount()).isEqualTo(3);
    }

    @Test
    void unreachableAgentIsRetriedThenReportedAsInfrastructureFailure() {
        when(adapter.send(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Agent unreachable: ConnectException"));

        ScenarioRunner.Outcome o = runner.execute(runId, single("[]"), null, 0);

        assertThat(o.result().isPassed()).isFalse();
        assertThat(o.infraFailure()).isTrue();
        assertThat(o.result().getError()).contains("unreachable");
        verify(adapter, times(3)).send(any(), any(), any());
    }

    @Test
    void transientStatusIsRetriedAndThenSucceeds() {
        when(adapter.send(any(), any(), any())).thenReturn(status(503), reply("Hello there", List.of()));

        ScenarioRunner.Outcome o = runner.execute(runId, single("[{\"type\":\"contains\",\"value\":\"hello\"}]"), null, 0);

        assertThat(o.result().isPassed()).isTrue();
        verify(adapter, times(2)).send(any(), any(), any());
    }

    @Test
    void clientErrorsAreNotRetried() {
        when(adapter.send(any(), any(), any())).thenReturn(status(400));

        ScenarioRunner.Outcome o = runner.execute(runId, single("[]"), null, 0);

        assertThat(o.result().isPassed()).isFalse();
        assertThat(o.infraFailure()).isFalse();
        verify(adapter, times(1)).send(any(), any(), any());
    }
}