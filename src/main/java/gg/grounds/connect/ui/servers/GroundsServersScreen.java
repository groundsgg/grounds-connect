package gg.grounds.connect.ui.servers;

import gg.grounds.connect.Grounds;
import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.config.GroundsConfig;
import gg.grounds.connect.core.GroundsServices;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

/**
 * Dedicated Grounds screen: a project picker plus the selected project's connectable Minecraft
 * servers, each with a forge-runtime health badge. (The player's own saved servers live in the
 * vanilla multiplayer screen, so we don't duplicate them here.) Opened from the main menu.
 */
public final class GroundsServersScreen extends Screen {

  private final Screen lastScreen;
  private final GroundsServices services = Grounds.services();
  private final ServerDataLoader loader = new ServerDataLoader(services, new LoaderListener());
  private final ServerScreenActions actions;
  private final ServerStatusPinger pinger = new ServerStatusPinger();

  private static final int RUNTIME_POLL_TICKS = 100; // ~5s
  private static final int READINESS_POLL_TICKS = 200; // ~10s

  private boolean projectsRequested;
  private boolean initialLoadDone;
  private boolean platformReady = true; // assume up until a probe says otherwise
  private final ServerListModel model = new ServerListModel();
  private String currentProjectId;
  private Component statusMessage = Component.empty();
  private int tickCounter;

  private GroundsServerList serverList;
  private AbstractWidget projectButton; // CycleButton<Project> or a disabled placeholder
  private EditBox search;
  private StringWidget status;

  public GroundsServersScreen(Screen lastScreen) {
    super(Component.translatable("grounds_connect.title"));
    this.lastScreen = lastScreen;
    this.actions =
        new ServerScreenActions(
            this,
            services,
            () -> serverList == null ? null : serverList.selected(),
            () -> currentProjectId);
  }

  @Override
  protected void init() {
    int row = 6;
    int searchY = 30;
    int listTop = 54;
    int listHeight = Math.max(20, this.height - 60 - listTop);

    serverList = new GroundsServerList(this.minecraft, this.width, listHeight, listTop, 32);
    serverList.setOnJoin(actions::joinSelected);
    serverList.setOnToggleFavorite(this::toggleFavorite);
    serverList.setOnToggleExpand(this::toggleExpand);
    addRenderableWidget(serverList);

    projectButton = buildProjectButton(row);
    addRenderableWidget(projectButton);
    addRenderableWidget(
        Button.builder(
                Component.translatable("grounds_connect.control.refresh"), b -> reloadServers())
            .bounds(192, row, 80, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("grounds_connect.menu.logout"), b -> logout())
            .bounds(this.width - 104, row, 100, 20)
            .build());

    if (platformReady) {
      search =
          new EditBox(
              this.font,
              this.width / 2 - 150,
              searchY,
              300,
              18,
              Component.translatable("grounds_connect.search.hint"));
      search.setHint(Component.translatable("grounds_connect.search.hint"));
      search.setMaxLength(64);
      search.setValue(model.searchText());
      search.setResponder(
          value -> {
            model.setSearchText(value);
            applyView();
          });
      addRenderableWidget(search);
    } else {
      // Platform unreachable — the search box is useless, so show a banner in its place.
      StringWidget banner =
          new StringWidget(
              this.width / 2 - 150,
              searchY + 4,
              300,
              12,
              Component.translatable("grounds_connect.platform.unreachable")
                  .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
              this.font);
      addRenderableWidget(banner);
    }

    status =
        new StringWidget(this.width / 2 - 150, listTop + 16, 300, 12, statusMessage, this.font);
    addRenderableWidget(status);

    int recoveryY = this.height - 52;
    addRenderableWidget(
        Button.builder(
                Component.translatable("grounds_connect.action.retry"), b -> actions.retryBuild())
            .bounds(this.width / 2 - 153, recoveryY, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("grounds_connect.action.rollback"),
                b -> actions.rollbackSelected())
            .bounds(this.width / 2 - 50, recoveryY, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("NATS"), b -> actions.openNats())
            .bounds(this.width / 2 + 53, recoveryY, 100, 20)
            .build());

    int by = this.height - 28;
    addRenderableWidget(
        Button.builder(
                Component.translatable("grounds_connect.action.join"), b -> actions.joinSelected())
            .bounds(this.width / 2 - 153, by, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("grounds_connect.action.logs"), b -> actions.openLogs())
            .bounds(this.width / 2 - 50, by, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(CommonComponents.GUI_BACK, b -> onClose())
            .bounds(this.width / 2 + 53, by, 100, 20)
            .build());

    applyView();

    // Only fetch on the first build — later rebuilds (resize, banner toggle) just repaint.
    if (!initialLoadDone) {
      initialLoadDone = true;
      if (services.auth().isLoggedIn()) {
        if (services.projects().cachedProjects().isEmpty() && !projectsRequested) {
          projectsRequested = true;
          setStatusMessage(Component.translatable("grounds_connect.status.loadingProjects"));
          loader.loadProjects();
        } else {
          reloadServers();
        }
      } else {
        setStatusMessage(Component.translatable("grounds_connect.control.login"));
      }
    }
  }

  private AbstractWidget buildProjectButton(int y) {
    Project selected = services.auth().isLoggedIn() ? services.projects().selectedProject() : null;
    if (selected != null && !services.projects().cachedProjects().isEmpty()) {
      return CycleButton.builder((Project p) -> Component.literal(p.displayName()), selected)
          .withValues(services.projects().cachedProjects())
          .create(
              8,
              y,
              180,
              20,
              Component.translatable("grounds_connect.control.project.prefix"),
              (btn, value) -> onProjectChosen(value));
    }
    Button placeholder =
        Button.builder(
                Component.translatable(
                    services.auth().isLoggedIn()
                        ? "grounds_connect.control.project.loading"
                        : "grounds_connect.control.project.placeholder"),
                b -> {})
            .bounds(8, y, 180, 20)
            .build();
    placeholder.active = false;
    return placeholder;
  }

  // --- data ---------------------------------------------------------------

  private void reloadServers() {
    Project selected = services.auth().isLoggedIn() ? services.projects().selectedProject() : null;
    if (selected == null) {
      model.clear();
      currentProjectId = null;
      applyView();
      setStatusMessage(Component.translatable("grounds_connect.status.noProjects"));
      return;
    }
    setStatusMessage(Component.translatable("grounds_connect.status.loadingServers"));
    final String projectId = selected.id();
    this.currentProjectId = projectId;
    loader.loadServers(projectId);
  }

  private void applyView() {
    if (serverList == null) {
      return;
    }
    List<ServerEntry> view = model.visibleEntries(GroundsConfig.get());
    ServerEntry previouslySelected = serverList.selected();
    serverList.setServers(view);
    if (previouslySelected != null && view.contains(previouslySelected)) {
      serverList.setSelected(previouslySelected);
    }
  }

  private void toggleFavorite(ServerEntry entry) {
    GroundsConfig.get().toggleFavorite(entry.address);
    applyView();
  }

  private void toggleExpand(ServerEntry entry) {
    GroundsConfig.get().toggleExpanded(entry.name);
    applyView();
  }

  @Override
  public void tick() {
    pinger.tick();
    tickCounter++;
    if (tickCounter % RUNTIME_POLL_TICKS == 0) {
      refreshRuntimes();
    }
    if (tickCounter % READINESS_POLL_TICKS == 0) {
      pollPlatformReadiness();
    }
  }

  /** Reacts to a readiness probe: swap the banner in/out and refresh once the platform is back. */
  private void setPlatformReady(boolean ready) {
    if (ready == platformReady) {
      return;
    }
    platformReady = ready;
    rebuildWidgets(); // swaps the search box <-> the unreachable banner
    if (ready) {
      reloadServers();
    }
  }

  @Override
  public void removed() {
    pinger.removeAll();
  }

  private void pingAll(List<ServerEntry> list) {
    pinger.removeAll();
    for (ServerEntry entry : list) {
      try {
        entry.pinging = true;
        pinger.pingServer(
            entry.data,
            () -> entry.pinging = false,
            () -> entry.pinging = false,
            EventLoopGroupHolder.remote(false));
      } catch (Exception e) {
        entry.pinging = false;
      }
    }
  }

  /** Re-pulls per-server runtime so badges (replica count, paused/scaling) stay live. */
  private void refreshRuntimes() {
    String projectId = this.currentProjectId;
    if (projectId == null) {
      return;
    }
    loader.refreshRuntimes(projectId, model.entries());
  }

  private void onProjectChosen(Project project) {
    services.projects().selectProject(project);
    reloadServers();
  }

  private void logout() {
    actions.logout();
  }

  @Override
  public void onClose() {
    this.minecraft.setScreen(lastScreen);
  }

  boolean isCurrentScreen() {
    return minecraft != null && minecraft.screen == this;
  }

  Minecraft client() {
    return minecraft;
  }

  void setStatusMessage(Component message) {
    statusMessage = message == null ? Component.empty() : message;
    status.setMessage(statusMessage);
  }

  private void pollPlatformReadiness() {
    loader.pollPlatformReadiness();
  }

  private static String msg(Throwable t) {
    return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
  }

  private final class LoaderListener implements ServerDataLoader.Listener {
    @Override
    public void onProjectsLoaded(List<Project> projects) {
      if (isCurrentScreen()) {
        // Rebuild so the project picker is populated from the now-cached projects.
        minecraft.setScreen(new GroundsServersScreen(lastScreen));
      }
    }

    @Override
    public void onProjectsError(Throwable error) {
      if (isCurrentScreen()) {
        setStatusMessage(Component.translatable("grounds_connect.error.projects", msg(error)));
      }
    }

    @Override
    public boolean onServersLoaded(String projectId, List<GroundsServer> servers) {
      if (!isCurrentScreen() || !projectId.equals(currentProjectId)) {
        return false;
      }
      model.replaceServers(servers);
      applyView();
      pingAll(model.entries());
      setStatusMessage(
          model.entries().isEmpty()
              ? Component.translatable("grounds_connect.status.noServers")
              : null);
      return true;
    }

    @Override
    public void onServersError(String projectId, Throwable error) {
      if (isCurrentScreen() && projectId.equals(currentProjectId)) {
        setStatusMessage(Component.translatable("grounds_connect.error.servers", msg(error)));
      }
    }

    @Override
    public List<ServerEntry> runtimeEntries(String projectId) {
      if (!isCurrentScreen() || !projectId.equals(currentProjectId)) {
        return List.of();
      }
      return model.entries();
    }

    @Override
    public void onRuntimeResult(String projectId, ServerEntry entry, DeploymentRuntime runtime) {
      if (projectId.equals(currentProjectId)) {
        entry.runtime = runtime;
      }
    }

    @Override
    public void onRuntimeError(String projectId, ServerEntry entry, Throwable error) {
      // Keep the last known runtime.
    }

    @Override
    public void onPlatformReadiness(boolean ready) {
      if (isCurrentScreen()) {
        setPlatformReady(ready);
      }
    }
  }
}
