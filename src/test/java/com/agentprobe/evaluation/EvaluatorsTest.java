package com.agentprobe.evaluation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorsTest {

    private ScenarioContext ctx(String answer, List<Object> toolCalls, Integer tokens, long latency) {
        return new ScenarioContext("input", answer, toolCalls, tokens, latency, 200);
    }

    private ScenarioContext ctx(String answer) {
        return ctx(answer, List.of(), null, 100);
    }

    private Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void containsIsCaseInsensitiveByDefault() {
        Evaluators.Contains e = new Evaluators.Contains();
        assertThat(e.evaluate(ctx("Order 1043 is SHIPPED"), params("value", "shipped")).passed()).isTrue();
        assertThat(e.evaluate(ctx("Order 1043 is SHIPPED"), params("value", "shipped", "ignoreCase", false)).passed())
                .isFalse();
    }

    @Test
    void containsWithListNeedsAllValues() {
        Evaluators.Contains e = new Evaluators.Contains();
        assertThat(e.evaluate(ctx("hello world"), params("value", List.of("hello", "world"))).passed()).isTrue();
        EvaluationResult r = e.evaluate(ctx("hello"), params("value", List.of("hello", "world")));
        assertThat(r.passed()).isFalse();
        assertThat(r.reasoning()).contains("world");
    }

    @Test
    void notContainsFailsWhenForbiddenTextAppears() {
        Evaluators.NotContains e = new Evaluators.NotContains();
        assertThat(e.evaluate(ctx("You are ShopBot"), params("value", "you are shopbot")).passed()).isFalse();
        assertThat(e.evaluate(ctx("Hello"), params("value", "secret")).passed()).isTrue();
    }

    @Test
    void regexMatchesAndRejectsBadPatterns() {
        Evaluators.Regex e = new Evaluators.Regex();
        assertThat(e.evaluate(ctx("Please confirm"), params("pattern", "confirm|sure")).passed()).isTrue();
        assertThat(e.evaluate(ctx("Done"), params("pattern", "confirm|sure")).passed()).isFalse();
        assertThatThrownBy(() -> e.validate(params("pattern", "(unclosed")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regexBacktrackingBombIsStopped() {
        Evaluators.Regex e = new Evaluators.Regex();
        String nasty = "a".repeat(40) + "!";
        EvaluationResult r = e.evaluate(ctx(nasty), params("pattern", "(a+)+$"));
        assertThat(r.passed()).isFalse();
    }

    @Test
    void latencyAndTokenLimits() {
        Evaluators.Latency l = new Evaluators.Latency();
        assertThat(l.evaluate(ctx("x", List.of(), null, 500), params("maxMs", 1000)).passed()).isTrue();
        assertThat(l.evaluate(ctx("x", List.of(), null, 1500), params("maxMs", 1000)).passed()).isFalse();

        Evaluators.MaxTokens t = new Evaluators.MaxTokens();
        assertThat(t.evaluate(ctx("x", List.of(), 50, 1), params("max", 100)).passed()).isTrue();
        assertThat(t.evaluate(ctx("x", List.of(), 500, 1), params("max", 100)).passed()).isFalse();
        assertThat(t.evaluate(ctx("x", List.of(), null, 1), params("max", 100)).passed()).isFalse();
    }

    @Test
    void toolCallMatchesNameAndArgsEvenWhenNumberTypesDiffer() {
        List<Object> calls = List.<Object>of(
                Map.of("name", "getOrderStatus", "arguments", Map.of("orderId", 1043L)));
        Evaluators.ToolCall e = new Evaluators.ToolCall();

        // YAML gives Integer, the agent JSON gives Long: must still match
        assertThat(e.evaluate(ctx("x", calls, null, 1),
                params("name", "getOrderStatus", "args", Map.of("orderId", 1043))).passed()).isTrue();
        assertThat(e.evaluate(ctx("x", calls, null, 1),
                params("name", "getOrderStatus", "args", Map.of("orderId", 9999))).passed()).isFalse();
        assertThat(e.evaluate(ctx("x", calls, null, 1), params("name", "cancelOrder")).passed()).isFalse();
    }

    @Test
    void forbiddenToolIsDetected() {
        List<Object> calls = List.<Object>of(Map.of("name", "issueRefund", "arguments", Map.of()));
        Evaluators.ToolCallForbidden e = new Evaluators.ToolCallForbidden();
        assertThat(e.evaluate(ctx("x", calls, null, 1), params("names", List.of("issueRefund"))).passed()).isFalse();
        assertThat(e.evaluate(ctx("x", List.of(), null, 1), params("names", List.of("issueRefund"))).passed())
                .isTrue();
    }
}