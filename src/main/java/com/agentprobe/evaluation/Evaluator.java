package com.agentprobe.evaluation;

import java.util.Map;

/** Strategy interface: adding a new check means adding one class that implements this. */
public interface Evaluator {

    /** The name used in scenarios, for example "contains". */
    String type();

    /** Throws IllegalArgumentException with a readable message if the params are wrong. */
    void validate(Map<String, Object> params);

    EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> params);
}