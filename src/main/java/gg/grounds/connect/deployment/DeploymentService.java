package gg.grounds.connect.deployment;

import gg.grounds.connect.api.ForgeApiException;
import gg.grounds.connect.api.Push;
import gg.grounds.connect.api.RollbackTarget;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;
import gg.grounds.connect.telemetry.SentryReporter;
import java.util.List;
import java.util.function.Consumer;

/** Build/deploy and rollback operations. */
public final class DeploymentService {
  private final ClientTaskRunner runner;
  private final AuthenticatedApi api;

  public DeploymentService(ClientTaskRunner runner, AuthenticatedApi api) {
    this.runner = runner;
    this.api = api;
  }

  public void fetchLatestPush(String name, String projectId, AsyncCallback<Push> cb) {
    runner.execute(
        () -> {
          try {
            Push push = api.withAuthRetry(token -> api.api().getLatestPush(token, projectId, name));
            runner.onClient(() -> cb.onResult(push));
          } catch (Throwable t) {
            SentryReporter.captureApiFailure(t, "deploy.fetch_latest_push", statusCode(t));
            runner.onClient(() -> cb.onError(t));
          }
        });
  }

  public void fetchRollbackTargets(
      String name, String projectId, AsyncCallback<List<RollbackTarget>> cb) {
    runner.execute(
        () -> {
          try {
            String app = api.withAuthRetry(token -> api.api().getAppName(token, projectId, name));
            List<RollbackTarget> targets =
                api.withAuthRetry(token -> api.api().listRollbackTargets(token, projectId, app));
            runner.onClient(() -> cb.onResult(targets));
          } catch (Throwable t) {
            SentryReporter.captureApiFailure(t, "deploy.fetch_rollback_targets", statusCode(t));
            runner.onClient(() -> cb.onError(t));
          }
        });
  }

  public void rollback(
      String name, String projectId, String pushId, Runnable onDone, Consumer<String> onError) {
    runner.execute(
        () -> {
          try {
            api.withAuthRetry(
                token -> {
                  api.api().rollback(token, name, projectId, pushId);
                  return Boolean.TRUE;
                });
            runner.onClient(onDone);
          } catch (ForgeApiException e) {
            SentryReporter.captureApiFailure(e, "deploy.rollback", e.statusCode());
            String m = e.statusCode() == 403 ? "owner or editor role required" : e.getMessage();
            runner.onClient(() -> onError.accept(m));
          } catch (Throwable t) {
            SentryReporter.captureApiFailure(t, "deploy.rollback", statusCode(t));
            runner.onClient(() -> onError.accept(message(t)));
          }
        });
  }

  /** Retries the deployment's latest push if it failed. */
  public void retryLatestBuild(
      String name, String projectId, Runnable onStarted, Consumer<String> onError) {
    runner.execute(
        () -> {
          try {
            Push latest =
                api.withAuthRetry(token -> api.api().getLatestPush(token, projectId, name));
            if (latest == null) {
              runner.onClient(() -> onError.accept("no build found"));
              return;
            }
            String st = latest.status();
            if (!"build_failed".equals(st) && !"deploy_failed".equals(st)) {
              runner.onClient(
                  () -> onError.accept("latest build is '" + st + "' — nothing to retry"));
              return;
            }
            api.withAuthRetry(
                token -> {
                  api.api().retryPush(token, latest.id(), projectId);
                  return Boolean.TRUE;
                });
            runner.onClient(onStarted);
          } catch (Throwable t) {
            SentryReporter.captureApiFailure(t, "deploy.retry_latest_build", statusCode(t));
            runner.onClient(() -> onError.accept(message(t)));
          }
        });
  }

  private static String message(Throwable t) {
    return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
  }

  private static Integer statusCode(Throwable t) {
    return t instanceof ForgeApiException e ? e.statusCode() : null;
  }
}
