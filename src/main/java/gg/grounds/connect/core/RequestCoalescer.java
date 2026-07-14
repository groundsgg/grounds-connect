package gg.grounds.connect.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Suppresses duplicate in-flight requests for the same logical resource. */
public final class RequestCoalescer {
  private final Set<Key> inFlight = ConcurrentHashMap.newKeySet();

  public boolean begin(String scope, String name) {
    return inFlight.add(new Key(scope, name));
  }

  public void finish(String scope, String name) {
    inFlight.remove(new Key(scope, name));
  }

  private record Key(String scope, String name) {}
}
