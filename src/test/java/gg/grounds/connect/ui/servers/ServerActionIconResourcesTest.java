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
