package com.agentprobe.util;

import net.minidev.json.JSONValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON helpers so the rest of the code never touches JSONValue directly. */
public final class Json {

    private Json() {
    }

    public static String write(Object o) {
        return JSONValue.toJSONString(o);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String json) {
        Object o = JSONValue.parse(json);
        if (o instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) o);
        }
        return new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(String json) {
        Object o = JSONValue.parse(json);
        if (o instanceof List) {
            return new ArrayList<Object>((List<Object>) o);
        }
        return new ArrayList<Object>();
    }
}