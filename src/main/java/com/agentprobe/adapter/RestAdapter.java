package com.agentprobe.adapter;

import com.agentprobe.api.ApiException;
import com.agentprobe.security.SsrfGuard;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import net.minidev.json.JSONValue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RestAdapter implements AgentAdapter {

    private static final int MAX_RAW_BODY = 20_000;

    private final SsrfGuard ssrfGuard;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public RestAdapter(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    @Override
    public AgentReply send(AgentTarget target, String message, String sessionId) {
        URI uri = ssrfGuard.validate(target.endpointUrl());
        String body = renderTemplate(target.requestTemplateJson(), message, sessionId);

        HttpRequest request;
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (notBlank(target.authHeaderName()) && notBlank(target.authHeaderValue())) {
                rb.header(target.authHeaderName(), target.authHeaderValue());
            }
            request = rb.build();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid auth header name or value");
        }

        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Agent unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Call to agent was interrupted");
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        String raw = response.body() == null ? "" : response.body();
        String storedRaw = raw.length() > MAX_RAW_BODY ? raw.substring(0, MAX_RAW_BODY) : raw;
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            return new AgentReply(null, List.of(), null, latencyMs, status, storedRaw);
        }

        Map<String, Object> mapping = parseObject(target.responseMappingJson());
        String answer = asString(read(raw, str(mapping.get("answerPath"), "$.answer")));
        Object tools = read(raw, str(mapping.get("toolCallsPath"), "$.toolCalls"));
        Object tok = mapping.get("tokensPath") == null ? null : read(raw, str(mapping.get("tokensPath"), null));

        List<Object> toolCalls = tools instanceof List<?> l ? new ArrayList<>(l) : List.of();
        Integer tokens = tok instanceof Number n ? n.intValue() : null;
        return new AgentReply(answer, toolCalls, tokens, latencyMs, status, storedRaw);
    }

    /** sessionId first, message last, so user text is never re-scanned for placeholders. */
    static String renderTemplate(String templateJson, String message, String sessionId) {
        return templateJson
                .replace("{{sessionId}}", JSONValue.escape(sessionId))
                .replace("{{message}}", JSONValue.escape(message));
    }

    private static Object read(String json, String path) {
        if (path == null) {
            return null;
        }
        try {
            return JsonPath.read(json, path);
        } catch (JsonPathException | IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        Object o = JSONValue.parse(json);
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String str(Object o, String fallback) {
        return o instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}