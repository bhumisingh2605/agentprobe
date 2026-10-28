package com.agentprobe.adapter;

import com.agentprobe.api.ApiException;
import com.agentprobe.security.SsrfGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAdapterTest {

    private static final String TEMPLATE = "{\"message\":\"{{message}}\",\"sessionId\":\"{{sessionId}}\"}";
    private static final String MAPPING =
            "{\"answerPath\":\"$.answer\",\"toolCallsPath\":\"$.toolCalls\",\"tokensPath\":\"$.usage.total\"}";

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private final RestAdapter adapter = new RestAdapter(new SsrfGuard(true));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AgentTarget target(String authName, String authValue) {
        return new AgentTarget("http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                authName, authValue, TEMPLATE, MAPPING);
    }

    @Test
    void mapsAnswerToolCallsAndTokens() {
        responseBody = "{\"answer\":\"Order cancelled\",\"toolCalls\":[{\"name\":\"cancelOrder\"}],\"usage\":{\"total\":42}}";
        AgentReply r = adapter.send(target("Authorization", "Bearer abc"), "cancel 1042", "s1");

        assertThat(r.answer()).isEqualTo("Order cancelled");
        assertThat(r.toolCalls()).hasSize(1);
        assertThat(r.tokens()).isEqualTo(42);
        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(lastAuth.get()).isEqualTo("Bearer abc");
        assertThat(lastBody.get()).contains("cancel 1042").contains("s1");
    }

    @Test
    void messageWithQuotesAndBracesIsEscaped() {
        responseBody = "{\"answer\":\"ok\"}";
        adapter.send(target(null, null), "say \"hi\" {{sessionId}} \\ end", "s1");
        assertThat(lastBody.get()).contains("say \\\"hi\\\"");
        assertThat(lastBody.get()).contains("{{sessionId}}");
    }

    @Test
    void non2xxReturnsNullAnswerWithStatus() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";
        AgentReply r = adapter.send(target(null, null), "hi", "s1");
        assertThat(r.answer()).isNull();
        assertThat(r.httpStatus()).isEqualTo(500);
        assertThat(r.rawBody()).contains("boom");
    }

    @Test
    void missingPathGivesNullAnswerNotException() {
        responseBody = "{\"somethingElse\":1}";
        AgentReply r = adapter.send(target(null, null), "hi", "s1");
        assertThat(r.answer()).isNull();
        assertThat(r.toolCalls()).isEmpty();
    }

    @Test
    void strictGuardBlocksLocalhostAtCallTime() {
        RestAdapter strict = new RestAdapter(new SsrfGuard(false));
        assertThatThrownBy(() -> strict.send(target(null, null), "hi", "s1")).isInstanceOf(ApiException.class);
    }
}