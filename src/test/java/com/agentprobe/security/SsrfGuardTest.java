package com.agentprobe.security;

import com.agentprobe.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    private final SsrfGuard strict = new SsrfGuard(false);
    private final SsrfGuard dev = new SsrfGuard(true);

    @Test
    void strictModeBlocksInternalTargets() {
        for (String url : new String[]{
                "http://127.0.0.1:8080/chat", "http://localhost/chat", "http://10.0.0.5/x",
                "http://192.168.1.10/x", "http://172.16.0.1/x", "http://169.254.169.254/latest/meta-data",
                "http://[::1]/x", "http://0.0.0.0/x", "http://100.64.0.1/x"}) {
            assertThatThrownBy(() -> strict.validate(url)).as(url).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void devModeAllowsLoopbackButStillBlocksMetadata() {
        assertThat(dev.validate("http://localhost:8082/chat").getPort()).isEqualTo(8082);
        assertThatThrownBy(() -> dev.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsBadSchemesAndCredentials() {
        assertThatThrownBy(() -> dev.validate("file:///etc/passwd")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> dev.validate("ftp://example.com/x")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> dev.validate("http://user:pw@localhost/x")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> dev.validate("not a url")).isInstanceOf(ApiException.class);
    }
}