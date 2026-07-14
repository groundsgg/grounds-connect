package gg.grounds.connect.ui.servers;

import gg.grounds.connect.core.RequestCoalescer;
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
  private final RequestCoalescer retries;
  private final String projectId;
  private final String serverName;

  ServerManagementState(
      String projectRole, RequestCoalescer retries, String projectId, String serverName) {
    rollbackActive = canRollback(projectRole);
    this.retries = retries;
    this.projectId = projectId;
    this.serverName = serverName;
  }

  boolean beginRetry() {
    return retries.begin(projectId, serverName);
  }

  void finishRetry() {
    retries.finish(projectId, serverName);
  }

  boolean retryActive() {
    return !retries.isInFlight(projectId, serverName);
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
