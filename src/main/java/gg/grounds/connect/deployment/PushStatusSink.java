package gg.grounds.connect.deployment;

/** Receives push build/deploy status transitions on the client thread. */
public interface PushStatusSink {
  void onStatus(String pushId, String appName, String status);
}
