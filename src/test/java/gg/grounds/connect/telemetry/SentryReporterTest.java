package gg.grounds.connect.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class SentryReporterTest {

  @Test
  void apiHostTagKeepsOnlyHostAndPort() {
    assertEquals(
        "platform.grnds.io",
        SentryReporter.apiHostTag("https://platform.grnds.io/v1/projects?access_token=secret"));
    assertEquals("localhost:8080", SentryReporter.apiHostTag("http://localhost:8080/api"));
  }

  @Test
  void invalidApiHostTagIsOmitted() {
    assertNull(SentryReporter.apiHostTag(""));
    assertNull(SentryReporter.apiHostTag("not a uri"));
  }

  @Test
  void sampleRateParsingIsClamped() {
    assertEquals(0.25, SentryReporter.sampleRate(Map.of("RATE", "0.25"), "RATE", 1.0));
    assertEquals(0.0, SentryReporter.sampleRate(Map.of("RATE", "-1"), "RATE", 1.0));
    assertEquals(1.0, SentryReporter.sampleRate(Map.of("RATE", "2"), "RATE", 0.0));
    assertEquals(0.5, SentryReporter.sampleRate(Map.of("RATE", "nope"), "RATE", 0.5));
  }

  @Test
  void expectedClientResponsesAreNotCapturedAsFailures() {
    assertFalse(SentryReporter.shouldCaptureApiFailure(401));
    assertFalse(SentryReporter.shouldCaptureApiFailure(403));
    assertFalse(SentryReporter.shouldCaptureApiFailure(404));
    assertTrue(SentryReporter.shouldCaptureApiFailure(429));
    assertTrue(SentryReporter.shouldCaptureApiFailure(500));
  }

  @Test
  void sensitiveValuesAreScrubbedFromMessages() {
    String sanitized =
        SentryReporter.sanitizeMessage(
            "GET /v1/projects?access_token=abc&refresh_token=def "
                + "Authorization: Bearer secret-token device_code=abc123");

    assertFalse(sanitized.contains("abc123"));
    assertFalse(sanitized.contains("secret-token"));
    assertFalse(sanitized.contains("access_token=abc"));
    assertFalse(sanitized.contains("refresh_token=def"));
    assertTrue(sanitized.contains("access_token=[Filtered]"));
    assertTrue(sanitized.contains("Authorization: [Filtered]"));
  }

  @Test
  void httpDescriptionsRemoveQueriesAndDynamicSegments() {
    assertEquals("GET /v1/projects", SentryReporter.httpDescription("GET", "/v1/projects?x=y"));
    assertEquals(
        "SSE /v1/pushes/:id/logs",
        SentryReporter.httpDescription("SSE", "/v1/pushes/01HTABCDEFG/logs"));
  }
}
