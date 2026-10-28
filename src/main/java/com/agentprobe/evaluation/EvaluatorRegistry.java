package com.agentprobe.evaluation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class EvaluatorRegistry {

    private final Map<String, Evaluator> byType = new LinkedHashMap<String, Evaluator>();

    public EvaluatorRegistry(List<Evaluator> evaluators) {
        for (Evaluator e : evaluators) {
            byType.put(e.type(), e);
        }
    }

    public Optional<Evaluator> find(String type) {
        return Optional.ofNullable(byType.get(type));
    }

    public Set<String> types() {
        return byType.keySet();
    }
}