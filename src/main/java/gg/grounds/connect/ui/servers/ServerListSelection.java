package gg.grounds.connect.ui.servers;

import java.util.List;

final class ServerListSelection {
  private String selectedName;

  void remember(ServerEntry entry) {
    selectedName = entry == null ? null : entry.name;
  }

  void clear() {
    selectedName = null;
  }

  String selectedName() {
    return selectedName;
  }

  ServerEntry restore(List<ServerEntry> visibleEntries) {
    if (selectedName == null) {
      return null;
    }
    for (ServerEntry entry : visibleEntries) {
      if (selectedName.equals(entry.name)) {
        return entry;
      }
    }
    clear();
    return null;
  }
}
