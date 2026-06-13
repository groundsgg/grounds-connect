package gg.grounds.connect.nats;

import gg.grounds.connect.api.Nats;

/** Receives NATS live-tail frames on the client thread. */
public interface NatsTailSink {
    void onMessage(Nats.TailMessage message);

    void onInfo(String line);
}
