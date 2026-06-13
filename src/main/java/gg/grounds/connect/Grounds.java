package gg.grounds.connect;

import gg.grounds.connect.core.GroundsServices;

/** Entry point for shared Grounds services. */
public final class Grounds {
  private static final GroundsServices SERVICES = new GroundsServices();

  private Grounds() {}

  public static GroundsServices services() {
    return SERVICES;
  }
}
