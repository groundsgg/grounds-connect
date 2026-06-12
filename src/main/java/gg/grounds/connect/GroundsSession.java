package gg.grounds.connect;

import com.google.gson.JsonParser;
import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.ForgeApiClient;
import gg.grounds.connect.api.ForgeApiException;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.api.Nats;
import gg.grounds.connect.api.Push;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.api.RollbackTarget;
import gg.grounds.connect.auth.Credentials;
import gg.grounds.connect.auth.KeycloakClient;
import gg.grounds.connect.auth.Pkce;
import gg.grounds.connect.auth.TokenStore;
import gg.grounds.connect.config.GroundsConfig;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Holds the logged-in {@link Credentials}, the cached project list, and orchestrates all
 * network work off the render thread. Result callbacks are delivered on the client thread.
 */
public final class GroundsSession {

    private static final long REFRESH_SKEW_SECONDS = 30;
    private static final GroundsSession INSTANCE = new GroundsSession();

    public static GroundsSession get() {
        return INSTANCE;
    }

    /** Generic async result sink. Both methods run on the client (render) thread. */
    public interface Callback<T> {
        void onResult(T value);
        void onError(Throwable error);
    }

    /** Receives push (build/deploy) status transitions on the client thread. */
    public interface PushStatusSink {
        void onStatus(String pushId, String appName, String status);
    }

    /** Receives log lines from an SSE log endpoint on the client thread. */
    public interface LogSink {
        void onLine(String line);
    }

    /** Receives NATS live-tail frames on the client thread. */
    public interface NatsTailSink {
        void onMessage(Nats.TailMessage message);
        void onInfo(String line);
    }

    /** Device-login progress sink. All methods run on the client thread. */
    public interface LoginListener {
        void onCode(KeycloakClient.DeviceCode code);
        void onSuccess();
        void onError(String message);
    }

    /** Handle to an in-flight login, allowing cancellation. */
    public static final class LoginHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        public void cancel() {
            cancelled.set(true);
        }
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "grounds-net");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "grounds-watch");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean deployWatcherStarted;
    private final KeycloakClient keycloak = new KeycloakClient();

    private volatile Credentials credentials;
    private volatile List<Project> cachedProjects = List.of();
    private final Set<String> watchedPushes = ConcurrentHashMap.newKeySet();
    private volatile String lastJoinedGroundsAddress;

    private GroundsSession() {}

    // --- session state ------------------------------------------------------

    public void loadFromStore() {
        this.credentials = TokenStore.load();
    }

    public boolean isLoggedIn() {
        Credentials c = credentials;
        return c != null && c.accessToken != null && !c.accessToken.isBlank();
    }

    public String accountName() {
        Credentials c = credentials;
        return c != null ? c.displayName() : "";
    }

    public List<Project> cachedProjects() {
        return cachedProjects;
    }

    public void logout() {
        credentials = null;
        cachedProjects = List.of();
        TokenStore.clear();
    }

    /** Resolves the selected project from config against the cached list (falls back to the first). */
    public Project selectedProject() {
        String id = GroundsConfig.get().selectedProjectId();
        List<Project> projects = cachedProjects;
        if (projects.isEmpty()) {
            return null;
        }
        if (id != null) {
            for (Project p : projects) {
                if (Objects.equals(p.id(), id)) {
                    return p;
                }
            }
        }
        return projects.get(0);
    }

    public void selectProject(Project project) {
        GroundsConfig.get().setSelectedProjectId(project == null ? null : project.id());
    }

    /** Remember that we're connecting to a Grounds server, so leaving it returns to our screen. */
    public void markGroundsJoin(String address) {
        this.lastJoinedGroundsAddress = address;
    }

    public boolean isGroundsAddress(String address) {
        String a = lastJoinedGroundsAddress;
        return a != null && a.equalsIgnoreCase(address);
    }

    // --- async operations ---------------------------------------------------

    public LoginHandle startLogin(LoginListener listener) {
        LoginHandle handle = new LoginHandle();
        executor.submit(() -> runLogin(handle, listener));
        return handle;
    }

    /** Probes platform readiness ({@code /readyz}, unauthenticated); delivers the result on the client thread. */
    public void pollPlatformReadiness(Consumer<Boolean> sink) {
        executor.submit(() -> {
            boolean ready = api().isReady();
            onClient(() -> sink.accept(ready));
        });
    }

    public void fetchProjects(Callback<List<Project>> cb) {
        executor.submit(() -> {
            try {
                List<Project> projects = withAuthRetry(this::listProjects);
                this.cachedProjects = projects;
                onClient(() -> cb.onResult(projects));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    public void fetchServers(String projectId, Callback<List<GroundsServer>> cb) {
        executor.submit(() -> {
            try {
                List<GroundsServer> servers = withAuthRetry(token -> api().listMinecraftServers(token, projectId));
                onClient(() -> cb.onResult(servers));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    public void fetchRuntime(String name, String projectId, Callback<DeploymentRuntime> cb) {
        executor.submit(() -> {
            try {
                DeploymentRuntime rt = withAuthRetry(token -> api().getRuntime(token, name, projectId));
                onClient(() -> cb.onResult(rt));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    /** Resumes a paused deployment, then polls runtime until ready (or ~90s) before {@code onReady}. */
    public void resumeAndAwait(String name, String projectId, Consumer<String> onProgress,
                              Runnable onReady, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                withAuthRetry(token -> {
                    api().resume(token, name, projectId);
                    return Boolean.TRUE;
                });
                long deadline = Instant.now().getEpochSecond() + 90;
                while (Instant.now().getEpochSecond() < deadline) {
                    DeploymentRuntime rt = withAuthRetry(token -> api().getRuntime(token, name, projectId));
                    onClient(() -> onProgress.accept(rt.replicasReady() + "/" + rt.replicasDesired()));
                    if (rt.replicasDesired() > 0 && rt.replicasReady() >= rt.replicasDesired()) {
                        onClient(onReady);
                        return;
                    }
                    Thread.sleep(2000);
                }
                onClient(onReady); // timed out — try connecting anyway
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                onClient(() -> onError.accept(message(t)));
            }
        });
    }

    public void fetchLatestPush(String name, String projectId, Callback<Push> cb) {
        executor.submit(() -> {
            try {
                Push p = withAuthRetry(token -> api().getLatestPush(token, projectId, name));
                onClient(() -> cb.onResult(p));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    public void fetchRollbackTargets(String name, String projectId, Callback<List<RollbackTarget>> cb) {
        executor.submit(() -> {
            try {
                String app = withAuthRetry(token -> api().getAppName(token, projectId, name));
                List<RollbackTarget> targets = withAuthRetry(token -> api().listRollbackTargets(token, projectId, app));
                onClient(() -> cb.onResult(targets));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    public void rollback(String name, String projectId, String pushId, Runnable onDone, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                withAuthRetry(token -> {
                    api().rollback(token, name, projectId, pushId);
                    return Boolean.TRUE;
                });
                onClient(onDone);
            } catch (ForgeApiException e) {
                String m = e.statusCode() == 403 ? "owner or editor role required" : e.getMessage();
                onClient(() -> onError.accept(m));
            } catch (Throwable t) {
                onClient(() -> onError.accept(message(t)));
            }
        });
    }

    /** Retries the deployment's latest push if it failed. */
    public void retryLatestBuild(String name, String projectId, Runnable onStarted, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                Push latest = withAuthRetry(token -> api().getLatestPush(token, projectId, name));
                if (latest == null) {
                    onClient(() -> onError.accept("no build found"));
                    return;
                }
                String st = latest.status();
                if (!"build_failed".equals(st) && !"deploy_failed".equals(st)) {
                    onClient(() -> onError.accept("latest build is '" + st + "' — nothing to retry"));
                    return;
                }
                withAuthRetry(token -> {
                    api().retryPush(token, latest.id(), projectId);
                    return Boolean.TRUE;
                });
                onClient(onStarted);
            } catch (Throwable t) {
                onClient(() -> onError.accept(message(t)));
            }
        });
    }

    /** Background poller: shows build/deploy toasts even when the Grounds screen is closed. */
    public void startDeployWatcher(PushStatusSink sink) {
        if (deployWatcherStarted) {
            return;
        }
        deployWatcherStarted = true;
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isLoggedIn()) {
                    return;
                }
                if (cachedProjects.isEmpty()) {
                    cachedProjects = withAuthRetry(this::listProjects);
                }
                Project selected = selectedProject();
                if (selected != null) {
                    watchInFlightPushes(selected.id(), sink);
                }
            } catch (Throwable ignored) {
                // transient; try again next tick
            }
        }, 10, 15, TimeUnit.SECONDS);
    }

    private static String message(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    /**
     * Finds in-flight pushes in the project and streams each one's status to {@code sink} (toasts).
     * Streams run on the executor and outlive the screen, so progress keeps arriving in-game.
     * Already-watched pushes are skipped, so this is safe to call repeatedly (e.g. on a poll).
     */
    public void watchInFlightPushes(String projectId, PushStatusSink sink) {
        executor.submit(() -> {
            try {
                List<Push> inflight = withAuthRetry(token -> api().listInFlightPushes(token, projectId));
                for (Push p : inflight) {
                    if (watchedPushes.add(p.id())) {
                        streamPush(p, sink);
                    }
                }
            } catch (Throwable t) {
                Constants.LOG.debug("[grounds] watch pushes failed: {}", t.toString());
            }
        });
    }

    private void streamPush(Push push, PushStatusSink sink) {
        executor.submit(() -> {
            try {
                onClient(() -> sink.onStatus(push.id(), push.appName(), push.status()));
                String token = validAccessToken();
                api().streamSse(token, "/v1/pushes/" + push.id() + "/logs", (event, data) -> {
                    if ("status".equals(event)) {
                        String st = parsePushStatus(data);
                        if (st != null && !st.isBlank()) {
                            onClient(() -> sink.onStatus(push.id(), push.appName(), st));
                        }
                    }
                }, () -> false);
            } catch (Throwable t) {
                Constants.LOG.debug("[grounds] push stream {} ended: {}", push.id(), t.toString());
            } finally {
                watchedPushes.remove(push.id());
            }
        });
    }

    private static String parsePushStatus(String dataJson) {
        return jsonField(dataJson, "status");
    }

    /**
     * Tails an SSE log endpoint (e.g. {@code /v1/deployments/:name/logs} or
     * {@code /v1/pushes/:id/logs}), pushing each line to {@code sink}. Runs until the stream ends
     * or {@code cancelled} returns true. Lines are best-effort: log/warning/status frames.
     */
    public void streamLogs(String path, LogSink sink, java.util.function.BooleanSupplier cancelled) {
        executor.submit(() -> {
            try {
                String token = validAccessToken();
                api().streamSse(token, path, (event, data) -> {
                    String line = switch (event) {
                        case "log" -> jsonField(data, "line");
                        case "warning" -> "[" + jsonField(data, "reason") + "]";
                        case "status" -> "== status: " + jsonField(data, "status") + " ==";
                        default -> null;
                    };
                    if (line != null) {
                        onClient(() -> sink.onLine(line));
                    }
                }, cancelled);
                onClient(() -> sink.onLine("== stream ended =="));
            } catch (Throwable t) {
                onClient(() -> sink.onLine("== error: " + t.getMessage() + " =="));
            }
        });
    }

    /** {@code GET /v1/cluster/nats} — broker stats, declared events, connections, JetStream. */
    public void fetchClusterNats(String projectId, Callback<Nats.Snapshot> cb) {
        executor.submit(() -> {
            try {
                Nats.Snapshot snap = withAuthRetry(token -> api().getClusterNats(token, projectId));
                onClient(() -> cb.onResult(snap));
            } catch (Throwable t) {
                onClient(() -> cb.onError(t));
            }
        });
    }

    /**
     * Tails the workspace NATS broker via {@code GET /v1/cluster/nats/tail} (SSE). Messages go to
     * {@code sink.onMessage}; ready/error/warning frames and stream end become {@code sink.onInfo} lines.
     * Runs until the stream ends or {@code cancelled} returns true.
     */
    public void streamNatsTail(String projectId, List<String> subjects, NatsTailSink sink,
                               java.util.function.BooleanSupplier cancelled) {
        executor.submit(() -> {
            try {
                StringBuilder path = new StringBuilder("/v1/cluster/nats/tail?projectId=")
                        .append(java.net.URLEncoder.encode(projectId, java.nio.charset.StandardCharsets.UTF_8));
                for (String s : subjects) {
                    path.append("&subject=")
                            .append(java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8));
                }
                String token = validAccessToken();
                api().streamSse(token, path.toString(), (event, data) -> {
                    switch (event) {
                        case "message" -> {
                            Nats.TailMessage m = parseTailMessage(data);
                            if (m != null) {
                                onClient(() -> sink.onMessage(m));
                            }
                        }
                        case "ready" -> onClient(() -> sink.onInfo("== streaming =="));
                        case "error" -> onClient(() -> sink.onInfo("== error: " + jsonField(data, "reason") + " =="));
                        case "warning" -> onClient(() -> sink.onInfo("== warning: " + jsonField(data, "reason") + " =="));
                        default -> { }
                    }
                }, cancelled);
                onClient(() -> sink.onInfo("== tail ended =="));
            } catch (Throwable t) {
                onClient(() -> sink.onInfo("== error: " + message(t) + " =="));
            }
        });
    }

    private static Nats.TailMessage parseTailMessage(String dataJson) {
        try {
            var o = JsonParser.parseString(dataJson).getAsJsonObject();
            String subject = o.has("subject") && !o.get("subject").isJsonNull() ? o.get("subject").getAsString() : "";
            String data = o.has("data") && !o.get("data").isJsonNull() ? o.get("data").getAsString() : "";
            long bytes = o.has("bytes") && !o.get("bytes").isJsonNull() ? o.get("bytes").getAsLong() : 0L;
            return new Nats.TailMessage(subject, data, bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonField(String dataJson, String key) {
        try {
            var o = JsonParser.parseString(dataJson).getAsJsonObject();
            return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
        } catch (Exception e) {
            return dataJson;
        }
    }

    // --- internals ----------------------------------------------------------

    private List<Project> listProjects(String token) throws Exception {
        return api().listProjects(token);
    }

    private void runLogin(LoginHandle handle, LoginListener listener) {
        try {
            Pkce pkce = Pkce.generate();
            KeycloakClient.DeviceCode code = keycloak.requestDeviceCode(pkce);
            onClient(() -> listener.onCode(code));

            long deadline = Instant.now().getEpochSecond() + code.expiresInSeconds();
            long intervalMs = Math.max(1, code.intervalSeconds()) * 1000L;

            while (!handle.isCancelled()) {
                if (Instant.now().getEpochSecond() >= deadline) {
                    onClient(() -> listener.onError("expired_token"));
                    return;
                }
                Thread.sleep(intervalMs);
                if (handle.isCancelled()) {
                    return;
                }
                KeycloakClient.PollResult result = keycloak.pollOnce(code.deviceCode(), pkce);
                if (result instanceof KeycloakClient.PollResult.Authorized authorized) {
                    Credentials c = authorized.credentials();
                    this.credentials = c;
                    TokenStore.save(c);
                    onClient(listener::onSuccess);
                    return;
                } else if (result instanceof KeycloakClient.PollResult.SlowDown) {
                    intervalMs += 5000L;
                } else if (result instanceof KeycloakClient.PollResult.Failed failed) {
                    onClient(() -> listener.onError(failed.error()));
                    return;
                }
                // Pending -> keep polling.
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Constants.LOG.warn("[grounds] device login failed", t);
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            onClient(() -> listener.onError(msg));
        }
    }

    private interface ApiCall<T> {
        T invoke(String accessToken) throws Exception;
    }

    /** Runs an API call with a valid token, refreshing once on a 401. */
    private <T> T withAuthRetry(ApiCall<T> call) throws Exception {
        String token = validAccessToken();
        try {
            return call.invoke(token);
        } catch (ForgeApiException e) {
            if (e.isUnauthorized()) {
                String refreshed = forceRefresh();
                if (refreshed != null) {
                    return call.invoke(refreshed);
                }
            }
            throw e;
        }
    }

    /** Returns a non-expired access token, refreshing proactively if near expiry. */
    private synchronized String validAccessToken() throws Exception {
        Credentials c = credentials;
        if (c == null || c.accessToken == null) {
            throw new IllegalStateException("not logged in");
        }
        if (c.accessTokenExpired(REFRESH_SKEW_SECONDS) && c.hasRefreshToken()) {
            return forceRefresh();
        }
        return c.accessToken;
    }

    /** Forces a refresh-token exchange; returns the new access token, or null if not possible. */
    private synchronized String forceRefresh() throws Exception {
        Credentials c = credentials;
        if (c == null || !c.hasRefreshToken()) {
            return null;
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
        this.credentials = refreshed;
        TokenStore.save(refreshed);
        return refreshed.accessToken;
    }

    private ForgeApiClient api() {
        return new ForgeApiClient(GroundsConfig.get().apiBaseUrl());
    }

    private static void onClient(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
