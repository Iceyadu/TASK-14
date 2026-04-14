package com.eaglepoint.exam.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptedFieldConverter} covering AES-256-GCM
 * encrypt/decrypt round-trip, ciphertext opacity, IV randomization,
 * and wrong-key failure.
 */
class EncryptionTest {

    private EncryptedFieldConverter converter;

    /**
     * A valid 32-byte (256-bit) AES key encoded as Base64.
     */
    private static final String TEST_KEY_BASE64 =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    private static final String WRONG_KEY_BASE64 =
            Base64.getEncoder().encodeToString("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX".getBytes());

    @BeforeEach
    void setUp() {
        converter = new EncryptedFieldConverter(TEST_KEY_BASE64);
    }

    @Test
    void testEncryptDecryptRoundTrip() {
        String original = "Sensitive Student ID: STU12345678";

        byte[] encrypted = converter.convertToDatabaseColumn(original);
        assertNotNull(encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void testEncryptedOutputDiffersFromPlaintext() {
        String plaintext = "guardian@school.local";

        byte[] encrypted = converter.convertToDatabaseColumn(plaintext);
        byte[] plaintextBytes = plaintext.getBytes();

        assertNotNull(encrypted);
        assertFalse(Arrays.equals(encrypted, plaintextBytes),
                "Ciphertext must not equal the plaintext bytes");
    }

    @Test
    void testDifferentEncryptionsProduceDifferentCiphertext() {
        String input = "Same input data for two encryptions";

        byte[] encrypted1 = converter.convertToDatabaseColumn(input);
        byte[] encrypted2 = converter.convertToDatabaseColumn(input);

        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertFalse(Arrays.equals(encrypted1, encrypted2),
                "Two encryptions of the same plaintext should produce different ciphertext due to random IV");

        // Both should still decrypt to the original
        assertEquals(input, converter.convertToEntityAttribute(encrypted1));
        assertEquals(input, converter.convertToEntityAttribute(encrypted2));
    }

    @Test
    void testDecryptWithWrongKeyFails() {
        String original = "Secret data";

        byte[] encrypted = converter.convertToDatabaseColumn(original);

        // Create a new converter with a different key
        EncryptedFieldConverter wrongKeyConverter = new EncryptedFieldConverter(WRONG_KEY_BASE64);

        assertThrows(RuntimeException.class,
                () -> wrongKeyConverter.convertToEntityAttribute(encrypted),
                "Decrypting with the wrong key should fail");
    }

    @Test
    void testNullInputReturnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
