package gg.grounds.connect.core;

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
    executor.execute(task);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable task, long initialDelay, long delay, TimeUnit unit) {
    return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
  }

  public void onClient(Runnable task) {
    Minecraft.getInstance().execute(task);
  }
}
