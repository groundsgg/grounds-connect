package gg.grounds.connect.ui.servers;

import java.util.List;

final class ServerManagementState {
  enum Action {
    LOGS,
    RETRY_BUILD,
    ROLLBACK,
    BACK
  }

  private static final List<Action> ACTION_ORDER =
      List.of(Action.LOGS, Action.RETRY_BUILD, Action.ROLLBACK, Action.BACK);
  private final boolean rollbackActive;
  private final ServerRetryRegistry retries;
  private final String projectId;
  private final String serverName;

  ServerManagementState(
      String projectRole, ServerRetryRegistry retries, String projectId, String serverName) {
    rollbackActive = canRollback(projectRole);
    this.retries = retries;
    this.projectId = projectId;
    this.serverName = serverName;
  }

  boolean beginRetry() {
    return retries.begin(projectId, serverName);
  }

  void finishRetrySuccessfully() {
    retries.finishSuccessfully(projectId, serverName);
  }

  void finishRetryWithError(String error) {
    retries.finishWithError(projectId, serverName, error);
  }

  boolean retryActive() {
    return retrySnapshot().status() != ServerRetryRegistry.Status.PENDING;
  }

  ServerRetryRegistry.Snapshot retrySnapshot() {
    return retries.snapshot(projectId, serverName);
  }

  boolean rollbackActive() {
    return rollbackActive;
  }

  static List<Action> actionOrder() {
    return ACTION_ORDER;
  }

  static boolean canRollback(String role) {
    return "owner".equalsIgnoreCase(role) || "editor".equalsIgnoreCase(role);
  }
}
