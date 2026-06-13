package gg.grounds.connect.logs;

/** Receives log lines from an SSE log endpoint on the client thread. */
public interface LogSink {
  void onLine(String line);
}
