package gg.grounds.connect.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Shared helpers for navigating between client screens. */
public final class ScreenNavigation {

  private ScreenNavigation() {}

  public static boolean isCurrent(Object displayed, Object expected) {
    return displayed == expected;
  }

  public static void show(Minecraft minecraft, Screen screen) {
    minecraft.setScreenAndShow(screen);
  }
}
