package com.agentprobe.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static final SecretKey KEY = key("test-secret-test-secret-test-secret!!");

    private static JwtEncoder encoder(SecretKey k) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(k));
    }

    private static JwtDecoder decoder(SecretKey k) {
        return NimbusJwtDecoder.withSecretKey(k).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void validTokenIsDecodedWithSubjectAndEmail() {
        JwtService service = new JwtService(encoder(KEY), 15);
        UUID id = UUID.randomUUID();

        String token = service.createAccessToken(id, "a@b.com");
        Jwt jwt = decoder(KEY).decode(token);

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("a@b.com");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(encoder(KEY), 15);
        String token = service.createAccessToken(UUID.randomUUID(), "a@b.com");

        String[] p = token.split("\\.");
        char first = p[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = p[0] + "." + p[1] + "." + first + p[2].substring(1);

        assertThatThrownBy(() -> decoder(KEY).decode(tampered)).isInstanceOf(JwtException.class);
    }
    @Test
    void expiredTokenIsRejected() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agentprobe")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(10, ChronoUnit.MINUTES))
                .expiresAt(now.minus(5, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder(KEY).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> decoder(KEY).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecretKey other = key("another-secret-another-secret-123456");
        JwtService service = new JwtService(encoder(other), 15);
        String token = service.createAccessToken(UUID.randomUUID(), "a@b.com");

        assertThatThrownBy(() -> decoder(KEY).decode(token)).isInstanceOf(JwtException.class);
    }
}