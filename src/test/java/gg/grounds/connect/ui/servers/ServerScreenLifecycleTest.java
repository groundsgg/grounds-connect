package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ServerScreenLifecycleTest {
  @BeforeAll
  static void detectMinecraftVersion() {
    SharedConstants.tryDetectVersion();
  }

  @Test
  void completesCurrentProjectLoadDuringNatsRoundTripAndPingsItOnReturn() {
    ServerScreenLifecycle lifecycle = new ServerScreenLifecycle();
    ServerEntry loaded = entry("loaded", ServerData.State.INITIAL);

    lifecycle.onRemoved();

    assertTrue(lifecycle.acceptsServerLoad("project-a", "project-a"));
    assertFalse(lifecycle.acceptsServerLoad("project-a", "project-b"));
    assertEquals(
        List.of(loaded), lifecycle.onInitialized(ServerContentState.CONTENT, List.of(loaded)));
  }

  @Test
  void restartsInterruptedPingOnceAfterChildScreenRoundTrip() {
    ServerScreenLifecycle lifecycle = new ServerScreenLifecycle();
    ServerEntry pending = entry("pending", ServerData.State.PINGING);
    ServerEntry complete = entry("complete", ServerData.State.SUCCESSFUL);

    lifecycle.onRemoved();

    assertEquals(
        List.of(pending),
        lifecycle.onInitialized(ServerContentState.CONTENT, List.of(pending, complete)));
    assertTrue(lifecycle.onInitialized(ServerContentState.CONTENT, List.of(pending)).isEmpty());
  }

  private static ServerEntry entry(String name, ServerData.State state) {
    ServerData data = new ServerData(name, "localhost", ServerData.Type.OTHER);
    data.setState(state);
    return new ServerEntry(name, "localhost", data, "ready", "paper");
  }
}
