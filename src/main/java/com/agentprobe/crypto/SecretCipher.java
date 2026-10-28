package com.agentprobe.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM. Stored format: base64( iv[12] + ciphertext + tag ). */
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${agentprobe.crypto.key}") String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "agentprobe.crypto.key must be a base64 encoded 32-byte key (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String payload) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(payload));
            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);
            byte[] enc = new byte[buf.remaining()];
            buf.get(enc);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Decryption failed (wrong key or corrupted data)", e);
        }
    }
}