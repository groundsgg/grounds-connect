package gg.grounds.connect.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks account-scoped async work so logout can cancel stale callbacks and streams. */
public final class SessionLifecycle {
  private final Set<Lease> leases = ConcurrentHashMap.newKeySet();

  public Lease openLease() {
    Lease lease = new Lease();
    leases.add(lease);
    return lease;
  }

  public void reset() {
    for (Lease lease : leases) {
      lease.cancel();
    }
    leases.clear();
  }

  public final class Lease implements AutoCloseable {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Set<AutoCloseable> resources = ConcurrentHashMap.newKeySet();

    private Lease() {}

    public boolean isCancelled() {
      return cancelled.get();
    }

    public void closeOnCancel(AutoCloseable resource) {
      if (resource == null) {
        return;
      }
      if (isCancelled()) {
        closeQuietly(resource);
        return;
      }
      resources.add(resource);
      if (isCancelled() && resources.remove(resource)) {
        closeQuietly(resource);
      }
    }

    private void cancel() {
      if (cancelled.compareAndSet(false, true)) {
        closeResources();
      }
    }

    @Override
    public void close() {
      leases.remove(this);
      closeResources();
    }

    private void closeResources() {
      for (AutoCloseable resource : resources) {
        closeQuietly(resource);
      }
      resources.clear();
    }
  }

  private static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Best-effort cancellation cleanup.
    }
  }
}
