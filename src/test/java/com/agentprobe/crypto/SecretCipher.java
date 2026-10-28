package com.agentprobe.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    @Test
    void roundTrip() {
        SecretCipher c = new SecretCipher(KEY);
        String enc = c.encrypt("Bearer sk-secret");
        assertThat(enc).doesNotContain("sk-secret");
        assertThat(c.decrypt(enc)).isEqualTo("Bearer sk-secret");
    }

    @Test
    void sameInputGivesDifferentCiphertext() {
        SecretCipher c = new SecretCipher(KEY);
        assertThat(c.encrypt("x")).isNotEqualTo(c.encrypt("x"));
    }

    @Test
    void wrongKeyFails() {
        String enc = new SecretCipher(KEY).encrypt("x");
        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void badKeyLengthRejected() {
        assertThatThrownBy(() -> new SecretCipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
    }
}