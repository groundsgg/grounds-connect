package gg.grounds.connect.ui.servers;

import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.GroundsServices;
import gg.grounds.connect.ui.DeployToasts;
import java.util.List;

final class ServerDataLoader {
  interface Listener {
    void onProjectsLoaded(List<Project> projects);

    void onProjectsError(Throwable error);

    boolean onServersLoaded(String projectId, List<GroundsServer> servers);

    void onServersError(String projectId, Throwable error);

    List<ServerEntry> runtimeEntries(String projectId);

    void onRuntimeResult(String projectId, ServerEntry entry, DeploymentRuntime runtime);

    void onRuntimeError(String projectId, ServerEntry entry, Throwable error);

    void onPlatformReadiness(boolean ready);
  }

  private final GroundsServices services;
  private final Listener listener;

  ServerDataLoader(GroundsServices services, Listener listener) {
    this.services = services;
    this.listener = listener;
  }

  void loadProjects() {
    services
        .projects()
        .fetch(
            new AsyncCallback<>() {
              @Override
              public void onResult(List<Project> value) {
                listener.onProjectsLoaded(value);
              }

              @Override
              public void onError(Throwable error) {
                listener.onProjectsError(error);
              }
            });
  }

  void loadServers(String projectId) {
    services
        .servers()
        .fetchServers(
            projectId,
            new AsyncCallback<>() {
              @Override
              public void onResult(List<GroundsServer> value) {
                if (listener.onServersLoaded(projectId, value)) {
                  refreshRuntimes(projectId, listener.runtimeEntries(projectId));
                }
              }

              @Override
              public void onError(Throwable error) {
                listener.onServersError(projectId, error);
              }
            });
  }

  void refreshRuntimes(String projectId, List<ServerEntry> entries) {
    if (projectId == null) {
      return;
    }
    for (ServerEntry entry : entries) {
      services
          .servers()
          .fetchRuntime(
              entry.name,
              projectId,
              new AsyncCallback<>() {
                @Override
                public void onResult(DeploymentRuntime rt) {
                  listener.onRuntimeResult(projectId, entry, rt);
                }

                @Override
                public void onError(Throwable error) {
                  listener.onRuntimeError(projectId, entry, error);
                }
              });
    }
    services.pushes().watchInFlightPushes(projectId, DeployToasts::onStatus);
  }

  void pollPlatformReadiness() {
    AsyncCallback<Boolean> callback =
        new AsyncCallback<>() {
          @Override
          public void onResult(Boolean value) {
            listener.onPlatformReadiness(value);
          }

          @Override
          public void onError(Throwable error) {
            // PlatformService does not currently surface readiness probe errors separately.
          }
        };
    services.platform().pollReadiness(callback::onResult);
  }
}
