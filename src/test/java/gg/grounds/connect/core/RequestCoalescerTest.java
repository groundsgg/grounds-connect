package gg.grounds.connect.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RequestCoalescerTest {
  @Test
  void rejectsDuplicateInFlightRequestUntilFinished() {
    RequestCoalescer coalescer = new RequestCoalescer();

    assertTrue(coalescer.begin("project-a", "server-a"));
    assertFalse(coalescer.begin("project-a", "server-a"));

    coalescer.finish("project-a", "server-a");

    assertTrue(coalescer.begin("project-a", "server-a"));
  }

  @Test
  void separatesRequestsByProjectAndName() {
    RequestCoalescer coalescer = new RequestCoalescer();

    assertTrue(coalescer.begin("project-a", "server-a"));
    assertTrue(coalescer.begin("project-a", "server-b"));
    assertTrue(coalescer.begin("project-b", "server-a"));
  }
}
