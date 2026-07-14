# Vanilla Server Action Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the refresh and logout Unicode glyphs with centered 12 by 12 pixel sprites rendered by Minecraft's vanilla icon-button widget.

**Architecture:** Store two dedicated GUI sprites in the Grounds Connect resource namespace and reference them from `GroundsServersScreen` through `SpriteIconButton`. A focused resource test verifies that both assets exist, have the approved dimensions, contain visible pixels, and retain transparency.

**Tech Stack:** Java 25, Minecraft 26.2 client GUI API, Fabric Loom, JUnit 6, PNG GUI sprites

## Global Constraints

- Keep both icon-only buttons at 20 by 20 pixels.
- Render centered 12 by 12 pixel-art sprites.
- Preserve the existing translated tooltips and narration text.
- Keep vanilla normal, hover, focus, and disabled button backgrounds.
- Do not change project selection, NATS, bottom actions, loading behavior, or action semantics.

---

## File Structure

- `src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/refresh.png`: 12 by 12 clockwise refresh symbol with transparent background.
- `src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/logout.png`: 12 by 12 logout arrow and door symbol with transparent background.
- `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java`: builds and positions the two icon-only `SpriteIconButton` controls.
- `src/test/java/gg/grounds/connect/ui/servers/ServerActionIconResourcesTest.java`: validates the sprite resources without depending on Minecraft's renderer.

### Task 1: Add and render vanilla-style action sprites

**Files:**
- Create: `src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/refresh.png`
- Create: `src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/logout.png`
- Create: `src/test/java/gg/grounds/connect/ui/servers/ServerActionIconResourcesTest.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java:15-20,96-121`

**Interfaces:**
- Consumes: `SpriteIconButton.builder(Component, Button.OnPress, boolean)`, `Identifier.fromNamespaceAndPath(String, String)`, and the existing refresh/logout callbacks.
- Produces: resource identifiers `grounds_connect:icon/refresh` and `grounds_connect:icon/logout`, each rendered at 12 by 12 pixels inside a 20 by 20 icon-only button.

- [ ] **Step 1: Write the failing resource test**

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerActionIconResourcesTest {

  @ParameterizedTest
  @ValueSource(strings = {"refresh", "logout"})
  void iconIsTwelvePixelTransparentSprite(String name) throws IOException {
    String path = "assets/grounds_connect/textures/gui/sprites/icon/" + name + ".png";
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      assertNotNull(stream, () -> "Missing action icon: " + path);
      BufferedImage image = ImageIO.read(stream);
      assertNotNull(image, () -> "Unreadable action icon: " + path);
      assertEquals(12, image.getWidth());
      assertEquals(12, image.getHeight());

      int visiblePixels = 0;
      int transparentPixels = 0;
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          int alpha = image.getRGB(x, y) >>> 24;
          if (alpha == 0) {
            transparentPixels++;
          } else {
            visiblePixels++;
          }
        }
      }
      assertTrue(visiblePixels > 0, () -> "Action icon is empty: " + path);
      assertTrue(transparentPixels > 0, () -> "Action icon has no transparency: " + path);
    }
  }
}
```

- [ ] **Step 2: Run the focused test and verify the resources are missing**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerActionIconResourcesTest
```

Expected: FAIL with `Missing action icon` for `refresh.png` and `logout.png`.

- [ ] **Step 3: Add the two pixel-art sprites**

Create both PNGs as 12 by 12 RGBA images with no antialiasing:

- `refresh.png`: light-gray clockwise circular arrow, one-pixel strokes, transparent background, visually centered with two pixels of outer breathing room.
- `logout.png`: light-gray door frame on the left and right-facing arrow, one-pixel strokes, transparent background, visually centered with one pixel of separation between door and arrow.
- Use only fully transparent or fully opaque pixels so GUI scaling stays crisp.

- [ ] **Step 4: Run the focused resource test and verify it passes**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerActionIconResourcesTest
```

Expected: PASS for both `refresh` and `logout` parameter values.

- [ ] **Step 5: Replace Unicode buttons with `SpriteIconButton`**

Add imports and constants to `GroundsServersScreen`:

```java
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.resources.Identifier;

private static final int ACTION_BUTTON_SIZE = 20;
private static final int ACTION_ICON_SIZE = 12;
private static final Identifier REFRESH_ICON =
    Identifier.fromNamespaceAndPath("grounds_connect", "icon/refresh");
private static final Identifier LOGOUT_ICON =
    Identifier.fromNamespaceAndPath("grounds_connect", "icon/logout");
```

Replace the refresh button construction with:

```java
Component refreshTooltip =
    Component.translatable("grounds_connect.control.refresh.tooltip");
SpriteIconButton refreshIconButton =
    SpriteIconButton.builder(refreshTooltip, button -> reloadServers(), true)
        .size(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE)
        .sprite(REFRESH_ICON, ACTION_ICON_SIZE, ACTION_ICON_SIZE)
        .tooltip(refreshTooltip)
        .narration(ignored -> refreshTooltip)
        .build();
refreshIconButton.setX(192);
refreshIconButton.setY(row);
refreshButton = refreshIconButton;
addRenderableWidget(refreshButton);
```

Replace the logout button construction with:

```java
Component logoutTooltip = Component.translatable("grounds_connect.menu.logout");
SpriteIconButton logoutButton =
    SpriteIconButton.builder(logoutTooltip, button -> logout(), true)
        .size(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE)
        .sprite(LOGOUT_ICON, ACTION_ICON_SIZE, ACTION_ICON_SIZE)
        .tooltip(logoutTooltip)
        .narration(ignored -> logoutTooltip)
        .build();
logoutButton.setX(width - 28);
logoutButton.setY(row);
addRenderableWidget(logoutButton);
```

- [ ] **Step 6: Compile and rerun the focused test**

Run with escalated permissions:

```bash
./gradlew compileJava test --tests gg.grounds.connect.ui.servers.ServerActionIconResourcesTest
```

Expected: `BUILD SUCCESSFUL` and both resource cases pass.

- [ ] **Step 7: Run the mandatory Gradle verification sequence**

Run each command with escalated permissions:

```bash
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Expected: all three commands finish with `BUILD SUCCESSFUL`; formatting does not alter the intended behavior.

- [ ] **Step 8: Launch the test client and visually verify the controls**

Run with escalated permissions using the development Fabric API runtime setup already required by this checkout:

```bash
./gradlew -I /tmp/grounds-connect-runclient.gradle runClient
```

Verify at the active GUI scale:

- Refresh and logout icons are visibly larger than the former glyphs.
- Both icons are centered within their 20 by 20 buttons.
- Pixel edges stay crisp.
- Hover tooltips identify the actions.
- Refresh still reloads the selected project's servers.
- Logout still starts the existing logout flow.

- [ ] **Step 9: Commit the implementation**

```bash
git add src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java \
  src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/refresh.png \
  src/main/resources/assets/grounds_connect/textures/gui/sprites/icon/logout.png \
  src/test/java/gg/grounds/connect/ui/servers/ServerActionIconResourcesTest.java
git commit -m "fix(connect): render vanilla action icons"
```
