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
  private boolean retrying;

  ServerManagementState(String projectRole) {
    rollbackActive = canRollback(projectRole);
  }

  boolean beginRetry() {
    if (retrying) {
      return false;
    }
    retrying = true;
    return true;
  }

  void finishRetry() {
    retrying = false;
  }

  boolean retryActive() {
    return !retrying;
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
