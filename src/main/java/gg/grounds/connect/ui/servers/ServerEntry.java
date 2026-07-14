package gg.grounds.connect.ui.servers;

import gg.grounds.connect.api.DeploymentRuntime;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** One server row: name + address, plus a health badge for Grounds deployments. */
public final class ServerEntry extends ObjectSelectionList.Entry<ServerEntry> {

  /** Left-edge column widths (render advance == click hit-zone), in order: caret, then star. */
  private static final int CARET_W = 12; // only on Velocity-proxy rows

  private static final int STAR_W = 14; // only on top-level rows
  private static final int CHILD_INDENT = 18; // indent for nested backend rows (no caret/star)

  public final String name;
  public final String address;
  public final ServerData data;

  /** Coarse deployment state from the list endpoint (null for plain saved servers — no badge). */
  public final String deploymentState;

  /** Forge manifest type (plugin-velocity / gamemode / plugin-paper / service). */
  public final String type;

  /** Live runtime, filled in asynchronously; read live each frame. */
  public volatile DeploymentRuntime runtime;

  /** Whether this server is pinned; set by the screen from config when (re)building the view. */
  public volatile boolean favorite;

  /** 0 = top-level row; 1 = backend nested under a Velocity proxy. */
  public int depth;

  /** Whether this proxy's backends are currently shown; set from config when building the view. */
  public volatile boolean expanded;

  /** Backend game servers fronted by this proxy (null/empty for non-proxy rows). */
  public List<ServerEntry> children;

  /** Current visible-row index, used for Vanilla's staggered ping animation. */
  private int statusRowIndex;

  public ServerEntry(
      String name, String address, ServerData data, String deploymentState, String type) {
    this.name = name;
    this.address = address;
    this.data = data;
    this.deploymentState = deploymentState;
    this.type = type;
  }

  public boolean isProxy() {
    return "plugin-velocity".equals(type) || "velocity".equals(type);
  }

  /** Minestom backends have no direct minecraft:// route — reachable only through the proxy. */
  public boolean isMinestom() {
    return "minestom".equals(type);
  }

  void setStatusRowIndex(int statusRowIndex) {
    this.statusRowIndex = statusRowIndex;
  }

  @Override
  public Component getNarration() {
    return Component.literal(name);
  }

  /** Gap kept between the left text column and a right-aligned badge/ping so they never touch. */
  private static final int RIGHT_GAP = 6;

  @Override
  public void extractContent(
      GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
    Font font = Minecraft.getInstance().font;
    int x = getContentX();
    int y = getContentY();
    int rightEdge = getContentRight() - 4;
    int line1Y = y + 3;
    int line2Y = y + 5 + font.lineHeight;
    int glyphY = y + 3 + font.lineHeight / 2;
    boolean child = depth > 0;

    // Left column: expand caret (proxy only), then pin star (top-level only), then indent
    // (children).
    int cursor = x + 4;
    if (isProxy()) {
      extractor.text(font, expanded ? "▾" : "▸", cursor, glyphY, 0xFFCCCCCC);
      cursor += CARET_W;
    }
    if (!child) {
      extractor.text(
          font, favorite ? "★" : "☆", cursor, glyphY, favorite ? 0xFFFFD24A : 0xFF777777);
      cursor += STAR_W;
    } else {
      cursor += CHILD_INDENT;
    }
    int textX = cursor;

    // Right-aligned columns drawn first, so we know how much room the left text has.
    int vanillaLeft =
        VanillaServerStatusRenderer.render(
            extractor,
            font,
            data,
            rightEdge,
            line1Y - 1,
            mouseX,
            mouseY,
            statusRowIndex,
            net.minecraft.util.Util.getMillis());
    int nameMaxRight = vanillaLeft - RIGHT_GAP;

    int addressMaxRight = rightEdge;
    if (deploymentState != null) {
      String label = badgeLabel();
      int bx = rightEdge - font.width(label);
      extractor.text(font, label, bx, line2Y, badgeColor());
      addressMaxRight = bx - RIGHT_GAP;
    }

    String label = name;
    if (isProxy() && children != null && !children.isEmpty()) {
      label = name + "  (" + children.size() + ")";
    }
    int nameColor = child ? 0xFFB0B0B0 : 0xFFFFFFFF;
    int addressColor = child ? 0xFF707070 : 0xFFA0A0A0;
    extractor.text(font, fit(font, label, nameMaxRight - textX), textX, line1Y, nameColor);
    extractor.text(font, fit(font, address, addressMaxRight - textX), textX, line2Y, addressColor);
  }

  /** Truncates {@code s} with an ellipsis so it fits within {@code maxWidth} pixels. */
  private static String fit(Font font, String s, int maxWidth) {
    if (maxWidth <= 0) {
      return "";
    }
    if (font.width(s) <= maxWidth) {
      return s;
    }
    String ellipsis = "…";
    int ew = font.width(ellipsis);
    if (maxWidth <= ew) {
      return font.plainSubstrByWidth(s, maxWidth);
    }
    return font.plainSubstrByWidth(s, maxWidth - ew) + ellipsis;
  }

  private DeploymentRuntime.Health health() {
    return DeploymentRuntime.healthFor(deploymentState, runtime);
  }

  private String badgeLabel() {
    DeploymentRuntime rt = runtime;
    return switch (health()) {
      case UP ->
          "● "
              + I18n.get("grounds_connect.server.health.online")
              + (rt != null && rt.replicasDesired() > 0
                  ? " " + rt.replicasReady() + "/" + rt.replicasDesired()
                  : "");
      case STARTING -> "● " + I18n.get("grounds_connect.server.health.starting");
      case PAUSED -> "● " + I18n.get("grounds_connect.server.health.paused");
      case DOWN -> "● " + I18n.get("grounds_connect.server.health.offline");
      default -> "● …";
    };
  }

  private int badgeColor() {
    return switch (health()) {
      case UP -> 0xFF55FF55;
      case STARTING -> 0xFFFFFF55;
      case PAUSED -> 0xFFAAAAAA;
      case DOWN -> 0xFFFF5555;
      default -> 0xFF888888;
    };
  }
}
