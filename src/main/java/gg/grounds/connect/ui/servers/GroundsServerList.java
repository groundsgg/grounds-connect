package gg.grounds.connect.ui.servers;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Scrollable server list rendered entirely by us, with per-row pin star, expand caret and health
 * badge.
 */
public class GroundsServerList extends ObjectSelectionList<ServerEntry> {

  /** Left-edge column widths (render advance == click hit-zone), in order: caret, then star. */
  private static final int CARET_W = 12; // only on Velocity-proxy rows

  private static final int STAR_W = 14; // only on top-level rows

  private Runnable onJoin;
  private Consumer<ServerEntry> onSelectionChanged;
  private Consumer<ServerEntry> onToggleFavorite;
  private Consumer<ServerEntry> onToggleExpand;

  public GroundsServerList(Minecraft mc, int width, int height, int top, int itemHeight) {
    super(mc, width, height, top, itemHeight);
  }

  /** Action run when an entry is double-clicked (connect to it). */
  public void setOnJoin(Runnable onJoin) {
    this.onJoin = onJoin;
  }

  public void setOnSelectionChanged(Consumer<ServerEntry> onSelectionChanged) {
    this.onSelectionChanged = onSelectionChanged;
  }

  @Override
  public void setSelected(ServerEntry entry) {
    super.setSelected(entry);
    if (onSelectionChanged != null) {
      onSelectionChanged.accept(entry);
    }
  }

  /** Called when the pin star of an entry is clicked. */
  public void setOnToggleFavorite(Consumer<ServerEntry> onToggleFavorite) {
    this.onToggleFavorite = onToggleFavorite;
  }

  /** Called when the expand caret of a Velocity-proxy row is clicked. */
  public void setOnToggleExpand(Consumer<ServerEntry> onToggleExpand) {
    this.onToggleExpand = onToggleExpand;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (!doubleClick && event.button() == 0) {
      ServerEntry entry = getEntryAtPosition(event.x(), event.y());
      if (entry != null) {
        int cx = entry.getContentX() + 4;
        if (entry.isProxy()) {
          if (event.x() <= cx + CARET_W) {
            if (onToggleExpand != null) {
              onToggleExpand.accept(entry);
            }
            return true; // caret toggles expand only — don't select/connect
          }
          cx += CARET_W;
        }
        if (entry.depth == 0 && event.x() <= cx + STAR_W) {
          if (onToggleFavorite != null) {
            onToggleFavorite.accept(entry);
          }
          return true; // star toggles the pin only
        }
      }
    }
    boolean handled = super.mouseClicked(event, doubleClick);
    if (doubleClick && onJoin != null) {
      ServerEntry entry = getEntryAtPosition(event.x(), event.y());
      if (entry != null && entry.depth == 0 && !entry.isMinestom()) { // backends join via the proxy
        setSelected(entry);
        onJoin.run();
      }
    }
    return handled;
  }

  @Override
  public int getRowWidth() {
    return 308;
  }

  public void setServers(List<ServerEntry> entries) {
    this.clearEntries();
    for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++) {
      ServerEntry entry = entries.get(rowIndex);
      entry.setStatusRowIndex(rowIndex);
      this.addEntry(entry);
    }
  }

  public ServerEntry selected() {
    return getSelected();
  }
}
