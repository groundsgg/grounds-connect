package gg.grounds.connect.core;

import gg.grounds.connect.api.ForgeApiClient;
import gg.grounds.connect.api.ForgeApiException;
import gg.grounds.connect.auth.AuthService;
import gg.grounds.connect.config.GroundsConfig;

/** Runs Forge API calls with a valid access token and one unauthorized retry. */
public final class AuthenticatedApi {
    private final AuthService auth;

    public AuthenticatedApi(AuthService auth) {
        this.auth = auth;
    }

    public ForgeApiClient api() {
        return new ForgeApiClient(GroundsConfig.get().apiBaseUrl());
    }

    public <T> T withAuthRetry(ApiCall<T> call) throws Exception {
        String token = auth.validAccessToken();
        try {
            return call.invoke(token);
        } catch (ForgeApiException e) {
            if (e.isUnauthorized()) {
                String refreshed = auth.forceRefresh();
                if (refreshed != null) {
                    return call.invoke(refreshed);
                }
            }
            throw e;
        }
    }

    public interface ApiCall<T> {
        T invoke(String accessToken) throws Exception;
    }
}
