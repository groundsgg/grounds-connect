package gg.grounds.connect.ui.servers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class VanillaServerStatusRenderer {
  private static final int ICON_WIDTH = 10;
  private static final int ICON_HEIGHT = 8;
  private static final int STATUS_GAP = 5;
  private static final int STATUS_COLOR = 0xFF808080;
  private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

  private VanillaServerStatusRenderer() {}

  static Display display(ServerData data, long nowMillis, int rowIndex) {
    Component statusText = data.status == null ? CommonComponents.EMPTY : data.status;
    List<Component> playerTooltip = List.of();
    Identifier icon;
    Component pingTooltip;

    switch (data.state()) {
      case INITIAL -> {
        icon = sprite("ping_1");
        pingTooltip = Component.translatable("multiplayer.status.pinging");
      }
      case PINGING -> {
        icon = pingingSprite(nowMillis, rowIndex);
        pingTooltip = Component.translatable("multiplayer.status.pinging");
      }
      case UNREACHABLE -> {
        icon = sprite("unreachable");
        pingTooltip = Component.translatable("multiplayer.status.no_connection");
      }
      case INCOMPATIBLE -> {
        icon = sprite("incompatible");
        pingTooltip = Component.translatable("multiplayer.status.incompatible");
        statusText =
            (data.version == null ? CommonComponents.EMPTY : data.version)
                .copy()
                .withStyle(ChatFormatting.RED);
        playerTooltip = copyPlayerTooltip(data.playerList);
      }
      case SUCCESSFUL -> {
        icon = successfulSprite(data.ping);
        pingTooltip = Component.translatable("multiplayer.status.ping", data.ping);
        playerTooltip = copyPlayerTooltip(data.playerList);
      }
      default -> throw new IllegalStateException("Unhandled server state: " + data.state());
    }

    return new Display(icon, statusText, pingTooltip, playerTooltip);
  }

  static int render(
      GuiGraphicsExtractor extractor,
      Font font,
      ServerData data,
      int rightEdge,
      int rowY,
      int mouseX,
      int mouseY,
      int rowIndex,
      long nowMillis) {
    Display display = display(data, nowMillis, rowIndex);
    int iconX = rightEdge - ICON_WIDTH;
    extractor.blitSprite(PIPELINE, display.icon(), iconX, rowY, ICON_WIDTH, ICON_HEIGHT);

    int statusWidth = font.width(display.statusText());
    int statusX = iconX - STATUS_GAP - statusWidth;
    extractor.text(font, display.statusText(), statusX, rowY + 1, STATUS_COLOR);

    if (inside(mouseX, mouseY, iconX, rowY, ICON_WIDTH, ICON_HEIGHT)) {
      extractor.setTooltipForNextFrame(display.pingTooltip(), mouseX, mouseY);
    } else if (!display.playerTooltip().isEmpty()
        && inside(mouseX, mouseY, statusX, rowY, statusWidth, font.lineHeight + 8)) {
      extractor.setTooltipForNextFrame(
          display.playerTooltip().stream().map(Component::getVisualOrderText).toList(),
          mouseX,
          mouseY);
    }

    return statusWidth == 0 ? iconX : statusX;
  }

  private static Identifier successfulSprite(long ping) {
    if (ping < 150) {
      return sprite("ping_5");
    }
    if (ping < 300) {
      return sprite("ping_4");
    }
    if (ping < 600) {
      return sprite("ping_3");
    }
    if (ping < 1000) {
      return sprite("ping_2");
    }
    return sprite("ping_1");
  }

  private static Identifier pingingSprite(long nowMillis, int rowIndex) {
    int frame = (int) ((nowMillis / 100L + rowIndex * 2L) & 7L);
    if (frame > 4) {
      frame = 8 - frame;
    }
    return sprite("pinging_" + (frame + 1));
  }

  private static Identifier sprite(String name) {
    return Identifier.withDefaultNamespace("server_list/" + name);
  }

  private static List<Component> copyPlayerTooltip(List<Component> playerList) {
    return playerList == null || playerList.isEmpty() ? List.of() : List.copyOf(playerList);
  }

  private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  record Display(
      Identifier icon,
      Component statusText,
      Component pingTooltip,
      List<Component> playerTooltip) {}
}
