package gg.grounds.connect.ui.servers;

import java.util.List;

record ServerScreenControls(boolean joinActive, boolean manageActive) {
  enum BottomAction {
    JOIN,
    MANAGE,
    BACK
  }

  private static final List<BottomAction> BOTTOM_ORDER =
      List.of(BottomAction.JOIN, BottomAction.MANAGE, BottomAction.BACK);

  static ServerScreenControls forState(ServerContentState state, boolean hasSelectedServer) {
    boolean selectedServerActionsActive = state.serverActionsActive() && hasSelectedServer;
    return new ServerScreenControls(selectedServerActionsActive, selectedServerActionsActive);
  }

  static List<BottomAction> bottomOrder() {
    return BOTTOM_ORDER;
  }
}
