package com.agentprobe.evaluation;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * type: goal_reached (no params). For multi_turn scenarios: passes when the simulated customer
 * decided its goal was achieved. The simulator is an LLM, so pair this with tool_call or llm_judge checks.
 */
@Component
public class GoalReachedEvaluator implements Evaluator {

    @Override
    public String type() {
        return "goal_reached";
    }

    @Override
    public void validate(Map<String, Object> params) {
        // no parameters
    }

    @Override
    public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> params) {
        if ("goal_reached".equals(ctx.stopReason())) {
            return EvaluationResult.pass(type(), "The simulated customer reached its goal after " + ctx.turns() + " turn(s)");
        }
        return EvaluationResult.fail(type(), "Conversation ended with '" + ctx.stopReason()
                + "' after " + ctx.turns() + " turn(s) instead of goal_reached");
    }
}