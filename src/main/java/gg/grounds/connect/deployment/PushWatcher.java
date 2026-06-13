package gg.grounds.connect.deployment;

import com.google.gson.JsonParser;
import gg.grounds.connect.Constants;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.api.Push;
import gg.grounds.connect.auth.AuthService;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;
import gg.grounds.connect.project.ProjectService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Background watcher for in-flight build/deploy pushes. */
public final class PushWatcher {
  private final ClientTaskRunner runner;
  private final AuthenticatedApi api;
  private final AuthService auth;
  private final ProjectService projects;
  private final Set<String> watchedPushes = ConcurrentHashMap.newKeySet();
  private volatile boolean started;

  public PushWatcher(
      ClientTaskRunner runner, AuthenticatedApi api, AuthService auth, ProjectService projects) {
    this.runner = runner;
    this.api = api;
    this.auth = auth;
    this.projects = projects;
  }

  /** Background poller: shows build/deploy toasts even when the Grounds screen is closed. */
  public void start(PushStatusSink sink) {
    if (started) {
      return;
    }
    started = true;
    runner.scheduleWithFixedDelay(
        () -> {
          try {
            if (!auth.isLoggedIn()) {
              return;
            }
            if (projects.cachedProjects().isEmpty()) {
              projects.refreshNow();
            }
            Project selected = projects.selectedProject();
            if (selected != null) {
              watchInFlightPushes(selected.id(), sink);
            }
          } catch (Throwable ignored) {
            // transient; try again next tick
          }
        },
        10,
        15,
        TimeUnit.SECONDS);
  }

  /**
   * Finds in-flight pushes in the project and streams each one's status to {@code sink}.
   * Already-watched pushes are skipped, so this is safe to call repeatedly.
   */
  public void watchInFlightPushes(String projectId, PushStatusSink sink) {
    runner.execute(
        () -> {
          try {
            List<Push> inflight =
                api.withAuthRetry(token -> api.api().listInFlightPushes(token, projectId));
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
    runner.execute(
        () -> {
          try {
            runner.onClient(() -> sink.onStatus(push.id(), push.appName(), push.status()));
            String token = api.withAuthRetry(t -> t);
            api.api()
                .streamSse(
                    token,
                    "/v1/pushes/" + push.id() + "/logs",
                    (event, data) -> {
                      if ("status".equals(event)) {
                        String st = parsePushStatus(data);
                        if (st != null && !st.isBlank()) {
                          runner.onClient(() -> sink.onStatus(push.id(), push.appName(), st));
                        }
                      }
                    },
                    () -> false);
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

  private static String jsonField(String dataJson, String key) {
    try {
      var o = JsonParser.parseString(dataJson).getAsJsonObject();
      return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    } catch (Exception e) {
      return dataJson;
    }
  }
}
