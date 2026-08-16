package com.codereview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for sensitive tokens.
 * Uses a key derived from the GITHUB_TOKEN_ENCRYPTION_KEY environment variable.
 */
@Slf4j
@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";

    private final SecretKey secretKey;

    public TokenEncryptionService(
            @Value("${github.token-encryption-key:${GITHUB_TOKEN_ENCRYPTION_KEY:}}") String encryptionKey) {
        this.secretKey = deriveKey(encryptionKey);
    }

    /**
     * Encrypt a plaintext token using AES-256-GCM.
     *
     * @param plaintext the token to encrypt
     * @return Base64-encoded encrypted token with salt and IV prepended
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [IV length (4 bytes)][IV][ciphertext]
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + cipherText.length);
            byteBuffer.putInt(iv.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt token: {}", e.getMessage());
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted token.
     *
     * @param encryptedToken Base64-encoded encrypted token
     * @return the original plaintext token
     */
    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            return encryptedToken;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedToken);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            int ivLength = byteBuffer.getInt();
            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt token: {}", e.getMessage());
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    /**
     * Derive a 256-bit AES key from the provided passphrase using PBKDF2.
     */
    private SecretKey deriveKey(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            log.warn("No encryption key configured; using a generated key (tokens will not survive restart)");
            passphrase = generateRandomKey();
        }

        try {
            // Use a fixed salt for deterministic key derivation from the same passphrase
            byte[] salt = "codereview-github-token-v1".getBytes(StandardCharsets.UTF_8);

            PBEKeySpec spec = new PBEKeySpec(
                    passphrase.toCharArray(),
                    salt,
                    ITERATION_COUNT,
                    KEY_LENGTH
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Failed to derive encryption key: {}", e.getMessage());
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    /**
     * Generate a random 32-character key as fallback.
     */
    private String generateRandomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
