package gg.grounds.connect.ui.servers;

import gg.grounds.connect.Grounds;
import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.config.GroundsConfig;
import gg.grounds.connect.core.GroundsServices;
import gg.grounds.connect.core.RequestCoalescer;
import java.net.UnknownHostException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
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
  private final ServerPingScheduler pingScheduler =
      new ServerPingScheduler(services::executeInBackground);
  private final ServerScreenActions actions;
  private final ServerStatusPinger pinger = new ServerStatusPinger();

  private static final int RUNTIME_POLL_TICKS = 100; // ~5s
  private static final int READINESS_POLL_TICKS = 200; // ~10s

  private boolean projectsRequested;
  private boolean initialLoadDone;
  private boolean platformReady = true; // assume up until a probe says otherwise
  private final ServerListModel model = new ServerListModel();
  private final ServerListSelection selection = new ServerListSelection();
  private final RequestCoalescer retryRequests = new RequestCoalescer();
  private final ServerScreenLifecycle lifecycle = new ServerScreenLifecycle();
  private ServerContentState contentState = ServerContentState.UNAVAILABLE;
  private String currentProjectId;
  private Component loadingMessage =
      Component.translatable("grounds_connect.status.loadingProjects");
  private Component statusMessage = Component.empty();
  private int tickCounter;

  private GroundsServerList serverList;
  private AbstractWidget projectButton; // CycleButton<Project> or a disabled placeholder
  private AbstractWidget refreshButton;
  private AbstractWidget natsButton;
  private AbstractWidget joinButton;
  private AbstractWidget manageButton;
  private EditBox search;
  private LoadingDotsWidget loadingIndicator;
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
    serverList.setOnSelectionChanged(
        entry -> {
          selection.remember(entry);
          applyContentState();
        });
    serverList.setOnToggleFavorite(this::toggleFavorite);
    serverList.setOnToggleExpand(this::toggleExpand);
    addRenderableWidget(serverList);

    projectButton = buildProjectButton(row);
    addRenderableWidget(projectButton);
    refreshButton =
        Button.builder(Component.literal("↻"), button -> reloadServers())
            .bounds(192, row, 20, 20)
            .tooltip(
                Tooltip.create(Component.translatable("grounds_connect.control.refresh.tooltip")))
            .createNarration(
                ignored -> Component.translatable("grounds_connect.control.refresh.tooltip"))
            .build();
    addRenderableWidget(refreshButton);
    natsButton =
        Button.builder(Component.literal("NATS"), button -> actions.openNats())
            .bounds(216, row, 60, 20)
            .build();
    addRenderableWidget(natsButton);
    addRenderableWidget(
        Button.builder(Component.literal("⇥"), button -> logout())
            .bounds(width - 28, row, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("grounds_connect.menu.logout")))
            .createNarration(ignored -> Component.translatable("grounds_connect.menu.logout"))
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

    loadingIndicator = new LoadingDotsWidget(this.font, loadingMessage);
    loadingIndicator.setX(this.width / 2 - loadingIndicator.getWidth() / 2);
    loadingIndicator.setY(listTop + Math.max(0, (listHeight - loadingIndicator.getHeight()) / 2));
    addRenderableWidget(loadingIndicator);

    int buttonY = height - 28;
    for (int index = 0; index < ServerScreenControls.bottomOrder().size(); index++) {
      ServerScreenControls.BottomAction action = ServerScreenControls.bottomOrder().get(index);
      int buttonX = width / 2 - 153 + index * 103;
      AbstractWidget widget =
          switch (action) {
            case JOIN ->
                joinButton =
                    Button.builder(
                            Component.translatable("grounds_connect.action.join"),
                            button -> actions.joinSelected())
                        .bounds(buttonX, buttonY, 100, 20)
                        .build();
            case MANAGE ->
                manageButton =
                    Button.builder(
                            Component.translatable("grounds_connect.action.manage"),
                            button -> openManagement())
                        .bounds(buttonX, buttonY, 100, 20)
                        .build();
            case BACK ->
                Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                    .bounds(buttonX, buttonY, 100, 20)
                    .build();
          };
      addRenderableWidget(widget);
    }

    applyView();
    applyContentState();

    // Only fetch on the first build — later rebuilds (resize, banner toggle) just repaint.
    if (!initialLoadDone) {
      initialLoadDone = true;
      if (services.auth().isLoggedIn()) {
        if (services.projects().cachedProjects().isEmpty() && !projectsRequested) {
          projectsRequested = true;
          beginLoading(Component.translatable("grounds_connect.status.loadingProjects"));
          loader.loadProjects();
        } else {
          reloadServers();
        }
      } else {
        showUnavailable(Component.translatable("grounds_connect.control.login"));
      }
    }
    resumePings(lifecycle.onInitialized(contentState, model.entries()));
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
      currentProjectId = null;
      showUnavailable(Component.translatable("grounds_connect.status.noProjects"));
      return;
    }
    final String projectId = selected.id();
    this.currentProjectId = projectId;
    beginLoading(Component.translatable("grounds_connect.status.loadingServers"));
    loader.loadServers(projectId);
  }

  private void applyView() {
    if (serverList == null) {
      return;
    }
    List<ServerEntry> view = model.visibleEntries(GroundsConfig.get());
    serverList.setServers(view);
    serverList.setSelected(selection.restore(view));
  }

  private void beginLoading(Component message) {
    contentState = ServerContentState.LOADING;
    loadingMessage = message;
    model.clear();
    if (serverList != null) {
      selection.clear();
      serverList.setSelected(null);
    }
    applyView();
    setStatusMessage(null);
    updateLoadingIndicator();
    applyContentState();
  }

  private void showUnavailable(Component message) {
    contentState = ServerContentState.UNAVAILABLE;
    model.clear();
    if (serverList != null) {
      selection.clear();
      serverList.setSelected(null);
    }
    applyView();
    setStatusMessage(message);
    applyContentState();
  }

  private void updateLoadingIndicator() {
    if (loadingIndicator == null) {
      return;
    }
    loadingIndicator.setMessage(loadingMessage);
    loadingIndicator.setWidth(this.font.width(loadingMessage));
    loadingIndicator.setX(this.width / 2 - loadingIndicator.getWidth() / 2);
  }

  private void applyContentState() {
    if (serverList != null) {
      serverList.visible = contentState.listVisible();
    }
    if (loadingIndicator != null) {
      loadingIndicator.visible = contentState.loaderVisible();
    }
    if (status != null) {
      status.visible = !contentState.loaderVisible();
    }
    ServerScreenControls controls =
        ServerScreenControls.forState(
            contentState, serverList != null && serverList.selected() != null);
    if (joinButton != null) {
      joinButton.active = controls.joinActive();
    }
    if (manageButton != null) {
      manageButton.active = controls.manageActive();
    }
    boolean projectActionsActive = ServerContentState.projectActionsActive(currentProjectId);
    if (refreshButton != null) {
      refreshButton.active = projectActionsActive;
    }
    if (natsButton != null) {
      natsButton.active = projectActionsActive;
    }
  }

  private void openManagement() {
    ServerEntry entry = serverList == null ? null : serverList.selected();
    Project project = services.projects().selectedProject();
    if (entry == null || project == null || currentProjectId == null) {
      return;
    }
    minecraft.setScreenAndShow(
        new ServerManagementScreen(
            this,
            entry.name,
            currentProjectId,
            project.displayName(),
            project.role(),
            retryRequests));
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
    lifecycle.onRemoved();
    pinger.removeAll();
  }

  private void pingAll(List<ServerEntry> list) {
    pinger.removeAll();
    schedulePings(list);
  }

  private void resumePings(List<ServerEntry> list) {
    schedulePings(list);
  }

  private void schedulePings(List<ServerEntry> list) {
    for (ServerEntry entry : list) {
      VanillaServerPingState.start(entry.data);
      pingScheduler.submit(
          () -> {
            try {
              pinger.pingServer(
                  entry.data,
                  () -> {},
                  () ->
                      VanillaServerPingState.complete(
                          entry.data, SharedConstants.getCurrentVersion().protocolVersion()),
                  EventLoopGroupHolder.remote(false));
            } catch (UnknownHostException _) {
              VanillaServerPingState.fail(
                  entry.data, Component.translatable("multiplayer.status.cannot_resolve"));
            } catch (Exception _) {
              VanillaServerPingState.fail(
                  entry.data, Component.translatable("multiplayer.status.cannot_connect"));
            }
          });
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
    this.minecraft.gui.setScreen(lastScreen);
  }

  boolean isCurrentScreen() {
    return minecraft != null && minecraft.gui.screen() == this;
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
    private void finishServerLoad() {
      contentState = ServerContentState.afterServerLoad(model.entries().size());
      setStatusMessage(
          model.entries().isEmpty()
              ? Component.translatable("grounds_connect.status.noServers")
              : null);
      applyContentState();
    }

    @Override
    public void onProjectsLoaded(List<Project> projects) {
      if (isCurrentScreen()) {
        // Rebuild so the project picker is populated from the now-cached projects.
        minecraft.setScreenAndShow(new GroundsServersScreen(lastScreen));
      }
    }

    @Override
    public void onProjectsError(Throwable error) {
      if (isCurrentScreen()) {
        showUnavailable(Component.translatable("grounds_connect.error.projects", msg(error)));
      }
    }

    @Override
    public boolean onServersLoaded(String projectId, List<GroundsServer> servers) {
      if (!lifecycle.acceptsServerLoad(currentProjectId, projectId)) {
        return false;
      }
      model.replaceServers(servers);
      if (isCurrentScreen()) {
        applyView();
        pingAll(model.entries());
      }
      finishServerLoad();
      return true;
    }

    @Override
    public void onServersError(String projectId, Throwable error) {
      if (lifecycle.acceptsServerLoad(currentProjectId, projectId)) {
        showUnavailable(Component.translatable("grounds_connect.error.servers", msg(error)));
      }
    }

    @Override
    public List<ServerEntry> runtimeEntries(String projectId) {
      if (!lifecycle.acceptsServerLoad(currentProjectId, projectId)) {
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
