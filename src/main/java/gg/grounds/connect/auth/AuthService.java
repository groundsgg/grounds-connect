package gg.grounds.connect.auth;

import gg.grounds.connect.Constants;
import gg.grounds.connect.core.ClientTaskRunner;
import java.time.Instant;

/** Owns authentication state and device-login flow for the Grounds client. */
public final class AuthService {
  private static final long REFRESH_SKEW_SECONDS = 30;

  private final ClientTaskRunner runner;
  private final KeycloakClient keycloak = new KeycloakClient();
  private final Object refreshLock = new Object();
  private volatile Credentials credentials;
  private long authGeneration;
  private boolean refreshInProgress;

  public AuthService(ClientTaskRunner runner) {
    this.runner = runner;
  }

  public synchronized void loadFromStore() {
    this.credentials = TokenStore.load();
    authGeneration++;
  }

  public boolean isLoggedIn() {
    Credentials c = credentials;
    return c != null && c.accessToken != null && !c.accessToken.isBlank();
  }

  public String accountName() {
    Credentials c = credentials;
    return c != null ? c.displayName() : "";
  }

  public synchronized void logout() {
    credentials = null;
    authGeneration++;
    TokenStore.clear();
  }

  public synchronized LoginHandle startLogin(LoginListener listener) {
    LoginHandle handle = new LoginHandle();
    long loginGeneration = ++authGeneration;
    runner.execute(() -> runLogin(loginGeneration, handle, listener));
    return handle;
  }

  /** Returns a non-expired access token, refreshing proactively if near expiry. */
  public String validAccessToken() throws Exception {
    String token = currentAccessToken();
    if (token != null) {
      return token;
    }
    token = forceRefresh();
    if (token == null) {
      throw new IllegalStateException("not logged in");
    }
    return token;
  }

  /** Forces a refresh-token exchange; returns the new access token, or null if not possible. */
  public String forceRefresh() throws Exception {
    synchronized (refreshLock) {
      while (refreshInProgress) {
        refreshLock.wait();
        String token = currentAccessToken();
        if (token != null) {
          return token;
        }
      }
      refreshInProgress = true;
    }

    try {
      return refreshNow();
    } finally {
      synchronized (refreshLock) {
        refreshInProgress = false;
        refreshLock.notifyAll();
      }
    }
  }

  private synchronized String currentAccessToken() {
    Credentials c = credentials;
    if (c == null || c.accessToken == null) {
      return null;
    }
    if (!c.accessTokenExpired(REFRESH_SKEW_SECONDS) || !c.hasRefreshToken()) {
      return c.accessToken;
    }
    return null;
  }

  private String refreshNow() throws Exception {
    Credentials c;
    long generation;
    synchronized (this) {
      c = credentials;
      generation = authGeneration;
      if (c == null || !c.hasRefreshToken()) {
        return null;
      }
    }
    Credentials refreshed = keycloak.refresh(c.refreshToken);
    if (refreshed.refreshToken == null) {
      refreshed.refreshToken = c.refreshToken;
    }
    if (refreshed.email == null) {
      refreshed.email = c.email;
    }
    if (refreshed.preferredUsername == null) {
      refreshed.preferredUsername = c.preferredUsername;
    }
    synchronized (this) {
      if (authGeneration != generation || credentials != c) {
        return null;
      }
      this.credentials = refreshed;
      TokenStore.save(refreshed);
      return refreshed.accessToken;
    }
  }

  private void runLogin(long loginGeneration, LoginHandle handle, LoginListener listener) {
    try {
      Pkce pkce = Pkce.generate();
      KeycloakClient.DeviceCode code = keycloak.requestDeviceCode(pkce);
      runner.onClient(() -> listener.onCode(code));

      long deadline = Instant.now().getEpochSecond() + code.expiresInSeconds();
      long intervalMs = Math.max(1, code.intervalSeconds()) * 1000L;

      while (!handle.isCancelled()) {
        if (Instant.now().getEpochSecond() >= deadline) {
          runner.onClient(() -> listener.onError("expired_token"));
          return;
        }
        Thread.sleep(intervalMs);
        if (handle.isCancelled()) {
          return;
        }
        KeycloakClient.PollResult result = keycloak.pollOnce(code.deviceCode(), pkce);
        if (result instanceof KeycloakClient.PollResult.Authorized authorized) {
          Credentials c = authorized.credentials();
          if (!completeLogin(loginGeneration, handle, c)) {
            return;
          }
          runner.onClient(listener::onSuccess);
          return;
        } else if (result instanceof KeycloakClient.PollResult.SlowDown) {
          intervalMs += 5000L;
        } else if (result instanceof KeycloakClient.PollResult.Failed failed) {
          runner.onClient(() -> listener.onError(failed.error()));
          return;
        }
        // Pending -> keep polling.
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      Constants.LOG.warn("[grounds] device login failed", t);
      String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
      runner.onClient(() -> listener.onError(msg));
    }
  }

  private synchronized boolean completeLogin(
      long loginGeneration, LoginHandle handle, Credentials c) {
    if (handle.isCancelled() || authGeneration != loginGeneration) {
      return false;
    }
    this.credentials = c;
    TokenStore.save(c);
    return true;
  }
}
