package gg.grounds.serverlist.api;

/** A build/deploy push, from {@code GET /v1/pushes}. {@code appName} is derived from the manifest. */
public record Push(String id, String status, String appName) {
}
