package gg.grounds.connect.core;

import gg.grounds.connect.telemetry.SentryReporter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

/** Runs network work off-thread and marshals callbacks back to the client thread. */
public final class ClientTaskRunner {
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "grounds-net");
            t.setDaemon(true);
            return t;
          });
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "grounds-watch");
            t.setDaemon(true);
            return t;
          });

  public void execute(Runnable task) {
    executor.execute(wrap(task, "background.execute"));
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable task, long initialDelay, long delay, TimeUnit unit) {
    return scheduler.scheduleWithFixedDelay(
        wrap(task, "background.schedule"), initialDelay, delay, unit);
  }

  public void onClient(Runnable task) {
    Minecraft.getInstance().execute(task);
  }

  private static Runnable wrap(Runnable task, String operation) {
    return () -> {
      try {
        task.run();
      } catch (Throwable t) {
        SentryReporter.captureHandled(t, operation, "uncaught_background_task");
        throw t;
      }
    };
  }
}
