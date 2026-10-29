package com.agentprobe.evaluation;

import com.agentprobe.judge.JudgeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class LlmJudgeEvaluatorTest {

    private final JudgeService judge = Mockito.mock(JudgeService.class);
    private final LlmJudgeEvaluator evaluator = new LlmJudgeEvaluator(judge);
    private final ScenarioContext ctx = new ScenarioContext("hi", "answer", List.of(), null, 10, 200);

    private Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private JudgeService.Verdict verdict(int score) {
        return new JudgeService.Verdict(score, "because", List.of("quote"), "test-model", "judge-v1", 100, 5);
    }

    @Test
    void passesWhenScoreReachesThreshold() {
        when(judge.judge(any(), any(), any(), any(), any(), anyInt())).thenReturn(verdict(4));
        EvaluationResult r = evaluator.evaluate(ctx, params("rubric", "correctness", "threshold", 4));

        assertThat(r.passed()).isTrue();
        assertThat(r.score()).isEqualTo(0.8);
        assertThat(r.judgeModel()).isEqualTo("test-model");
        assertThat(r.judgePromptVersion()).isEqualTo("judge-v1");
        assertThat(r.reasoning()).contains("4/5").contains("because");
    }

    @Test
    void failsBelowThreshold() {
        when(judge.judge(any(), any(), any(), any(), any(), anyInt())).thenReturn(verdict(2));
        assertThat(evaluator.evaluate(ctx, params("rubric", "tone")).passed()).isFalse();
    }

    @Test
    void defaultThresholdIsFour() {
        when(judge.judge(any(), any(), any(), any(), any(), anyInt())).thenReturn(verdict(3));
        assertThat(evaluator.evaluate(ctx, params("rubric", "tone")).passed()).isFalse();
    }

    @Test
    void unavailableJudgeGivesFailWithClearMessage() {
        when(judge.judge(any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("Ollama is down"));
        EvaluationResult r = evaluator.evaluate(ctx, params("rubric", "tone"));

        assertThat(r.passed()).isFalse();
        assertThat(r.reasoning()).contains("Judge unavailable").contains("Ollama is down");
    }

    @Test
    void validationRejectsBadParams() {
        assertThatThrownBy(() -> evaluator.validate(params())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.validate(params("rubric", "x", "threshold", 9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.validate(params("rubric", "x", "votes", 2.5)))
                .isInstanceOf(IllegalArgumentException.class);
        evaluator.validate(params("rubric", "x", "threshold", 3, "votes", 3)); // valid, no exception
    }
}