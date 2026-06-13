package gg.grounds.connect.core;

import java.util.function.Consumer;

/** Unauthenticated platform-level probes. */
public final class PlatformService {
  private final ClientTaskRunner runner;
  private final AuthenticatedApi api;

  public PlatformService(ClientTaskRunner runner, AuthenticatedApi api) {
    this.runner = runner;
    this.api = api;
  }

  public void pollReadiness(Consumer<Boolean> sink) {
    runner.execute(
        () -> {
          boolean ready = api.api().isReady();
          runner.onClient(() -> sink.accept(ready));
        });
  }
}
