package gg.grounds.serverlist.api;

/**
 * A connectable Minecraft server derived from a forge deployment whose {@code publicUrl}
 * uses the {@code minecraft://} scheme.
 *
 * @param name       deployment name (stable id)
 * @param address    server address for the multiplayer list (host, optionally host:port)
 * @param state      deployment state (e.g. "active")
 * @param type       forge deployment type. The API returns the normalized internal form
 *                   ({@code plugin-velocity} / {@code plugin-paper} / {@code gamemode} /
 *                   {@code service}); we also accept the short public/manifest forms
 *                   ({@code velocity} / {@code paper} / {@code gamemode}) to be vocabulary-proof.
 */
public record GroundsServer(String name, String address, String state, String type) {

    public boolean isActive() {
        return "active".equalsIgnoreCase(state);
    }

    /** A Velocity proxy — the single externally-joinable endpoint that fronts backend game servers. */
    public boolean isVelocityProxy() {
        return "plugin-velocity".equals(type) || "velocity".equals(type);
    }

    /** A backend game server (Paper, Agones gamemode or Minestom) — joined via the proxy, not directly. */
    public boolean isBackend() {
        return "gamemode".equals(type)
                || "plugin-paper".equals(type) || "paper".equals(type)
                || "minestom".equals(type);
    }
}
