package gg.grounds.connect.nats;

import com.google.gson.JsonParser;
import gg.grounds.connect.api.Nats;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;
import gg.grounds.connect.core.SessionLifecycle;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

/** NATS cluster snapshot and live-tail operations. */
public final class NatsService {
  private final ClientTaskRunner runner;
  private final AuthenticatedApi api;
  private final SessionLifecycle lifecycle;

  public NatsService(ClientTaskRunner runner, AuthenticatedApi api, SessionLifecycle lifecycle) {
    this.runner = runner;
    this.api = api;
    this.lifecycle = lifecycle;
  }

  /** {@code GET /v1/cluster/nats} — broker stats, declared events, connections, JetStream. */
  public void fetchCluster(String projectId, AsyncCallback<Nats.Snapshot> cb) {
    runner.execute(
        () -> {
          try {
            Nats.Snapshot snap =
                api.withAuthRetry(token -> api.api().getClusterNats(token, projectId));
            runner.onClient(() -> cb.onResult(snap));
          } catch (Throwable t) {
            runner.onClient(() -> cb.onError(t));
          }
        });
  }

  /**
   * Tails the workspace NATS broker via {@code GET /v1/cluster/nats/tail} (SSE). Runs until the
   * stream ends or {@code cancelled} returns true.
   */
  public void tail(
      String projectId, List<String> subjects, NatsTailSink sink, BooleanSupplier cancelled) {
    runner.execute(
        () -> {
          SessionLifecycle.Lease lease = lifecycle.openLease();
          try (lease) {
            StringBuilder path =
                new StringBuilder("/v1/cluster/nats/tail?projectId=")
                    .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8));
            for (String s : subjects) {
              path.append("&subject=").append(URLEncoder.encode(s, StandardCharsets.UTF_8));
            }
            String token = api.withAuthRetry(t -> t);
            api.api()
                .streamSse(
                    token,
                    path.toString(),
                    (event, data) -> {
                      switch (event) {
                        case "message" -> {
                          Nats.TailMessage m = parseTailMessage(data);
                          if (m != null) {
                            runner.onClient(() -> sink.onMessage(m));
                          }
                        }
                        case "ready" -> runner.onClient(() -> sink.onInfo("== streaming =="));
                        case "error" ->
                            runner.onClient(
                                () ->
                                    sink.onInfo("== error: " + jsonField(data, "reason") + " =="));
                        case "warning" ->
                            runner.onClient(
                                () ->
                                    sink.onInfo(
                                        "== warning: " + jsonField(data, "reason") + " =="));
                        default -> {}
                      }
                    },
                    () -> cancelled.getAsBoolean() || lease.isCancelled(),
                    lease::closeOnCancel);
            if (!cancelled.getAsBoolean() && !lease.isCancelled()) {
              runner.onClient(() -> sink.onInfo("== tail ended =="));
            }
          } catch (Throwable t) {
            if (!cancelled.getAsBoolean() && !lease.isCancelled()) {
              runner.onClient(() -> sink.onInfo("== error: " + message(t) + " =="));
            }
          }
        });
  }

  private static Nats.TailMessage parseTailMessage(String dataJson) {
    try {
      var o = JsonParser.parseString(dataJson).getAsJsonObject();
      String subject =
          o.has("subject") && !o.get("subject").isJsonNull() ? o.get("subject").getAsString() : "";
      String data = o.has("data") && !o.get("data").isJsonNull() ? o.get("data").getAsString() : "";
      long bytes = o.has("bytes") && !o.get("bytes").isJsonNull() ? o.get("bytes").getAsLong() : 0L;
      return new Nats.TailMessage(subject, data, bytes);
    } catch (Exception e) {
      return null;
    }
  }

  private static String jsonField(String dataJson, String key) {
    try {
      var o = JsonParser.parseString(dataJson).getAsJsonObject();
      return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    } catch (Exception e) {
      return dataJson;
    }
  }

  private static String message(Throwable t) {
    return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
  }
}
