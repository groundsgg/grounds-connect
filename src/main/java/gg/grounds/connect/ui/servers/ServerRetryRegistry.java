package gg.grounds.connect.ui.servers;

import java.util.HashMap;
import java.util.Map;

final class ServerRetryRegistry {
  enum Status {
    IDLE,
    PENDING,
    SUCCESS,
    ERROR
  }

  record Snapshot(Status status, String error) {
    private static final Snapshot IDLE = new Snapshot(Status.IDLE, null);
    private static final Snapshot PENDING = new Snapshot(Status.PENDING, null);
    private static final Snapshot SUCCESS = new Snapshot(Status.SUCCESS, null);
  }

  private final Map<Key, Snapshot> snapshots = new HashMap<>();

  synchronized boolean begin(String projectId, String serverName) {
    Key key = new Key(projectId, serverName);
    if (snapshots.getOrDefault(key, Snapshot.IDLE).status() == Status.PENDING) {
      return false;
    }
    snapshots.put(key, Snapshot.PENDING);
    return true;
  }

  synchronized void finishSuccessfully(String projectId, String serverName) {
    snapshots.put(new Key(projectId, serverName), Snapshot.SUCCESS);
  }

  synchronized void finishWithError(String projectId, String serverName, String error) {
    snapshots.put(new Key(projectId, serverName), new Snapshot(Status.ERROR, error));
  }

  synchronized Snapshot snapshot(String projectId, String serverName) {
    return snapshots.getOrDefault(new Key(projectId, serverName), Snapshot.IDLE);
  }

  private record Key(String projectId, String serverName) {}
}
