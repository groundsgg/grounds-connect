package gg.grounds.connect.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** RFC 7636 PKCE pair (S256). */
public record Pkce(String verifier, String challenge) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    public static Pkce generate() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String verifier = B64URL.encodeToString(raw);
        return new Pkce(verifier, challengeFor(verifier));
    }

    private static String challengeFor(String verifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return B64URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
