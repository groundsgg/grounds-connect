package gg.grounds.connect.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class SessionLifecycleTest {
  @Test
  void resetCancelsOpenLeases() {
    SessionLifecycle lifecycle = new SessionLifecycle();
    SessionLifecycle.Lease lease = lifecycle.openLease();

    assertFalse(lease.isCancelled());

    lifecycle.reset();

    assertTrue(lease.isCancelled());
  }

  @Test
  void closedLeaseDoesNotCancelLaterGenerations() {
    SessionLifecycle lifecycle = new SessionLifecycle();
    SessionLifecycle.Lease lease = lifecycle.openLease();

    lease.close();
    lifecycle.reset();

    assertFalse(lease.isCancelled());
    assertFalse(lifecycle.openLease().isCancelled());
  }

  @Test
  void resetClosesRegisteredResources() throws Exception {
    SessionLifecycle lifecycle = new SessionLifecycle();
    SessionLifecycle.Lease lease = lifecycle.openLease();
    AtomicBoolean closed = new AtomicBoolean();

    lease.closeOnCancel(() -> closed.set(true));

    lifecycle.reset();

    assertTrue(closed.get());
  }
}
