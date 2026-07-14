package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void recreatedConsumerObservesSuccessfulRetryCompletion() {
    ServerRetryRegistry retries = new ServerRetryRegistry();
    ServerManagementState first =
        new ServerManagementState("owner", retries, "project-a", "server-a");

    assertTrue(first.beginRetry());

    ServerManagementState recreated =
        new ServerManagementState("owner", retries, "project-a", "server-a");
    assertFalse(recreated.beginRetry());
    assertFalse(recreated.retryActive());
    assertEquals(ServerRetryRegistry.Status.PENDING, recreated.retrySnapshot().status());

    first.finishRetrySuccessfully();

    assertTrue(recreated.retryActive());
    assertEquals(ServerRetryRegistry.Status.SUCCESS, recreated.retrySnapshot().status());
  }

  @Test
  void recreatedConsumerObservesRetryErrorCompletion() {
    ServerRetryRegistry retries = new ServerRetryRegistry();
    ServerManagementState first =
        new ServerManagementState("owner", retries, "project-a", "server-a");
    assertTrue(first.beginRetry());
    ServerManagementState recreated =
        new ServerManagementState("owner", retries, "project-a", "server-a");

    first.finishRetryWithError("build unavailable");

    assertTrue(recreated.retryActive());
    assertEquals(ServerRetryRegistry.Status.ERROR, recreated.retrySnapshot().status());
    assertEquals("build unavailable", recreated.retrySnapshot().error());
  }
}
