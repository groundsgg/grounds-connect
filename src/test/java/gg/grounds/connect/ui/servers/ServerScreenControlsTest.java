package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ServerScreenControlsTest {
  @Test
  void ordersJoinBeforeManageAndBack() {
    assertEquals(
        List.of(
            ServerScreenControls.BottomAction.JOIN,
            ServerScreenControls.BottomAction.MANAGE,
            ServerScreenControls.BottomAction.BACK),
        ServerScreenControls.bottomOrder());
  }

  @Test
  void enablesSelectedServerActionsOnlyForContentWithSelection() {
    assertFalse(ServerScreenControls.forState(ServerContentState.LOADING, true).joinActive());
    assertFalse(ServerScreenControls.forState(ServerContentState.UNAVAILABLE, true).manageActive());
    assertFalse(ServerScreenControls.forState(ServerContentState.CONTENT, false).joinActive());

    ServerScreenControls controls = ServerScreenControls.forState(ServerContentState.CONTENT, true);
    assertTrue(controls.joinActive());
    assertTrue(controls.manageActive());
  }
}
