package gg.grounds.connect.core;

/** Generic async result sink. */
public interface AsyncCallback<T> {
  void onResult(T value);

  void onError(Throwable error);
}
