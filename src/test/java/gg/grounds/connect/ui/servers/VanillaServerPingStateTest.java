package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VanillaServerPingStateTest {
  @BeforeAll
  static void detectMinecraftVersion() {
    SharedConstants.tryDetectVersion();
  }

  @Test
  void startsPingWithVanillaEmptyStatusFields() {
    ServerData data = serverData();
    data.motd = Component.literal("Old MOTD");
    data.status = Component.literal("3/20");

    VanillaServerPingState.start(data);

    assertSame(ServerData.State.PINGING, data.state());
    assertSame(CommonComponents.EMPTY, data.motd);
    assertSame(CommonComponents.EMPTY, data.status);
  }

  @Test
  void completesPingFromProtocolCompatibility() {
    ServerData matching = serverData();
    matching.protocol = 123;
    ServerData older = serverData();
    older.protocol = 122;
    ServerData newer = serverData();
    newer.protocol = 124;

    VanillaServerPingState.complete(matching, 123);
    VanillaServerPingState.complete(older, 123);
    VanillaServerPingState.complete(newer, 123);

    assertSame(ServerData.State.SUCCESSFUL, matching.state());
    assertSame(ServerData.State.INCOMPATIBLE, older.state());
    assertSame(ServerData.State.INCOMPATIBLE, newer.state());
  }

  @Test
  void failsPingWithVanillaFailureMessage() {
    ServerData data = serverData();
    Component failure = Component.translatable("multiplayer.status.cannot_connect");

    VanillaServerPingState.fail(data, failure);

    assertSame(ServerData.State.UNREACHABLE, data.state());
    assertSame(failure, data.motd);
  }

  private static ServerData serverData() {
    return new ServerData("Test", "localhost", ServerData.Type.OTHER);
  }
}
