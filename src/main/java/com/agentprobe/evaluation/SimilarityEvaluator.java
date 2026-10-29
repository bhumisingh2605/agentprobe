package com.agentprobe.evaluation;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * type: similarity
 * params: reference (expected answer text), threshold (cosine 0-1, default 0.75)
 * Meaning-based check: passes when the answer is semantically close to the reference.
 */
@Component
public class SimilarityEvaluator implements Evaluator {

    private final EmbeddingModel embeddings;

    public SimilarityEvaluator(EmbeddingModel embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public String type() {
        return "similarity";
    }

    @Override
    public void validate(Map<String, Object> p) {
        String ref = Params.string(p, "reference");
        if (ref.length() > 4000) {
            throw new IllegalArgumentException("'reference' is too long (max 4000 characters)");
        }
        double threshold = Params.numberOr(p, "threshold", 0.75);
        if (threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("'threshold' must be between 0 and 1");
        }
    }

    @Override
    public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
        double threshold = Params.numberOr(p, "threshold", 0.75);
        try {
            float[] ref = embeddings.embed(Params.string(p, "reference"));
            float[] ans = embeddings.embed(ctx.answer());
            double cos = cosine(ref, ans);
            double score = Math.max(0.0, Math.min(1.0, cos));
            boolean passed = cos >= threshold;
            String reasoning = String.format("Cosine similarity %.3f %s threshold %.2f",
                    cos, passed ? ">=" : "<", threshold);
            return new EvaluationResult(type(), passed, score, reasoning);
        } catch (RuntimeException e) {
            return EvaluationResult.fail(type(), "Embedding failed: " + e.getMessage());
        }
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            throw new IllegalArgumentException("Embedding vectors are empty or have different sizes");
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}