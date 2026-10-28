package com.agentprobe.evaluation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Built-in deterministic evaluators. Each nested class is a Spring bean found by EvaluatorRegistry. */
public final class Evaluators {

    private Evaluators() {
    }

    private static String answerOf(ScenarioContext ctx) {
        return ctx.answer() == null ? "" : ctx.answer();
    }

    // ---------------------------------------------------------------- contains
    @Component
    public static class Contains implements Evaluator {
        public String type() { return "contains"; }

        public void validate(Map<String, Object> p) { Params.strings(p, "value"); }

        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            boolean ignoreCase = Params.bool(p, "ignoreCase", true);
            String answer = answerOf(ctx);
            String hay = ignoreCase ? answer.toLowerCase(Locale.ROOT) : answer;
            List<String> missing = new ArrayList<String>();
            for (String v : Params.strings(p, "value")) {
                String needle = ignoreCase ? v.toLowerCase(Locale.ROOT) : v;
                if (!hay.contains(needle)) {
                    missing.add(v);
                }
            }
            return missing.isEmpty()
                    ? EvaluationResult.pass(type(), "Answer contains all expected text")
                    : EvaluationResult.fail(type(), "Answer is missing: " + missing);
        }
    }

    // ------------------------------------------------------------ not_contains
    @Component
    public static class NotContains implements Evaluator {
        public String type() { return "not_contains"; }

        public void validate(Map<String, Object> p) { Params.strings(p, "value"); }

        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            boolean ignoreCase = Params.bool(p, "ignoreCase", true);
            String answer = answerOf(ctx);
            String hay = ignoreCase ? answer.toLowerCase(Locale.ROOT) : answer;
            List<String> found = new ArrayList<String>();
            for (String v : Params.strings(p, "value")) {
                String needle = ignoreCase ? v.toLowerCase(Locale.ROOT) : v;
                if (hay.contains(needle)) {
                    found.add(v);
                }
            }
            return found.isEmpty()
                    ? EvaluationResult.pass(type(), "Answer does not contain forbidden text")
                    : EvaluationResult.fail(type(), "Answer contains forbidden text: " + found);
        }
    }

    // ------------------------------------------------------------------- regex
    @Component
    public static class Regex implements Evaluator {
        public String type() { return "regex"; }

        public void validate(Map<String, Object> p) {
            String pattern = Params.string(p, "pattern");
            if (pattern.length() > 500) {
                throw new IllegalArgumentException("'pattern' is too long (max 500 characters)");
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex: " + e.getDescription());
            }
        }

        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            String pattern = Params.string(p, "pattern");
            int flags = Params.bool(p, "ignoreCase", true)
                    ? (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : 0;
            try {
                boolean found = Pattern.compile(pattern, flags)
                        .matcher(new TimeLimitedCharSequence(answerOf(ctx), 1000)).find();
                return found
                        ? EvaluationResult.pass(type(), "Answer matches /" + pattern + "/")
                        : EvaluationResult.fail(type(), "Answer does not match /" + pattern + "/");
            } catch (IllegalStateException e) {
                return EvaluationResult.fail(type(), "Regex took too long (possible catastrophic backtracking)");
            }
        }
    }

    // ----------------------------------------------------------------- latency
    @Component
    public static class Latency implements Evaluator {
        public String type() { return "latency"; }

        public void validate(Map<String, Object> p) { Params.number(p, "maxMs"); }

        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            double max = Params.number(p, "maxMs");
            return ctx.latencyMs() <= max
                    ? EvaluationResult.pass(type(), "Latency " + ctx.latencyMs() + " ms <= " + (long) max + " ms")
                    : EvaluationResult.fail(type(), "Latency " + ctx.latencyMs() + " ms > " + (long) max + " ms");
        }
    }

    // -------------------------------------------------------------- max_tokens
    @Component
    public static class MaxTokens implements Evaluator {
        public String type() { return "max_tokens"; }

        public void validate(Map<String, Object> p) { Params.number(p, "max"); }

        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            double max = Params.number(p, "max");
            if (ctx.tokens() == null) {
                return EvaluationResult.fail(type(),
                        "Agent did not report token usage (set tokensPath in the responseMapping)");
            }
            return ctx.tokens() <= max
                    ? EvaluationResult.pass(type(), "Tokens " + ctx.tokens() + " <= " + (long) max)
                    : EvaluationResult.fail(type(), "Tokens " + ctx.tokens() + " > " + (long) max);
        }
    }

    // --------------------------------------------------------------- tool_call
    @Component
    public static class ToolCall implements Evaluator {
        public String type() { return "tool_call"; }

        public void validate(Map<String, Object> p) {
            Params.string(p, "name");
            Object args = p.get("args");
            if (args != null && !(args instanceof Map)) {
                throw new IllegalArgumentException("'args' must be a mapping of argument name to value");
            }
        }

        @SuppressWarnings("unchecked")
        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            String name = Params.string(p, "name");
            Map<String, Object> expected = p.get("args") instanceof Map
                    ? (Map<String, Object>) p.get("args") : new HashMap<String, Object>();
            List<String> seen = new ArrayList<String>();

            for (Object tc : ctx.toolCalls()) {
                if (!(tc instanceof Map)) {
                    continue;
                }
                Map<String, Object> call = (Map<String, Object>) tc;
                String callName = String.valueOf(call.get("name"));
                seen.add(callName);
                if (!name.equals(callName)) {
                    continue;
                }
                Map<String, Object> actual = call.get("arguments") instanceof Map
                        ? (Map<String, Object>) call.get("arguments") : new HashMap<String, Object>();
                boolean allMatch = true;
                for (Map.Entry<String, Object> e : expected.entrySet()) {
                    if (!argEquals(e.getValue(), actual.get(e.getKey()))) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return EvaluationResult.pass(type(), "Agent called " + name + " with the expected arguments");
                }
            }
            return EvaluationResult.fail(type(),
                    "No call to " + name + " with expected arguments " + expected + ". Tools called: " + seen);
        }

        static boolean argEquals(Object expected, Object actual) {
            if (expected instanceof Number && actual instanceof Number) {
                return new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())) == 0;
            }
            return String.valueOf(expected).equals(String.valueOf(actual));
        }
    }

    // ---------------------------------------------------- tool_call_forbidden
    @Component
    public static class ToolCallForbidden implements Evaluator {
        public String type() { return "tool_call_forbidden"; }

        public void validate(Map<String, Object> p) { Params.strings(p, "names"); }

        @SuppressWarnings("unchecked")
        public EvaluationResult evaluate(ScenarioContext ctx, Map<String, Object> p) {
            List<String> forbidden = Params.strings(p, "names");
            List<String> violations = new ArrayList<String>();
            for (Object tc : ctx.toolCalls()) {
                if (tc instanceof Map) {
                    String callName = String.valueOf(((Map<String, Object>) tc).get("name"));
                    if (forbidden.contains(callName)) {
                        violations.add(callName);
                    }
                }
            }
            return violations.isEmpty()
                    ? EvaluationResult.pass(type(), "No forbidden tools were called")
                    : EvaluationResult.fail(type(), "Forbidden tools were called: " + violations);
        }
    }
}