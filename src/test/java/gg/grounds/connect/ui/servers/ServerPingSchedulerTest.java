package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ServerPingSchedulerTest {
  @Test
  void submitsPingWithoutRunningItOnTheCallingThread() {
    AtomicReference<Runnable> queuedTask = new AtomicReference<>();
    AtomicBoolean pingStarted = new AtomicBoolean();
    ServerPingScheduler scheduler = new ServerPingScheduler(queuedTask::set);

    scheduler.submit(() -> pingStarted.set(true));

    assertFalse(pingStarted.get());
    assertNotNull(queuedTask.get());

    queuedTask.get().run();

    assertTrue(pingStarted.get());
  }
}
