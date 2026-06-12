package gg.grounds.connect.api;

import java.util.Locale;

/**
 * Live runtime status of a deployment, from {@code GET /v1/deployments/:name/runtime}.
 * (The deployment's coarse {@code state} comes from the list endpoint — see {@link GroundsServer}.)
 */
public record DeploymentRuntime(int replicasReady, int replicasDesired, String devClusterState, String imageTag) {

    public enum Health { UP, STARTING, PAUSED, DOWN, UNKNOWN }

    /**
     * Health bucket for a deployment. The deployment {@code state} (always present, from the list)
     * is the primary signal so an online server reads as online even before — or without — the
     * per-server runtime fetch (which resolves the project from the token default and can 404 for
     * a non-default project). The {@code runtime} (nullable) only refines paused/scaling detection.
     */
    public static Health healthFor(String state, DeploymentRuntime runtime) {
        String s = state == null ? "" : state.toLowerCase(Locale.ROOT);
        String dc = (runtime == null || runtime.devClusterState == null)
                ? "" : runtime.devClusterState.toLowerCase(Locale.ROOT);

        if (s.equals("paused") || dc.equals("paused")) {
            return Health.PAUSED;
        }
        if (s.equals("failed") || s.equals("deleted") || dc.equals("failed") || dc.equals("deleted")) {
            return Health.DOWN;
        }
        if (s.equals("active") || s.equals("ready")) {
            // Active but the runtime confidently reports zero ready pods -> still spinning up.
            if (runtime != null && runtime.replicasDesired > 0 && runtime.replicasReady == 0) {
                return Health.STARTING;
            }
            return Health.UP;
        }
        if (s.equals("pending") || dc.equals("creating")) {
            return Health.STARTING;
        }
        if (runtime != null && runtime.replicasDesired > 0) {
            return runtime.replicasReady >= runtime.replicasDesired ? Health.UP : Health.STARTING;
        }
        return Health.UNKNOWN;
    }
}
