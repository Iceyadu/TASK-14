package com.eaglepoint.exam.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that transparently encrypts {@code String}
 * entity fields to {@code byte[]} columns using AES-256-GCM.
 * <p>
 * Each encryption generates a random 12-byte IV which is prepended to the
 * ciphertext. The combined {@code IV || ciphertext} is stored in the database.
 * <p>
 * Because JPA may instantiate converters outside the Spring context, the AES
 * key is held in a static field that is populated either by Spring (via the
 * {@code @Component} constructor) or falls back to a default value when JPA
 * creates the converter directly.
 */
@Component
@Converter(autoApply = false)
public class EncryptedFieldConverter implements AttributeConverter<String, byte[]> {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String DEFAULT_KEY = "changeThisToA32ByteBase64Key==";

    /**
     * Static holder so that both Spring-managed and JPA-managed instances
     * share the same key material.
     */
    private static volatile SecretKeySpec sharedKey;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Spring-managed constructor. Sets the static key so that any JPA-created
     * instance also picks it up.
     */
    public EncryptedFieldConverter(
            @Value("${app.encryption.aes-key:" + DEFAULT_KEY + "}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must be 16, 24, or 32 bytes after Base64 decoding; got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        sharedKey = this.secretKey;
    }

    /**
     * No-arg constructor used by JPA when it instantiates the converter itself.
     * Falls back to the static key set by the Spring-managed instance, or to
     * the default key if Spring has not yet initialised.
     */
    @SuppressWarnings("unused")
    protected EncryptedFieldConverter() {
        if (sharedKey != null) {
            this.secretKey = sharedKey;
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(DEFAULT_KEY);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Store as IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(dbData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt field", e);
        }
    }
}
