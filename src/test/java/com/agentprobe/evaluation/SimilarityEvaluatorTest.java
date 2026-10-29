package com.agentprobe.evaluation;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class SimilarityEvaluatorTest {

    private final EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
    private final SimilarityEvaluator evaluator = new SimilarityEvaluator(model);

    private ScenarioContext ctx(String answer) {
        return new ScenarioContext("q", answer, List.of(), null, 1, 200);
    }

    private Map<String, Object> params(String reference, double threshold) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("reference", reference);
        m.put("threshold", threshold);
        return m;
    }

    @Test
    void cosineOfIdenticalOppositeAndOrthogonalVectors() {
        assertThat(SimilarityEvaluator.cosine(new float[]{1, 2, 3}, new float[]{1, 2, 3})).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SimilarityEvaluator.cosine(new float[]{1, 0}, new float[]{-1, 0})).isCloseTo(-1.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SimilarityEvaluator.cosine(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SimilarityEvaluator.cosine(new float[]{0, 0}, new float[]{1, 1})).isEqualTo(0.0);
    }

    @Test
    void differentVectorSizesAreRejected() {
        assertThatThrownBy(() -> SimilarityEvaluator.cosine(new float[]{1}, new float[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passesWhenAnswerIsCloseToReference() {
        when(model.embed("reference")).thenReturn(new float[]{1, 0});
        when(model.embed("close answer")).thenReturn(new float[]{0.9f, 0.1f});

        EvaluationResult r = evaluator.evaluate(ctx("close answer"), params("reference", 0.75));
        assertThat(r.passed()).isTrue();
        assertThat(r.score()).isGreaterThan(0.9);
    }

    @Test
    void failsWhenAnswerIsFarFromReference() {
        when(model.embed("reference")).thenReturn(new float[]{1, 0});
        when(model.embed("unrelated")).thenReturn(new float[]{0, 1});

        assertThat(evaluator.evaluate(ctx("unrelated"), params("reference", 0.75)).passed()).isFalse();
    }

    @Test
    void embeddingFailureBecomesAFailedEvaluationNotACrash() {
        when(model.embed("reference")).thenThrow(new RuntimeException("model not found"));
        EvaluationResult r = evaluator.evaluate(ctx("x"), params("reference", 0.75));
        assertThat(r.passed()).isFalse();
        assertThat(r.reasoning()).contains("Embedding failed");
    }
}