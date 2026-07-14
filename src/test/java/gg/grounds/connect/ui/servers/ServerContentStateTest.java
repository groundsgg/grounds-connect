package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerContentStateTest {

  @Test
  void loadingShowsOnlyLoader() {
    assertFalse(ServerContentState.LOADING.listVisible());
    assertTrue(ServerContentState.LOADING.loaderVisible());
    assertFalse(ServerContentState.LOADING.serverActionsActive());
  }

  @Test
  void contentShowsListAndEnablesServerActions() {
    assertTrue(ServerContentState.CONTENT.listVisible());
    assertFalse(ServerContentState.CONTENT.loaderVisible());
    assertTrue(ServerContentState.CONTENT.serverActionsActive());
  }

  @Test
  void unavailableShowsNeitherContentNorLoader() {
    assertFalse(ServerContentState.UNAVAILABLE.listVisible());
    assertFalse(ServerContentState.UNAVAILABLE.loaderVisible());
    assertFalse(ServerContentState.UNAVAILABLE.serverActionsActive());
  }

  @Test
  void completedLoadChoosesStateFromServerCount() {
    assertSame(ServerContentState.UNAVAILABLE, ServerContentState.afterServerLoad(0));
    assertSame(ServerContentState.CONTENT, ServerContentState.afterServerLoad(1));
  }
}
