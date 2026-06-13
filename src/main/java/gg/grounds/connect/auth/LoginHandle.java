package gg.grounds.connect.auth;

import java.util.concurrent.atomic.AtomicBoolean;

/** Handle to an in-flight login, allowing cancellation. */
public final class LoginHandle {
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  public void cancel() {
    cancelled.set(true);
  }

  public boolean isCancelled() {
    return cancelled.get();
  }
}
