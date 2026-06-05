package gg.grounds.serverlist.auth;

import java.time.Instant;

/**
 * Stored OIDC credentials. {@code expiresAt}/{@code refreshExpiresAt} are epoch seconds
 * (0 = unknown / never). The Grounds refresh token is an offline token with no expiry,
 * so in practice only {@code expiresAt} matters.
 */
public final class Credentials {
    public int version = 1;
    public String accessToken;
    public String refreshToken;
    public long expiresAt;        // epoch seconds
    public long refreshExpiresAt; // epoch seconds, 0 = never
    public String email;
    public String preferredUsername;

    public Credentials() {}

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** True if the access token is missing or within {@code skewSeconds} of expiry. */
    public boolean accessTokenExpired(long skewSeconds) {
        if (accessToken == null || accessToken.isBlank()) {
            return true;
        }
        if (expiresAt <= 0) {
            return false; // unknown expiry — assume still valid, rely on 401 retry
        }
        return Instant.now().getEpochSecond() >= (expiresAt - skewSeconds);
    }

    public String displayName() {
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "account";
    }
}
