package com.ghana.dashboard.service;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for the small number of columns that must not sit in the
 * database as readable text (currently the shop's bank account details).
 *
 * <p>The configured {@code app.crypto.aes-key} is a passphrase rather than a raw key,
 * so it is stretched to 256 bits with SHA-256 rather than used directly. That is
 * deliberately not a password KDF: the value comes from configuration, not from a
 * human choosing it, so there is no dictionary to grind and the simpler derivation
 * keeps the format self-describing.
 *
 * <p>Ciphertext is stored as {@code v1:<base64(iv)>:<base64(ciphertext+tag)>} so the
 * scheme can be rotated later without guessing how an existing value was produced.
 * GCM is used rather than CBC so that a row cannot be silently tampered with.
 */
@Component
public class AesCryptoService {

    private static final String PREFIX = "v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesCryptoService(AppProperties props) {
        String configured = props.getCrypto() == null ? null : props.getCrypto().getAesKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.aes-key must be set: encrypted settings columns cannot be stored without it");
        }
        this.key = new SecretKeySpec(sha256(configured), "AES");
    }

    /** Encrypts for storage. Returns {@code null} for blank input so callers can pass through. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(sealed);
        } catch (Exception e) {
            throw new DomainException(500, "Could not protect the value for storage");
        }
    }

    /** Reverses {@link #encrypt}. Values without the version prefix are returned untouched. */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            // Written before encryption was introduced; nothing to undo.
            return stored;
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] sealed = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Almost always a rotated or mismatched key. Never echo the ciphertext.
            throw new DomainException(500, "Stored value could not be read with the configured encryption key");
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX + ":");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}