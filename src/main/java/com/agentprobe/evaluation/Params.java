package com.agentprobe.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Params {

    private Params() {
    }

    public static String string(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof String) {
            String s = (String) v;
            if (!s.isBlank()) {
                return s;
            }
        }
        throw new IllegalArgumentException("'" + key + "' is required and must be text");
    }

    public static double number(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        throw new IllegalArgumentException("'" + key + "' is required and must be a number");
    }

    public static double numberOr(Map<String, Object> p, String key, double fallback) {
        Object v = p.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    public static boolean bool(Map<String, Object> p, String key, boolean fallback) {
        Object v = p.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    /** Accepts a single string or a list of strings. */
    public static List<String> strings(Map<String, Object> p, String key) {
        Object v = p.get(key);
        List<String> out = new ArrayList<String>();
        if (v instanceof String) {
            if (!((String) v).isBlank()) {
                out.add((String) v);
            }
        } else if (v instanceof List) {
            for (Object o : (List<?>) v) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o));
                }
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("'" + key + "' is required (text or a list of text)");
        }
        return out;
    }
}