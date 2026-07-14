# Server Action Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the Grounds server screen around Join, Manage, and Back; move deployment operations into a dedicated server-management screen; and replace Refresh and Logout labels with compact icon buttons and tooltips.

**Architecture:** Pure package-private state objects define bottom-action order, selection restoration, role gating, and retry locking. `GroundsServersScreen` consumes those objects for its project and primary-server controls, while a new `ServerManagementScreen` owns Logs, Retry Build, Rollback, and their local feedback. Existing deployment, log, rollback, NATS, and connection services remain unchanged.

**Tech Stack:** Java 25, Minecraft 26.2 client API, Fabric Loom, JUnit Jupiter 6, Gradle, Spotless

## Global Constraints

- Main-screen top order is Project selector, Refresh icon, NATS, Logout icon.
- Main-screen bottom order is Join, Manage, Back.
- Refresh and Logout are icon-only 20x20 Minecraft buttons with localized hover tooltips and descriptive narration.
- Use the Minecraft-font glyphs `↻` and `⇥`; add no bitmap assets or dependencies.
- Join and Manage require both `ServerContentState.CONTENT` and a selected server.
- NATS and Refresh depend on a selected project, not on server rows or server selection.
- Management order is Open Logs, Retry Build, Rollback, Back.
- Rollback is visible but disabled outside the `owner` and `editor` roles and exposes a localized explanatory tooltip.
- Retry Build rejects duplicate presses until its asynchronous success or failure callback completes.
- Returning to the server list preserves search text and restores the selected visible server.
- Follow the repository logging rules; add no logs for expected UI states or deployment failures.
- Use Conventional Commits and keep `.superpowers/` Visual Companion state and `logs/` out of commits.
- After Java or resource changes, run `./gradlew test`, `./gradlew spotlessApply`, and `./gradlew build` with escalated permissions.

---

### Task 1: Testable control, selection, and management state

**Files:**
- Create: `src/main/java/gg/grounds/connect/ui/servers/ServerScreenControls.java`
- Create: `src/main/java/gg/grounds/connect/ui/servers/ServerListSelection.java`
- Create: `src/main/java/gg/grounds/connect/ui/servers/ServerManagementState.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/ServerScreenControlsTest.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/ServerListSelectionTest.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/ServerManagementStateTest.java`

**Interfaces:**
- Consumes: existing `ServerContentState` and `ServerEntry` values.
- Produces: `ServerScreenControls.forState(ServerContentState, boolean)`, `ServerScreenControls.bottomOrder()`, mutable `ServerListSelection`, and mutable `ServerManagementState`.

- [ ] **Step 1: Write failing tests for control order and availability**

Create `ServerScreenControlsTest.java`:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ServerScreenControlsTest {
  @Test
  void ordersJoinBeforeManageAndBack() {
    assertEquals(
        List.of(
            ServerScreenControls.BottomAction.JOIN,
            ServerScreenControls.BottomAction.MANAGE,
            ServerScreenControls.BottomAction.BACK),
        ServerScreenControls.bottomOrder());
  }

  @Test
  void enablesSelectedServerActionsOnlyForContentWithSelection() {
    assertFalse(ServerScreenControls.forState(ServerContentState.LOADING, true).joinActive());
    assertFalse(ServerScreenControls.forState(ServerContentState.UNAVAILABLE, true).manageActive());
    assertFalse(ServerScreenControls.forState(ServerContentState.CONTENT, false).joinActive());

    ServerScreenControls controls =
        ServerScreenControls.forState(ServerContentState.CONTENT, true);
    assertTrue(controls.joinActive());
    assertTrue(controls.manageActive());
  }
}
```

- [ ] **Step 2: Write failing tests for selection restoration**

Create `ServerListSelectionTest.java`:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import org.junit.jupiter.api.Test;

final class ServerListSelectionTest {
  @Test
  void restoresASelectedVisibleServerAfterScreenRebuild() {
    ServerEntry lobby = entry("lobby");
    ServerEntry survival = entry("survival");
    ServerListSelection selection = new ServerListSelection();

    selection.remember(survival);

    assertSame(survival, selection.restore(List.of(lobby, survival)));
    assertEquals("survival", selection.selectedName());
  }

  @Test
  void clearsSelectionWhenTheServerIsNoLongerVisible() {
    ServerListSelection selection = new ServerListSelection();
    selection.remember(entry("survival"));

    assertNull(selection.restore(List.of(entry("lobby"))));
    assertNull(selection.selectedName());
  }

  private static ServerEntry entry(String name) {
    return new ServerEntry(
        name,
        "localhost",
        new ServerData(name, "localhost", ServerData.Type.OTHER),
        "ready",
        "paper");
  }
}
```

- [ ] **Step 3: Write failing tests for role gating and retry locking**

Create `ServerManagementStateTest.java`:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ServerManagementStateTest {
  @Test
  void ordersLogsRetryRollbackAndBack() {
    assertEquals(
        List.of(
            ServerManagementState.Action.LOGS,
            ServerManagementState.Action.RETRY_BUILD,
            ServerManagementState.Action.ROLLBACK,
            ServerManagementState.Action.BACK),
        ServerManagementState.actionOrder());
  }

  @Test
  void permitsRollbackForOwnerAndEditorOnly() {
    assertTrue(ServerManagementState.canRollback("owner"));
    assertTrue(ServerManagementState.canRollback("EDITOR"));
    assertFalse(ServerManagementState.canRollback("viewer"));
    assertFalse(ServerManagementState.canRollback(null));
  }

  @Test
  void rejectsDuplicateRetryUntilTheCallbackCompletes() {
    ServerManagementState state = new ServerManagementState("owner");

    assertTrue(state.beginRetry());
    assertFalse(state.beginRetry());
    assertFalse(state.retryActive());

    state.finishRetry();

    assertTrue(state.retryActive());
    assertTrue(state.beginRetry());
  }
}
```

- [ ] **Step 4: Run the focused tests and verify the red state**

Run with escalated permissions:

```bash
./gradlew test --tests 'gg.grounds.connect.ui.servers.Server*StateTest' --tests gg.grounds.connect.ui.servers.ServerScreenControlsTest --tests gg.grounds.connect.ui.servers.ServerListSelectionTest
```

Expected: `compileTestJava` fails because `ServerScreenControls`, `ServerListSelection`, and `ServerManagementState` do not exist.

- [ ] **Step 5: Implement the control model**

Create `ServerScreenControls.java`:

```java
package gg.grounds.connect.ui.servers;

import java.util.List;

record ServerScreenControls(boolean joinActive, boolean manageActive) {
  enum BottomAction {
    JOIN,
    MANAGE,
    BACK
  }

  private static final List<BottomAction> BOTTOM_ORDER =
      List.of(BottomAction.JOIN, BottomAction.MANAGE, BottomAction.BACK);

  static ServerScreenControls forState(ServerContentState state, boolean hasSelectedServer) {
    boolean selectedServerActionsActive = state.serverActionsActive() && hasSelectedServer;
    return new ServerScreenControls(selectedServerActionsActive, selectedServerActionsActive);
  }

  static List<BottomAction> bottomOrder() {
    return BOTTOM_ORDER;
  }
}
```

- [ ] **Step 6: Implement selection memory**

Create `ServerListSelection.java`:

```java
package gg.grounds.connect.ui.servers;

import java.util.List;

final class ServerListSelection {
  private String selectedName;

  void remember(ServerEntry entry) {
    selectedName = entry == null ? null : entry.name;
  }

  void clear() {
    selectedName = null;
  }

  String selectedName() {
    return selectedName;
  }

  ServerEntry restore(List<ServerEntry> visibleEntries) {
    if (selectedName == null) {
      return null;
    }
    for (ServerEntry entry : visibleEntries) {
      if (selectedName.equals(entry.name)) {
        return entry;
      }
    }
    clear();
    return null;
  }
}
```

- [ ] **Step 7: Implement role and retry state**

Create `ServerManagementState.java`:

```java
package gg.grounds.connect.ui.servers;

import java.util.List;

final class ServerManagementState {
  enum Action {
    LOGS,
    RETRY_BUILD,
    ROLLBACK,
    BACK
  }

  private static final List<Action> ACTION_ORDER =
      List.of(Action.LOGS, Action.RETRY_BUILD, Action.ROLLBACK, Action.BACK);
  private final boolean rollbackActive;
  private boolean retrying;

  ServerManagementState(String projectRole) {
    rollbackActive = canRollback(projectRole);
  }

  boolean beginRetry() {
    if (retrying) {
      return false;
    }
    retrying = true;
    return true;
  }

  void finishRetry() {
    retrying = false;
  }

  boolean retryActive() {
    return !retrying;
  }

  boolean rollbackActive() {
    return rollbackActive;
  }

  static List<Action> actionOrder() {
    return ACTION_ORDER;
  }

  static boolean canRollback(String role) {
    return "owner".equalsIgnoreCase(role) || "editor".equalsIgnoreCase(role);
  }
}
```

- [ ] **Step 8: Run the focused tests and verify the green state**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerScreenControlsTest --tests gg.grounds.connect.ui.servers.ServerListSelectionTest --tests gg.grounds.connect.ui.servers.ServerManagementStateTest
```

Expected: all focused tests pass.

- [ ] **Step 9: Commit the state models**

```bash
git add src/main/java/gg/grounds/connect/ui/servers/ServerScreenControls.java src/main/java/gg/grounds/connect/ui/servers/ServerListSelection.java src/main/java/gg/grounds/connect/ui/servers/ServerManagementState.java src/test/java/gg/grounds/connect/ui/servers/ServerScreenControlsTest.java src/test/java/gg/grounds/connect/ui/servers/ServerListSelectionTest.java src/test/java/gg/grounds/connect/ui/servers/ServerManagementStateTest.java
git commit -m "feat(connect): model server action states"
```

---

### Task 2: Main-screen hierarchy and server-management screen

**Files:**
- Create: `src/main/java/gg/grounds/connect/ui/servers/ServerManagementScreen.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServerList.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/ServerScreenActions.java`
- Modify: `src/main/resources/assets/grounds_connect/lang/en_us.json`
- Modify: `src/main/resources/assets/grounds_connect/lang/de_de.json`
- Modify: `src/main/resources/assets/grounds_connect/lang/es_es.json`
- Modify: `src/main/resources/assets/grounds_connect/lang/fr_fr.json`

**Interfaces:**
- Consumes: all Task 1 state objects, existing `DeploymentService.retryLatestBuild`, `LogConsoleScreen`, `RollbackPickerScreen`, `NatsScreen`, and `ServerScreenActions.joinSelected()`.
- Produces: `ServerManagementScreen(Screen, String, String, String, String)`, selection-change callbacks from `GroundsServerList`, and the approved top/bottom control layout.

- [ ] **Step 1: Add a selection-change callback to the list**

Add the field, setter, and override to `GroundsServerList.java`:

```java
private Consumer<ServerEntry> onSelectionChanged;

public void setOnSelectionChanged(Consumer<ServerEntry> onSelectionChanged) {
  this.onSelectionChanged = onSelectionChanged;
}

@Override
public void setSelected(ServerEntry entry) {
  super.setSelected(entry);
  if (onSelectionChanged != null) {
    onSelectionChanged.accept(entry);
  }
}
```

- [ ] **Step 2: Create the management screen**

Create `ServerManagementScreen.java`:

```java
package gg.grounds.connect.ui.servers;

import gg.grounds.connect.Grounds;
import gg.grounds.connect.ui.RollbackPickerScreen;
import gg.grounds.connect.ui.ScreenNavigation;
import gg.grounds.connect.ui.logs.LogConsoleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ServerManagementScreen extends Screen {
  private final Screen parent;
  private final String serverName;
  private final String projectId;
  private final String projectName;
  private final ServerManagementState state;
  private Button retryButton;
  private Button rollbackButton;
  private StringWidget status;

  ServerManagementScreen(
      Screen parent,
      String serverName,
      String projectId,
      String projectName,
      String projectRole) {
    super(Component.translatable("grounds_connect.manage.title", serverName));
    this.parent = parent;
    this.serverName = serverName;
    this.projectId = projectId;
    this.projectName = projectName;
    this.state = new ServerManagementState(projectRole);
  }

  @Override
  protected void init() {
    addRenderableWidget(new StringWidget(width / 2 - 150, 16, 300, 12, title, font));
    addRenderableWidget(
        new StringWidget(
            width / 2 - 150,
            32,
            300,
            12,
            Component.translatable("grounds_connect.manage.project", projectName),
            font));

    int x = width / 2 - 100;
    int y = height / 2 - 42;
    for (int index = 0; index < ServerManagementState.actionOrder().size(); index++) {
      ServerManagementState.Action action = ServerManagementState.actionOrder().get(index);
      Button widget =
          switch (action) {
            case LOGS ->
                Button.builder(
                        Component.translatable("grounds_connect.action.logs"),
                        button -> openLogs())
                    .bounds(x, y + index * 24, 200, 20)
                    .build();
            case RETRY_BUILD ->
                retryButton =
                    Button.builder(
                            Component.translatable("grounds_connect.action.retryBuild"),
                            button -> retryBuild())
                        .bounds(x, y + index * 24, 200, 20)
                        .build();
            case ROLLBACK ->
                rollbackButton =
                    Button.builder(
                            Component.translatable("grounds_connect.action.rollback"),
                            button -> openRollback())
                        .bounds(x, y + index * 24, 200, 20)
                        .build();
            case BACK ->
                Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                    .bounds(x, height - 28, 200, 20)
                    .build();
          };
      addRenderableWidget(widget);
    }
    if (!state.rollbackActive()) {
      rollbackButton.setTooltip(
          Tooltip.create(
              Component.translatable("grounds_connect.status.rollbackRoleRequired")));
    }
    status =
        new StringWidget(width / 2 - 150, y + 78, 300, 12, Component.empty(), font);
    addRenderableWidget(status);
    applyActionState();
  }

  private void openLogs() {
    minecraft.setScreenAndShow(new LogConsoleScreen(this, serverName, projectId));
  }

  private void retryBuild() {
    if (!state.beginRetry()) {
      return;
    }
    setStatus(Component.translatable("grounds_connect.status.retryingBuild", serverName));
    applyActionState();
    Grounds.services()
        .deployments()
        .retryLatestBuild(serverName, projectId, this::onRetryStarted, this::onRetryError);
  }

  private void onRetryStarted() {
    state.finishRetry();
    if (isCurrentScreen()) {
      setStatus(Component.translatable("grounds_connect.status.retryStarted"));
      applyActionState();
    }
  }

  private void onRetryError(String error) {
    state.finishRetry();
    if (isCurrentScreen()) {
      setStatus(Component.translatable("grounds_connect.error.retry", error));
      applyActionState();
    }
  }

  private void openRollback() {
    if (state.rollbackActive()) {
      minecraft.setScreenAndShow(new RollbackPickerScreen(this, serverName, projectId));
    }
  }

  private void applyActionState() {
    retryButton.active = state.retryActive();
    rollbackButton.active = state.rollbackActive();
  }

  private void setStatus(Component message) {
    status.setMessage(message);
  }

  private boolean isCurrentScreen() {
    return minecraft != null && minecraft.gui.screen() == this;
  }

  @Override
  public void onClose() {
    ScreenNavigation.show(minecraft, parent);
  }
}
```

- [ ] **Step 3: Replace the main-screen fields and wire selection memory**

In `GroundsServersScreen.java`, replace `serverActionWidgets` with explicit state and widget fields:

```java
private final ServerListSelection selection = new ServerListSelection();
private ServerContentState contentState = ServerContentState.UNAVAILABLE;
private AbstractWidget refreshButton;
private AbstractWidget natsButton;
private AbstractWidget joinButton;
private AbstractWidget manageButton;
```

After constructing `serverList` in `init()`, register selection changes:

```java
serverList.setOnSelectionChanged(
    entry -> {
      selection.remember(entry);
      applyContentState();
    });
```

In `applyView()`, replace the previous-selection block with selection restoration:

```java
List<ServerEntry> view = model.visibleEntries(GroundsConfig.get());
serverList.setServers(view);
serverList.setSelected(selection.restore(view));
```

In both `beginLoading(...)` and `showUnavailable(...)`, call `selection.clear()` immediately before clearing the selected list entry.

- [ ] **Step 4: Build icon-only project controls in the approved order**

Add imports for `Tooltip` and replace the current Refresh and Logout construction with:

```java
refreshButton =
    Button.builder(Component.literal("↻"), button -> reloadServers())
        .bounds(192, row, 20, 20)
        .tooltip(
            Tooltip.create(
                Component.translatable("grounds_connect.control.refresh.tooltip")))
        .createNarration(
            ignored ->
                Component.translatable("grounds_connect.control.refresh.tooltip"))
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
```

Remove the lower-row NATS button. Keep the field assignment for the top-row NATS button so project availability remains controlled by `applyContentState()`.

- [ ] **Step 5: Build Join, Manage, and Back from the tested order**

Replace both existing bottom action rows with:

```java
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
```

Add the navigation method:

```java
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
          project.role()));
}
```

- [ ] **Step 6: Apply project and selected-server availability**

Replace the server-widget loop in `applyContentState()` with:

```java
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
```

Delete `serverActionWidgets` and `addServerActionWidget(...)` because action availability is now explicit.

- [ ] **Step 7: Remove deployment actions from the main-screen action helper**

Delete `openLogs()`, `retryBuild()`, and `rollbackSelected()` from `ServerScreenActions.java`. Remove the now-unused `RollbackPickerScreen` and `LogConsoleScreen` imports; retain `Project` for `openNats()`. Keep `joinSelected()`, `openNats()`, `logout()`, and the wake/connect methods unchanged.

- [ ] **Step 8: Add localized management copy**

Add these keys to `en_us.json`, `es_es.json`, and `fr_fr.json`:

```json
"grounds_connect.action.manage": "Manage…",
"grounds_connect.action.retryBuild": "Retry build",
"grounds_connect.manage.title": "%s management",
"grounds_connect.manage.project": "Project: %s",
```

Add these keys to `de_de.json`:

```json
"grounds_connect.action.manage": "Verwalten…",
"grounds_connect.action.retryBuild": "Build wiederholen",
"grounds_connect.manage.title": "%s verwalten",
"grounds_connect.manage.project": "Projekt: %s",
```

Retain the existing localized Refresh tooltip, Logout label, Logs, Rollback, retry status, retry error, and role-required strings.

- [ ] **Step 9: Run focused and full verification**

Run each command separately with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerScreenControlsTest --tests gg.grounds.connect.ui.servers.ServerListSelectionTest --tests gg.grounds.connect.ui.servers.ServerManagementStateTest
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Expected: every command ends with `BUILD SUCCESSFUL`. Spotless may change Java formatting; inspect the resulting diff before committing.

- [ ] **Step 10: Inspect the final scoped diff**

```bash
git diff --check
git status --short
git diff -- src/main/java/gg/grounds/connect/ui/servers src/test/java/gg/grounds/connect/ui/servers src/main/resources/assets/grounds_connect/lang
```

Expected: only the planned Java, test, and localization files are modified. `.superpowers/` and `logs/` remain untracked and unstaged.

- [ ] **Step 11: Commit the UI hierarchy**

```bash
git add src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java src/main/java/gg/grounds/connect/ui/servers/GroundsServerList.java src/main/java/gg/grounds/connect/ui/servers/ServerManagementScreen.java src/main/java/gg/grounds/connect/ui/servers/ServerScreenActions.java src/main/resources/assets/grounds_connect/lang/en_us.json src/main/resources/assets/grounds_connect/lang/de_de.json src/main/resources/assets/grounds_connect/lang/es_es.json src/main/resources/assets/grounds_connect/lang/fr_fr.json
git commit -m "feat(connect): reorganize server actions"
```

- [ ] **Step 12: Manually verify the Minecraft interaction**

Launch the development client and confirm:

1. Top controls appear as Project, `↻`, NATS, `⇥`; both icons show the correct localized tooltip.
2. Bottom controls appear as Join, Manage, Back.
3. Join and Manage stay disabled until a server row is selected.
4. Refresh clears the old rows, shows the loader, and does not enable Join or Manage early.
5. Manage opens the selected server's screen with Logs, Retry Build, Rollback, and Back.
6. A viewer sees disabled Rollback with the role tooltip; an owner/editor can open the picker.
7. Double-clicking Retry Build while the request is pending sends only one request.
8. Returning from Logs, Rollback, or Management restores the prior search and visible selection.

Expected: all eight behaviors match the approved design without changing Join, NATS, logging, retry, or rollback service semantics.
