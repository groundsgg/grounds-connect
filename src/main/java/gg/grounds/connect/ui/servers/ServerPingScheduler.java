package gg.grounds.connect.ui.servers;

import java.util.concurrent.Executor;

/** Keeps blocking server-address resolution away from the Minecraft client thread. */
final class ServerPingScheduler {
  private final Executor executor;

  ServerPingScheduler(Executor executor) {
    this.executor = executor;
  }

  void submit(Runnable ping) {
    executor.execute(ping);
  }
}
