package com.barbershop.booking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cancel tokens: 256 random bits, URL-safe. Only the SHA-256 hash is stored, so a
 * leaked database cannot be used to cancel appointments. The raw token goes to
 * the customer (email link + booking response) and is never persisted.
 */
public final class CancelTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_TOKEN_LENGTH = 100;

    private CancelTokens() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cheap sanity check before hashing user input. */
    public static boolean plausible(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }
}
