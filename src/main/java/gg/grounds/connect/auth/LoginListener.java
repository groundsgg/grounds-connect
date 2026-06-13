package gg.grounds.connect.auth;

/** Device-login progress sink. All methods are dispatched on the client thread. */
public interface LoginListener {
  void onCode(KeycloakClient.DeviceCode code);

  void onSuccess();

  void onError(String message);
}
