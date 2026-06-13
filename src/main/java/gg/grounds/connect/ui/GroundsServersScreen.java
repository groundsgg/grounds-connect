package gg.grounds.connect.ui;

import gg.grounds.connect.Grounds;
import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.config.GroundsConfig;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.core.GroundsServices;
import gg.grounds.connect.ui.GroundsServerList.ServerEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Grounds screen: a project picker plus the selected project's connectable Minecraft
 * servers, each with a forge-runtime health badge. (The player's own saved servers live in the
 * vanilla multiplayer screen, so we don't duplicate them here.) Opened from the main menu.
 */
public final class GroundsServersScreen extends Screen {

    private final Screen lastScreen;
    private final GroundsServices services = Grounds.services();
    private final ServerStatusPinger pinger = new ServerStatusPinger();

    private static final int RUNTIME_POLL_TICKS = 100;   // ~5s
    private static final int READINESS_POLL_TICKS = 200; // ~10s

    private boolean projectsRequested;
    private boolean initialLoadDone;
    private boolean platformReady = true; // assume up until a probe says otherwise
    private List<ServerEntry> entries = new ArrayList<>();      // flat: every server (incl. backends), for polling
    private List<ServerEntry> topLevel = new ArrayList<>();     // tree roots: proxies + standalone servers
    private String currentProjectId;
    private String searchText = "";
    private Component statusMessage = Component.empty();
    private int tickCounter;

    private GroundsServerList serverList;
    private AbstractWidget projectButton;  // CycleButton<Project> or a disabled placeholder
    private EditBox search;
    private StringWidget status;

    public GroundsServersScreen(Screen lastScreen) {
        super(Component.translatable("grounds_connect.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int row = 6;
        int searchY = 30;
        int listTop = 54;
        int listHeight = Math.max(20, this.height - 60 - listTop);

        serverList = new GroundsServerList(this.minecraft, this.width, listHeight, listTop, 32);
        serverList.setOnJoin(this::joinSelected);
        serverList.setOnToggleFavorite(this::toggleFavorite);
        serverList.setOnToggleExpand(this::toggleExpand);
        addRenderableWidget(serverList);

        projectButton = buildProjectButton(row);
        addRenderableWidget(projectButton);
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.control.refresh"), b -> reloadServers())
                .bounds(192, row, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.menu.logout"), b -> logout())
                .bounds(this.width - 104, row, 100, 20).build());

        if (platformReady) {
            search = new EditBox(this.font, this.width / 2 - 150, searchY, 300, 18,
                    Component.translatable("grounds_connect.search.hint"));
            search.setHint(Component.translatable("grounds_connect.search.hint"));
            search.setMaxLength(64);
            search.setValue(searchText);
            search.setResponder(value -> {
                searchText = value;
                applyView();
            });
            addRenderableWidget(search);
        } else {
            // Platform unreachable — the search box is useless, so show a banner in its place.
            StringWidget banner = new StringWidget(this.width / 2 - 150, searchY + 4, 300, 12,
                    Component.translatable("grounds_connect.platform.unreachable")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    this.font);
            addRenderableWidget(banner);
        }

        status = new StringWidget(this.width / 2 - 150, listTop + 16, 300, 12, statusMessage, this.font);
        addRenderableWidget(status);

        int recoveryY = this.height - 52;
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.action.retry"), b -> retryBuild())
                .bounds(this.width / 2 - 153, recoveryY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.action.rollback"), b -> rollbackSelected())
                .bounds(this.width / 2 - 50, recoveryY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NATS"), b -> openNats())
                .bounds(this.width / 2 + 53, recoveryY, 100, 20).build());

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.action.join"), b -> joinSelected())
                .bounds(this.width / 2 - 153, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.action.logs"), b -> openLogs())
                .bounds(this.width / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 + 53, by, 100, 20).build());

        applyView();

        // Only fetch on the first build — later rebuilds (resize, banner toggle) just repaint.
        if (!initialLoadDone) {
            initialLoadDone = true;
            if (services.auth().isLoggedIn()) {
                if (services.projects().cachedProjects().isEmpty() && !projectsRequested) {
                    projectsRequested = true;
                    setStatus(Component.translatable("grounds_connect.status.loadingProjects"));
                    loadProjects();
                } else {
                    reloadServers();
                }
            } else {
                setStatus(Component.translatable("grounds_connect.control.login"));
            }
        }
    }

    private AbstractWidget buildProjectButton(int y) {
        Project selected = services.auth().isLoggedIn() ? services.projects().selectedProject() : null;
        if (selected != null && !services.projects().cachedProjects().isEmpty()) {
            return CycleButton.builder((Project p) -> Component.literal(p.displayName()), selected)
                    .withValues(services.projects().cachedProjects())
                    .create(8, y, 180, 20, Component.translatable("grounds_connect.control.project.prefix"),
                            (btn, value) -> onProjectChosen(value));
        }
        Button placeholder = Button.builder(
                        Component.translatable(services.auth().isLoggedIn()
                                ? "grounds_connect.control.project.loading" : "grounds_connect.control.project.placeholder"),
                        b -> {})
                .bounds(8, y, 180, 20).build();
        placeholder.active = false;
        return placeholder;
    }

    // --- data ---------------------------------------------------------------

    private void loadProjects() {
        services.projects().fetch(new AsyncCallback<>() {
            @Override
            public void onResult(List<Project> value) {
                if (isCurrent()) {
                    // Rebuild so the project picker is populated from the now-cached projects.
                    minecraft.setScreen(new GroundsServersScreen(lastScreen));
                }
            }

            @Override
            public void onError(Throwable error) {
                if (isCurrent()) {
                    setStatus(Component.translatable("grounds_connect.error.projects", msg(error)));
                }
            }
        });
    }

    private void reloadServers() {
        Project selected = services.auth().isLoggedIn() ? services.projects().selectedProject() : null;
        if (selected == null) {
            entries = new ArrayList<>();
            topLevel = new ArrayList<>();
            currentProjectId = null;
            applyView();
            setStatus(Component.translatable("grounds_connect.status.noProjects"));
            return;
        }
        setStatus(Component.translatable("grounds_connect.status.loadingServers"));
        final String projectId = selected.id();
        this.currentProjectId = projectId;
        services.servers().fetchServers(projectId, new AsyncCallback<>() {
            @Override
            public void onResult(List<GroundsServer> value) {
                if (!isCurrent()) {
                    return;
                }
                List<ServerEntry> built = new ArrayList<>();
                List<ServerEntry> proxies = new ArrayList<>();
                List<ServerEntry> backends = new ArrayList<>();
                List<ServerEntry> standalone = new ArrayList<>();
                for (GroundsServer s : value) {
                    ServerData data = new ServerData(s.name(), s.address(), ServerData.Type.OTHER);
                    ServerEntry entry = new ServerEntry(s.name(), s.address(), data, s.state(), s.type());
                    built.add(entry);
                    if (s.isVelocityProxy()) {
                        proxies.add(entry);
                    } else if (s.isBackend()) {
                        backends.add(entry);
                    } else {
                        standalone.add(entry);
                    }
                }
                entries = built;
                topLevel = buildTree(proxies, backends, standalone);
                applyView();
                pingAll(built);
                setStatus(built.isEmpty() ? Component.translatable("grounds_connect.status.noServers") : null);
                for (ServerEntry entry : built) {
                    services.servers().fetchRuntime(entry.name, projectId, new AsyncCallback<>() {
                        @Override
                        public void onResult(DeploymentRuntime rt) {
                            entry.runtime = rt;
                        }

                        @Override
                        public void onError(Throwable error) {
                            // leave UNKNOWN
                        }
                    });
                }
                services.pushes().watchInFlightPushes(projectId, DeployToasts::onStatus);
            }

            @Override
            public void onError(Throwable error) {
                if (isCurrent()) {
                    setStatus(Component.translatable("grounds_connect.error.servers", msg(error)));
                }
            }
        });
    }

    /**
     * Builds the display tree: Velocity proxies and standalone servers are roots; backend game
     * servers nest under the project's proxy (single-proxy-per-project assumption — see notes).
     * With no proxy in the project, backends stay top-level joinable (e.g. a standalone gamemode).
     */
    private List<ServerEntry> buildTree(List<ServerEntry> proxies, List<ServerEntry> backends,
                                        List<ServerEntry> standalone) {
        List<ServerEntry> roots = new ArrayList<>();
        roots.addAll(proxies);
        roots.addAll(standalone);
        if (proxies.isEmpty()) {
            roots.addAll(backends); // no proxy → backends are directly joinable
        } else {
            ServerEntry proxy = proxies.get(0); // can't attribute to a specific proxy from the API
            proxy.children = new ArrayList<>();
            for (ServerEntry b : backends) {
                b.depth = 1;
                proxy.children.add(b);
            }
        }
        return roots;
    }

    /** Flattens the tree into the visible list: roots (pins first), with each expanded proxy's backends after it. */
    private void applyView() {
        if (serverList == null) {
            return;
        }
        GroundsConfig cfg = GroundsConfig.get();
        String q = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);

        List<ServerEntry> roots = new ArrayList<>(topLevel);
        for (ServerEntry r : roots) {
            r.favorite = cfg.isFavorite(r.address);
            if (r.isProxy()) {
                r.expanded = cfg.isExpanded(r.name);
            }
        }
        roots.sort(Comparator.comparingInt((ServerEntry e) -> e.favorite ? 0 : 1)
                .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));

        List<ServerEntry> view = new ArrayList<>();
        for (ServerEntry r : roots) {
            boolean rootMatches = matches(r, q);
            List<ServerEntry> kids = r.children;
            boolean anyKidMatches = false;
            if (kids != null) {
                for (ServerEntry k : kids) {
                    if (matches(k, q)) {
                        anyKidMatches = true;
                        break;
                    }
                }
            }
            if (!q.isEmpty() && !rootMatches && !anyKidMatches) {
                continue;
            }
            view.add(r);
            if (kids != null && !kids.isEmpty() && (r.expanded || (!q.isEmpty() && anyKidMatches))) {
                List<ServerEntry> sortedKids = new ArrayList<>(kids);
                sortedKids.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
                for (ServerEntry k : sortedKids) {
                    if (q.isEmpty() || rootMatches || matches(k, q)) {
                        view.add(k);
                    }
                }
            }
        }

        ServerEntry previouslySelected = serverList.selected();
        serverList.setServers(view);
        if (previouslySelected != null && view.contains(previouslySelected)) {
            serverList.setSelected(previouslySelected);
        }
    }

    private static boolean matches(ServerEntry e, String q) {
        return q.isEmpty()
                || e.name.toLowerCase(Locale.ROOT).contains(q)
                || e.address.toLowerCase(Locale.ROOT).contains(q);
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
                pinger.pingServer(entry.data, () -> entry.pinging = false, () -> entry.pinging = false,
                        EventLoopGroupHolder.remote(false));
            } catch (Exception e) {
                entry.pinging = false;
            }
        }
    }

    /** Re-pulls per-server runtime so badges (replica count, paused/scaling) stay live. */
    private void refreshRuntimes() {
        String projectId = this.currentProjectId;
        if (projectId == null || entries.isEmpty()) {
            return;
        }
        for (ServerEntry entry : entries) {
            services.servers().fetchRuntime(entry.name, projectId, new AsyncCallback<>() {
                @Override
                public void onResult(DeploymentRuntime rt) {
                    entry.runtime = rt;
                }

                @Override
                public void onError(Throwable error) {
                    // keep the last known runtime
                }
            });
        }
        services.pushes().watchInFlightPushes(projectId, DeployToasts::onStatus);
    }

    // --- actions ------------------------------------------------------------

    private void onProjectChosen(Project project) {
        services.projects().selectProject(project);
        reloadServers();
    }

    private void joinSelected() {
        ServerEntry entry = serverList.selected();
        if (entry == null) {
            return;
        }
        if (entry.depth > 0 || entry.isMinestom()) { // backend / minestom — only reachable through the proxy
            setStatus(Component.translatable("grounds_connect.status.joinViaProxy"));
            return;
        }
        DeploymentRuntime.Health health = DeploymentRuntime.healthFor(entry.deploymentState, entry.runtime);
        if (health == DeploymentRuntime.Health.PAUSED && currentProjectId != null) {
            wakeThenConnect(entry);
        } else {
            connect(entry);
        }
    }

    private void connect(ServerEntry entry) {
        services.servers().markGroundsJoin(entry.address);
        ConnectScreen.startConnecting(this, this.minecraft,
                ServerAddress.parseString(entry.address), entry.data, false, null);
    }

    private void wakeThenConnect(ServerEntry entry) {
        setStatus(Component.translatable("grounds_connect.status.waking", entry.name));
        services.servers().resumeAndAwait(entry.name, currentProjectId,
                progress -> {
                    if (isCurrent()) {
                        setStatus(Component.translatable("grounds_connect.status.wakingProgress", entry.name, progress));
                    }
                },
                () -> {
                    if (isCurrent()) {
                        connect(entry);
                    }
                },
                error -> {
                    if (isCurrent()) {
                        setStatus(Component.translatable("grounds_connect.error.servers", error));
                    }
                });
    }

    private void openLogs() {
        ServerEntry entry = serverList.selected();
        if (entry == null || currentProjectId == null) {
            return;
        }
        this.minecraft.setScreen(new LogConsoleScreen(this, entry.name, currentProjectId));
    }

    private void retryBuild() {
        ServerEntry entry = serverList.selected();
        if (entry == null || currentProjectId == null) {
            return;
        }
        setStatus(Component.translatable("grounds_connect.status.retryingBuild", entry.name));
        services.deployments().retryLatestBuild(entry.name, currentProjectId,
                () -> {
                    if (isCurrent()) {
                        setStatus(Component.translatable("grounds_connect.status.retryStarted"));
                    }
                },
                error -> {
                    if (isCurrent()) {
                        setStatus(Component.translatable("grounds_connect.error.retry", error));
                    }
                });
    }

    private void rollbackSelected() {
        ServerEntry entry = serverList.selected();
        if (entry == null || currentProjectId == null) {
            return;
        }
        Project project = services.projects().selectedProject();
        String role = project != null ? project.role() : null;
        if (!"owner".equalsIgnoreCase(role) && !"editor".equalsIgnoreCase(role)) {
            setStatus(Component.translatable("grounds_connect.status.rollbackRoleRequired"));
            return;
        }
        this.minecraft.setScreen(new RollbackPickerScreen(this, entry.name, currentProjectId));
    }

    /** Opens the project's NATS view (broker/streams/events snapshot + live tail). Project-scoped. */
    private void openNats() {
        Project selected = services.projects().selectedProject();
        if (selected == null || currentProjectId == null) {
            setStatus(Component.translatable("grounds_connect.status.noProjects"));
            return;
        }
        this.minecraft.setScreen(new NatsScreen(this, currentProjectId, selected.displayName()));
    }

    private void logout() {
        services.logout();
        this.minecraft.setScreen(new TitleScreen());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    private boolean isCurrent() {
        return minecraft != null && minecraft.screen == this;
    }

    private void setStatus(Component message) {
        statusMessage = message == null ? Component.empty() : message;
        status.setMessage(statusMessage);
    }

    private void pollPlatformReadiness() {
        services.platform().pollReadiness(ready -> {
            if (isCurrent()) {
                setPlatformReady(ready);
            }
        });
    }

    private static String msg(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
