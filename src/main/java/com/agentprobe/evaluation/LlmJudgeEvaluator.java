package com.agentprobe.evaluation;

import com.agentprobe.judge.JudgeService;
import com.agentprobe.judge.Rubrics;
import com.agentprobe.util.Json;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * type: llm_judge
 * params: rubric (library name or custom text), threshold (1-5, default 4),
 *         votes (1-5, default 1), reference (optional expected answer)
 */
@Component
public class LlmJudgeEvaluator implements Evaluator {

    private final JudgeService judge;

    public LlmJudgeEvaluator(JudgeService judge) {
        this.judge = judge;
    }

    @Override
    public String type() {
        return "llm_judge";
    }

    @Override
    public void validate(Map<String, Object> p) {
        String rubric = Params.string(p, "rubric");
        if (rubric.length() > 2000) {
            throw new IllegalArgumentException("'rubric' is too long (max 2000 characters)");
        }
        double threshold = Params.numberOr(p, "threshold", 4);
        if (threshold < 1 || threshold > 5) {
            throw new IllegalArgumentException("'threshold' must be between 1 and 5");
        }
        double votes = Params.numberOr(p, "votes", 1);
        if (votes < 1 || votes > 5 || votes != Math.floor(votes)) {
            throw new IllegalArgumentException("'votes' must be a whole number from 1 to 5");
        }
        Object ref = p.get("reference");
        if (ref != null && !(ref instanceof String)) {
            throw new IllegalArgumentException("'reference' must be text");
        }
        if (ref instanceof String && ((String) ref).length() > 4000) {
            throw new IllegalArgumentException("'reference' is too long (max 4000 characters)");
        }
    }

    @Override
    public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
        double threshold = Params.numberOr(p, "threshold", 4);
        int votes = (int) Params.numberOr(p, "votes", 1);
        String reference = null;
        if (p.get("reference") instanceof String) {
            String r = (String) p.get("reference");
            if (!r.isBlank()) {
                reference = r;
            }
        }
        try {
            JudgeService.Verdict v = judge.judge(Rubrics.resolve(Params.string(p, "rubric")), ctx.input(),
                    ctx.answer(), Json.write(ctx.toolCalls()), reference, votes);
            boolean passed = v.score() >= threshold;
            StringBuilder reasoning = new StringBuilder();
            reasoning.append("Judge score ").append(v.score()).append("/5 (passes at ").append(threshold)
                    .append("): ").append(v.reasoning());
            if (!v.evidence().isEmpty()) {
                reasoning.append(" Evidence: ").append(v.evidence());
            }
            return new EvaluationResult(type(), passed, v.score() / 5.0, reasoning.toString(),
                    v.model(), v.promptVersion(), v.tokensEstimate(), v.latencyMs());
        } catch (RuntimeException e) {
            return EvaluationResult.fail(type(), "Judge unavailable: " + e.getMessage());
        }
    }
}