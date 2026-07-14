package gg.grounds.connect.ui.servers;

import java.util.List;
import net.minecraft.client.multiplayer.ServerData;

final class ServerScreenLifecycle {
  private boolean pingsInterrupted;

  boolean acceptsServerLoad(String currentProjectId, String responseProjectId) {
    return currentProjectId != null && currentProjectId.equals(responseProjectId);
  }

  void onRemoved() {
    pingsInterrupted = true;
  }

  List<ServerEntry> onInitialized(ServerContentState contentState, List<ServerEntry> entries) {
    if (!pingsInterrupted) {
      return List.of();
    }
    pingsInterrupted = false;
    if (contentState != ServerContentState.CONTENT) {
      return List.of();
    }
    return entries.stream().filter(ServerScreenLifecycle::needsPing).toList();
  }

  private static boolean needsPing(ServerEntry entry) {
    ServerData.State state = entry.data.state();
    return state == ServerData.State.INITIAL || state == ServerData.State.PINGING;
  }
}
