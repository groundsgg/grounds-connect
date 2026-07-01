package gg.grounds.connect.logs;

import com.google.gson.JsonParser;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;
import gg.grounds.connect.core.SessionLifecycle;
import gg.grounds.connect.telemetry.SentryReporter;
import java.util.function.BooleanSupplier;

/** Tails Grounds SSE log endpoints. */
public final class LogService {
  private final ClientTaskRunner runner;
  private final AuthenticatedApi api;
  private final SessionLifecycle lifecycle;

  public LogService(ClientTaskRunner runner, AuthenticatedApi api, SessionLifecycle lifecycle) {
    this.runner = runner;
    this.api = api;
    this.lifecycle = lifecycle;
  }

  /**
   * Tails an SSE log endpoint, pushing each log/warning/status frame to {@code sink}. Runs until
   * the stream ends or {@code cancelled} returns true.
   */
  public void stream(String path, LogSink sink, BooleanSupplier cancelled) {
    runner.execute(
        () -> {
          SessionLifecycle.Lease lease = lifecycle.openLease();
          try (lease) {
            String token = api.withAuthRetry(t -> t);
            api.api()
                .streamSse(
                    token,
                    path,
                    (event, data) -> {
                      String line =
                          switch (event) {
                            case "log" -> jsonField(data, "line");
                            case "warning" -> "[" + jsonField(data, "reason") + "]";
                            case "status" -> "== status: " + jsonField(data, "status") + " ==";
                            default -> null;
                          };
                      if (line != null) {
                        runner.onClient(() -> sink.onLine(line));
                      }
                    },
                    () -> cancelled.getAsBoolean() || lease.isCancelled(),
                    lease::closeOnCancel);
            if (!cancelled.getAsBoolean() && !lease.isCancelled()) {
              runner.onClient(() -> sink.onLine("== stream ended =="));
            }
          } catch (Throwable t) {
            if (!cancelled.getAsBoolean() && !lease.isCancelled()) {
              SentryReporter.captureHandled(t, "logs.stream", "stream_failed");
              runner.onClient(() -> sink.onLine("== error: " + t.getMessage() + " =="));
            }
          }
        });
  }

  private static String jsonField(String dataJson, String key) {
    try {
      var o = JsonParser.parseString(dataJson).getAsJsonObject();
      return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    } catch (Exception e) {
      return dataJson;
    }
  }
}
