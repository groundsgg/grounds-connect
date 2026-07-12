package gg.grounds.connect.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ScreenNavigationTest {
  @Test
  void detectsTheDisplayedScreenByIdentity() {
    Object displayed = new Object();
    Object other = new Object();

    assertTrue(ScreenNavigation.isCurrent(displayed, displayed));
    assertFalse(ScreenNavigation.isCurrent(displayed, other));
    assertFalse(ScreenNavigation.isCurrent(null, displayed));
  }
}
