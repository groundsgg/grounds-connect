package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ServerListSelectionTest {
  @BeforeAll
  static void detectMinecraftVersion() {
    SharedConstants.tryDetectVersion();
  }

  @Test
  void restoresASelectedVisibleServerAfterScreenRebuild() {
    ServerEntry lobby = entry("lobby");
    ServerEntry survival = entry("survival");
    ServerListSelection selection = new ServerListSelection();

    selection.remember(survival);

    assertSame(survival, selection.restore(List.of(lobby, survival)));
    assertEquals("survival", selection.selectedName());
  }

  @Test
  void clearsSelectionWhenTheServerIsNoLongerVisible() {
    ServerListSelection selection = new ServerListSelection();
    selection.remember(entry("survival"));

    assertNull(selection.restore(List.of(entry("lobby"))));
    assertNull(selection.selectedName());
  }

  private static ServerEntry entry(String name) {
    return new ServerEntry(
        name,
        "localhost",
        new ServerData(name, "localhost", ServerData.Type.OTHER),
        "ready",
        "paper");
  }
}
