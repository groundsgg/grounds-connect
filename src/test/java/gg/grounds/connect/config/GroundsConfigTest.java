package gg.grounds.connect.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.grounds.connect.Constants;
import org.junit.jupiter.api.Test;

final class GroundsConfigTest {
  @Test
  void apiBaseUrlNormalizesCurrentDefault() {
    assertEquals(
        Constants.DEFAULT_API_BASE_URL,
        GroundsConfig.normalizeApiBaseUrl(Constants.DEFAULT_API_BASE_URL + "/"));
  }

  @Test
  void apiBaseUrlMigratesLegacyDefault() {
    assertEquals(
        Constants.DEFAULT_API_BASE_URL,
        GroundsConfig.normalizeApiBaseUrl(Constants.LEGACY_API_BASE_URL));
  }

  @Test
  void apiBaseUrlKeepsCustomOverride() {
    assertEquals(
        "http://localhost:8080", GroundsConfig.normalizeApiBaseUrl(" http://localhost:8080/ "));
  }
}
