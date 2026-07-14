package gg.grounds.connect.ui.servers;

enum ServerContentState {
  LOADING(false, true, false),
  CONTENT(true, false, true),
  UNAVAILABLE(false, false, false);

  private final boolean listVisible;
  private final boolean loaderVisible;
  private final boolean serverActionsActive;

  ServerContentState(boolean listVisible, boolean loaderVisible, boolean serverActionsActive) {
    this.listVisible = listVisible;
    this.loaderVisible = loaderVisible;
    this.serverActionsActive = serverActionsActive;
  }

  boolean listVisible() {
    return listVisible;
  }

  boolean loaderVisible() {
    return loaderVisible;
  }

  boolean serverActionsActive() {
    return serverActionsActive;
  }

  static ServerContentState afterServerLoad(int serverCount) {
    return serverCount > 0 ? CONTENT : UNAVAILABLE;
  }
}
