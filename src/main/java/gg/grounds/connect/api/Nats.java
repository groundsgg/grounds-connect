package gg.grounds.connect.api;

import java.util.List;

/** Models for the forge NATS observability endpoints ({@code /v1/cluster/nats[/tail]}). */
public final class Nats {

  /**
   * Snapshot from {@code GET /v1/cluster/nats}. {@code broker}/{@code jetstream} are null if
   * unreachable.
   */
  public record Snapshot(
      Broker broker, List<Event> events, List<Conn> connections, JetStream jetstream) {}

  public record Broker(
      int connections,
      long inMsgs,
      long outMsgs,
      long inBytes,
      long outBytes,
      int subscriptions,
      int slowConsumers) {}

  /** A subject an app declares in its manifest ({@code dir} = pub/sub/both). */
  public record Event(String app, String subject, String dir) {}

  /**
   * A live client connection and the subjects it subscribes to ({@code system} = infra connection).
   */
  public record Conn(long cid, String name, List<String> subjects, boolean system) {}

  public record JetStream(int streams, long messages, long bytes, List<Stream> list) {}

  public record Stream(
      String name,
      List<String> subjects,
      long messages,
      long bytes,
      int consumers,
      long firstSeq,
      long lastSeq) {}

  /**
   * A live message from {@code GET /v1/cluster/nats/tail} ({@code data} is UTF-8 or {@code
   * base64:…}).
   */
  public record TailMessage(String subject, String data, long bytes) {}

  private Nats() {}
}
