package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VanillaServerStatusRendererTest {
  @BeforeAll
  static void detectMinecraftVersion() {
    SharedConstants.tryDetectVersion();
  }

  @Test
  void mapsSuccessfulLatencyToVanillaSignalStrength() {
    long[] pings = {149, 150, 299, 300, 599, 600, 999, 1000};
    String[] sprites = {
      "server_list/ping_5",
      "server_list/ping_4",
      "server_list/ping_4",
      "server_list/ping_3",
      "server_list/ping_3",
      "server_list/ping_2",
      "server_list/ping_2",
      "server_list/ping_1"
    };

    for (int i = 0; i < pings.length; i++) {
      ServerData data = serverData(ServerData.State.SUCCESSFUL);
      data.ping = pings[i];

      VanillaServerStatusRenderer.Display display = VanillaServerStatusRenderer.display(data, 0, 0);

      assertEquals(sprites[i], display.icon().getPath());
      assertEquals(
          Component.translatable("multiplayer.status.ping", pings[i]), display.pingTooltip());
    }
  }

  @Test
  void followsVanillaTriangularPingingAnimation() {
    String[] sprites = {
      "server_list/pinging_1",
      "server_list/pinging_2",
      "server_list/pinging_3",
      "server_list/pinging_4",
      "server_list/pinging_5",
      "server_list/pinging_4",
      "server_list/pinging_3",
      "server_list/pinging_2"
    };

    for (int step = 0; step < sprites.length; step++) {
      VanillaServerStatusRenderer.Display display =
          VanillaServerStatusRenderer.display(serverData(ServerData.State.PINGING), step * 100L, 0);

      assertEquals(sprites[step], display.icon().getPath());
    }
  }

  @Test
  void offsetsPingingAnimationByVisibleRowIndex() {
    VanillaServerStatusRenderer.Display display =
        VanillaServerStatusRenderer.display(serverData(ServerData.State.PINGING), 0, 1);

    assertEquals("server_list/pinging_3", display.icon().getPath());
  }

  @Test
  void mapsUnreachableAndIncompatibleStates() {
    assertEquals(
        "server_list/unreachable",
        VanillaServerStatusRenderer.display(serverData(ServerData.State.UNREACHABLE), 0, 0)
            .icon()
            .getPath());
    assertEquals(
        Component.translatable("multiplayer.status.no_connection"),
        VanillaServerStatusRenderer.display(serverData(ServerData.State.UNREACHABLE), 0, 0)
            .pingTooltip());
    assertEquals(
        "server_list/incompatible",
        VanillaServerStatusRenderer.display(serverData(ServerData.State.INCOMPATIBLE), 0, 0)
            .icon()
            .getPath());
    assertEquals(
        Component.translatable("multiplayer.status.incompatible"),
        VanillaServerStatusRenderer.display(serverData(ServerData.State.INCOMPATIBLE), 0, 0)
            .pingTooltip());
  }

  @Test
  void presentsIncompatibleVersionAndPropagatesPlayerTooltip() {
    ServerData data = serverData(ServerData.State.INCOMPATIBLE);
    data.version = Component.literal("26.1");
    data.playerList = List.of(Component.literal("Alex"), Component.literal("and 2 more"));

    VanillaServerStatusRenderer.Display display = VanillaServerStatusRenderer.display(data, 0, 0);

    assertEquals("26.1", display.statusText().getString());
    assertEquals(
        TextColor.fromLegacyFormat(ChatFormatting.RED), display.statusText().getStyle().getColor());
    assertEquals(data.playerList, display.playerTooltip());
  }

  @Test
  void presentsPlayerCountAndExactPingTooltipForSuccessfulServer() {
    ServerData data = serverData(ServerData.State.SUCCESSFUL);
    data.status = Component.literal("3/20");
    data.ping = 42;
    data.playerList = List.of(Component.literal("Alex"));

    VanillaServerStatusRenderer.Display display = VanillaServerStatusRenderer.display(data, 0, 0);

    assertSame(data.status, display.statusText());
    assertEquals(data.playerList, display.playerTooltip());
    assertEquals(Component.translatable("multiplayer.status.ping", 42L), display.pingTooltip());
  }

  private static ServerData serverData(ServerData.State state) {
    ServerData data = new ServerData("Test", "localhost", ServerData.Type.OTHER);
    data.setState(state);
    return data;
  }
}
