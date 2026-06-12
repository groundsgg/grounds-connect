package gg.grounds.connect.api;

/** A ready dev push a deployment can be rolled back to (from GET /v1/pushes). */
public record RollbackTarget(String id, String imageTag, String createdAt) {
}
