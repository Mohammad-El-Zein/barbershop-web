package com.barbershop.token;

import com.barbershop.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Opaque tokens for email links and refresh cookies.
 *
 * <p>A token is 256 random bits (URL-safe Base64, 43 chars). The database only
 * stores {@code HMAC-SHA256(serverKey, purpose + ":" + token)}:
 * <ul>
 *   <li>a leaked database contains nothing that can be used as a link,</li>
 *   <li>without the server key you cannot even check a guess against the table,</li>
 *   <li>the purpose is part of the MAC, so a "manage" token can never be
 *       replayed as a "payment" or "barber decision" token.</li>
 * </ul>
 * The key is APP_TOKEN_SECRET, or derived from APP_JWT_SECRET (domain-separated)
 * when that is not set.
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_TOKEN_LENGTH = 100;

    private final SecretKeySpec key;

    public TokenHasher(AppProperties props) {
        String secret = props.tokens().secret();
        byte[] material = secret != null && !secret.isBlank()
                ? secret.getBytes(StandardCharsets.UTF_8)
                : hmac(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "barbershop/action-tokens/v1");
        this.key = new SecretKeySpec(material, "HmacSHA256");
    }

    public String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String purpose, String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cheap sanity check before hashing user input. */
    public static boolean plausible(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
