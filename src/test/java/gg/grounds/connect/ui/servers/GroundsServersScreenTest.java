package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class GroundsServersScreenTest {

  @Test
  void refreshButtonResetClearsLoadingAndFocus() {
    SpriteIconButton button =
        SpriteIconButton.builder(Component.literal("Refresh"), ignored -> {}, true)
            .size(20, 20)
            .sprite(Identifier.fromNamespaceAndPath("grounds_connect", "icon/refresh"), 12, 12)
            .tooltip(Component.literal("Refresh"))
            .narration(ignored -> Component.literal("Refresh"))
            .build();

    button.setFocused(true);
    button.setLoading(true);

    GroundsServersScreen.resetRefreshButtonState(button);

    assertFalse(button.isFocused());
    assertTrue(button.isActive());
  }
}
