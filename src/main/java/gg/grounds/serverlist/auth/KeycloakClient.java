package gg.grounds.serverlist.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.grounds.serverlist.Constants;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Keycloak OIDC device-authorization-grant client (RFC 8628) with PKCE. */
public final class KeycloakClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Response of the device-authorization request. */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri,
                             String verificationUriComplete, int intervalSeconds, int expiresInSeconds) {}

    /** Outcome of a single token-poll. */
    public sealed interface PollResult {
        record Authorized(Credentials credentials) implements PollResult {}
        record Pending() implements PollResult {}
        record SlowDown() implements PollResult {}
        record Failed(String error) implements PollResult {}
    }

    /** Step 1: request a device + user code (with PKCE challenge). */
    public DeviceCode requestDeviceCode(Pkce pkce) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", Constants.KEYCLOAK_CLIENT_ID);
        form.put("scope", Constants.KEYCLOAK_SCOPE);
        form.put("code_challenge", pkce.challenge());
        form.put("code_challenge_method", "S256");

        HttpResponse<String> res = post(Constants.deviceAuthEndpoint(), form);
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("device-code request failed (HTTP " + res.statusCode() + "): " + res.body());
        }
        JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!has(obj, "device_code") || !has(obj, "user_code") || !has(obj, "verification_uri")) {
            throw new RuntimeException("incomplete device-code response");
        }
        int interval = obj.has("interval") ? obj.get("interval").getAsInt() : 5;
        return new DeviceCode(
                obj.get("device_code").getAsString(),
                obj.get("user_code").getAsString(),
                obj.get("verification_uri").getAsString(),
                obj.has("verification_uri_complete") ? obj.get("verification_uri_complete").getAsString()
                        : obj.get("verification_uri").getAsString(),
                interval,
                obj.has("expires_in") ? obj.get("expires_in").getAsInt() : 600);
    }

    /** Step 2: poll once for a token using the device code + PKCE verifier. */
    public PollResult pollOnce(String deviceCode, Pkce pkce) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", deviceCode);
        form.put("client_id", Constants.KEYCLOAK_CLIENT_ID);
        form.put("code_verifier", pkce.verifier());

        HttpResponse<String> res = post(Constants.tokenEndpoint(), form);
        if (res.statusCode() / 100 == 2) {
            return new PollResult.Authorized(credentialsFromTokenJson(parse(res.body())));
        }
        String error = errorOf(res.body());
        return switch (error) {
            case "authorization_pending" -> new PollResult.Pending();
            case "slow_down" -> new PollResult.SlowDown();
            default -> new PollResult.Failed(error);
        };
    }

    /** Refresh an access token. Returns refreshed credentials. */
    public Credentials refresh(String refreshToken) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", Constants.KEYCLOAK_CLIENT_ID);

        HttpResponse<String> res = post(Constants.tokenEndpoint(), form);
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("token refresh failed (HTTP " + res.statusCode() + "): " + errorOf(res.body()));
        }
        return credentialsFromTokenJson(parse(res.body()));
    }

    // --- helpers ------------------------------------------------------------

    private HttpResponse<String> post(String url, Map<String, String> form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static Credentials credentialsFromTokenJson(JsonObject obj) {
        if (!has(obj, "access_token")) {
            throw new RuntimeException("token response missing access_token");
        }
        Credentials c = new Credentials();
        c.accessToken = obj.get("access_token").getAsString();
        if (obj.has("refresh_token")) {
            c.refreshToken = obj.get("refresh_token").getAsString();
        }
        long now = Instant.now().getEpochSecond();
        c.expiresAt = obj.has("expires_in") ? now + obj.get("expires_in").getAsLong() : 0;
        long refreshExp = obj.has("refresh_expires_in") ? obj.get("refresh_expires_in").getAsLong() : 0;
        c.refreshExpiresAt = refreshExp > 0 ? now + refreshExp : 0;

        // Best-effort identity from the id_token claims (no signature verification needed client-side).
        if (obj.has("id_token")) {
            JsonObject claims = decodeJwtClaims(obj.get("id_token").getAsString());
            if (claims != null) {
                if (claims.has("preferred_username")) {
                    c.preferredUsername = claims.get("preferred_username").getAsString();
                }
                if (claims.has("email")) {
                    c.email = claims.get("email").getAsString();
                }
            }
        }
        return c;
    }

    private static JsonObject decodeJwtClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String errorOf(String body) {
        try {
            JsonObject obj = parse(body);
            if (obj.has("error")) {
                return obj.get("error").getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "unknown_error";
    }

    private static JsonObject parse(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    private static String formEncode(Map<String, String> form) {
        StringJoiner sj = new StringJoiner("&");
        form.forEach((k, v) -> sj.add(enc(k) + "=" + enc(v)));
        return sj.toString();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
