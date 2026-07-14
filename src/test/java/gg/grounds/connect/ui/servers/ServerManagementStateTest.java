package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.grounds.connect.core.RequestCoalescer;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ServerManagementStateTest {
  @Test
  void ordersLogsRetryRollbackAndBack() {
    assertEquals(
        List.of(
            ServerManagementState.Action.LOGS,
            ServerManagementState.Action.RETRY_BUILD,
            ServerManagementState.Action.ROLLBACK,
            ServerManagementState.Action.BACK),
        ServerManagementState.actionOrder());
  }

  @Test
  void permitsRollbackForOwnerAndEditorOnly() {
    assertTrue(ServerManagementState.canRollback("owner"));
    assertTrue(ServerManagementState.canRollback("EDITOR"));
    assertFalse(ServerManagementState.canRollback("viewer"));
    assertFalse(ServerManagementState.canRollback(null));
  }

  @Test
  void rejectsDuplicateRetryAcrossRecreatedStateUntilTheCallbackCompletes() {
    RequestCoalescer retries = new RequestCoalescer();
    ServerManagementState first =
        new ServerManagementState("owner", retries, "project-a", "server-a");

    assertTrue(first.beginRetry());

    ServerManagementState recreated =
        new ServerManagementState("owner", retries, "project-a", "server-a");
    assertFalse(recreated.beginRetry());
    assertFalse(recreated.retryActive());

    first.finishRetry();

    assertTrue(recreated.retryActive());
    assertTrue(recreated.beginRetry());
  }
}
