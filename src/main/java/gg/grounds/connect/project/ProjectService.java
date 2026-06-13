package gg.grounds.connect.project;

import gg.grounds.connect.api.Project;
import gg.grounds.connect.config.GroundsConfig;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.AuthenticatedApi;
import gg.grounds.connect.core.ClientTaskRunner;

import java.util.List;
import java.util.Objects;

/** Owns project cache and selected project config. */
public final class ProjectService {
    private final ClientTaskRunner runner;
    private final AuthenticatedApi api;

    private volatile List<Project> cachedProjects = List.of();

    public ProjectService(ClientTaskRunner runner, AuthenticatedApi api) {
        this.runner = runner;
        this.api = api;
    }

    public List<Project> cachedProjects() {
        return cachedProjects;
    }

    /** Resolves the selected project from config against the cached list, falling back to the first. */
    public Project selectedProject() {
        String id = GroundsConfig.get().selectedProjectId();
        List<Project> projects = cachedProjects;
        if (projects.isEmpty()) {
            return null;
        }
        if (id != null) {
            for (Project p : projects) {
                if (Objects.equals(p.id(), id)) {
                    return p;
                }
            }
        }
        return projects.get(0);
    }

    public void selectProject(Project project) {
        GroundsConfig.get().setSelectedProjectId(project == null ? null : project.id());
    }

    public void clearCache() {
        cachedProjects = List.of();
    }

    public void fetch(AsyncCallback<List<Project>> cb) {
        runner.execute(() -> {
            try {
                List<Project> projects = refreshNow();
                runner.onClient(() -> cb.onResult(projects));
            } catch (Throwable t) {
                runner.onClient(() -> cb.onError(t));
            }
        });
    }

    public List<Project> refreshNow() throws Exception {
        List<Project> projects = api.withAuthRetry(token -> api.api().listProjects(token));
        this.cachedProjects = projects;
        return projects;
    }
}
