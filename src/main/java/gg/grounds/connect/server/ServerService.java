package gg.grounds.connect.server;

import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** Server list, runtime polling, and join-address tracking. */
public final class ServerService {
    private final ClientTaskRunner runner;
    private final AuthenticatedApi api;
    private volatile String lastJoinedGroundsAddress;

    public ServerService(ClientTaskRunner runner, AuthenticatedApi api) {
        this.runner = runner;
        this.api = api;
    }

    public void fetchServers(String projectId, AsyncCallback<List<GroundsServer>> cb) {
        runner.execute(() -> {
            try {
                List<GroundsServer> servers = api.withAuthRetry(token -> api.api().listMinecraftServers(token, projectId));
                runner.onClient(() -> cb.onResult(servers));
            } catch (Throwable t) {
                runner.onClient(() -> cb.onError(t));
            }
        });
    }

    public void fetchRuntime(String name, String projectId, AsyncCallback<DeploymentRuntime> cb) {
        runner.execute(() -> {
            try {
                DeploymentRuntime rt = api.withAuthRetry(token -> api.api().getRuntime(token, name, projectId));
                runner.onClient(() -> cb.onResult(rt));
            } catch (Throwable t) {
                runner.onClient(() -> cb.onError(t));
            }
        });
    }

    /** Resumes a paused deployment, then polls runtime until ready or ~90s before trying anyway. */
    public void resumeAndAwait(String name, String projectId, Consumer<String> onProgress,
                               Runnable onReady, Consumer<String> onError) {
        runner.execute(() -> {
            try {
                api.withAuthRetry(token -> {
                    api.api().resume(token, name, projectId);
                    return Boolean.TRUE;
                });
                long deadline = Instant.now().getEpochSecond() + 90;
                while (Instant.now().getEpochSecond() < deadline) {
                    DeploymentRuntime rt = api.withAuthRetry(token -> api.api().getRuntime(token, name, projectId));
                    runner.onClient(() -> onProgress.accept(rt.replicasReady() + "/" + rt.replicasDesired()));
                    if (rt.replicasDesired() > 0 && rt.replicasReady() >= rt.replicasDesired()) {
                        runner.onClient(onReady);
                        return;
                    }
                    Thread.sleep(2000);
                }
                runner.onClient(onReady);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                runner.onClient(() -> onError.accept(message(t)));
            }
        });
    }

    /** Remember that we're connecting to a Grounds server, so leaving it returns to our screen. */
    public void markGroundsJoin(String address) {
        this.lastJoinedGroundsAddress = address;
    }

    public boolean isGroundsAddress(String address) {
        String a = lastJoinedGroundsAddress;
        return a != null && a.equalsIgnoreCase(address);
    }

    private static String message(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
