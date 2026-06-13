package gg.grounds.connect.core;

import gg.grounds.connect.auth.AuthService;
import gg.grounds.connect.deployment.DeploymentService;
import gg.grounds.connect.deployment.PushWatcher;
import gg.grounds.connect.logs.LogService;
import gg.grounds.connect.nats.NatsService;
import gg.grounds.connect.project.ProjectService;
import gg.grounds.connect.server.ServerService;

/** Shared service graph for Grounds client features. */
public final class GroundsServices {
    private final ClientTaskRunner runner = new ClientTaskRunner();
    private final AuthService auth = new AuthService(runner);
    private final AuthenticatedApi api = new AuthenticatedApi(auth);
    private final PlatformService platform = new PlatformService(runner, api);
    private final ProjectService projects = new ProjectService(runner, api);
    private final ServerService servers = new ServerService(runner, api);
    private final DeploymentService deployments = new DeploymentService(runner, api);
    private final PushWatcher pushes = new PushWatcher(runner, api, auth, projects);
    private final LogService logs = new LogService(runner, api);
    private final NatsService nats = new NatsService(runner, api);

    public AuthService auth() {
        return auth;
    }

    public PlatformService platform() {
        return platform;
    }

    public ProjectService projects() {
        return projects;
    }

    public ServerService servers() {
        return servers;
    }

    public DeploymentService deployments() {
        return deployments;
    }

    public PushWatcher pushes() {
        return pushes;
    }

    public LogService logs() {
        return logs;
    }

    public NatsService nats() {
        return nats;
    }

    public void logout() {
        auth.logout();
        projects.clearCache();
    }
}
