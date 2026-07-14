# Server List Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace stale Grounds server rows with Minecraft 26.2's animated `LoadingDotsWidget` during project and server loads.

**Architecture:** `GroundsServersScreen` owns a small explicit content state that controls the stable list surface, loader visibility, status visibility, and server-action availability. A package-private enum keeps those state semantics independent from Minecraft widgets and unit-testable; existing `currentProjectId` checks remain the authority for rejecting stale callbacks.

**Tech Stack:** Java 25, Minecraft 26.2 client GUI, Fabric, JUnit 6, Gradle, Spotless

## Global Constraints

- Use Minecraft 26.2's existing `LoadingDotsWidget`; do not add a custom animation or texture.
- Keep project selection, search, refresh, logout, and back navigation visible while loading.
- Disable `Join`, `Logs`, `Retry`, `Rollback`, and `NATS` while loading, empty, or failed.
- Clear old server rows and selection before dispatching each asynchronous server request.
- Keep the empty server-list widget visible behind the loader so the Vanilla background does not change.
- Ignore stale server responses and errors whose project ID does not equal `currentProjectId`.
- Do not add or modify log messages for this UI-only change.
- Run every Gradle command with escalated permissions.
- Keep the untracked `logs/` directory out of every commit.

---

### Task 1: Define and test server-content states

**Files:**
- Create: `src/main/java/gg/grounds/connect/ui/servers/ServerContentState.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/ServerContentStateTest.java`

**Interfaces:**
- Consumes: server count from a completed server-list request
- Produces: `ServerContentState.afterServerLoad(int)`, `listVisible()`, `loaderVisible()`, and `serverActionsActive()`

- [ ] **Step 1: Write the failing state tests**

Create `src/test/java/gg/grounds/connect/ui/servers/ServerContentStateTest.java`:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerContentStateTest {

  @Test
  void loadingKeepsListSurfaceBehindLoader() {
    assertTrue(ServerContentState.LOADING.listVisible());
    assertTrue(ServerContentState.LOADING.loaderVisible());
    assertFalse(ServerContentState.LOADING.serverActionsActive());
  }

  @Test
  void contentShowsListAndEnablesServerActions() {
    assertTrue(ServerContentState.CONTENT.listVisible());
    assertFalse(ServerContentState.CONTENT.loaderVisible());
    assertTrue(ServerContentState.CONTENT.serverActionsActive());
  }

  @Test
  void unavailableKeepsEmptyListSurfaceWithoutLoader() {
    assertTrue(ServerContentState.UNAVAILABLE.listVisible());
    assertFalse(ServerContentState.UNAVAILABLE.loaderVisible());
    assertFalse(ServerContentState.UNAVAILABLE.serverActionsActive());
  }

  @Test
  void completedLoadChoosesStateFromServerCount() {
    assertSame(ServerContentState.UNAVAILABLE, ServerContentState.afterServerLoad(0));
    assertSame(ServerContentState.CONTENT, ServerContentState.afterServerLoad(1));
  }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerContentStateTest
```

Expected: compilation fails because `ServerContentState` does not exist.

- [ ] **Step 3: Implement the minimal state enum**

Create `src/main/java/gg/grounds/connect/ui/servers/ServerContentState.java`:

```java
package gg.grounds.connect.ui.servers;

enum ServerContentState {
  LOADING(true, true, false),
  CONTENT(true, false, true),
  UNAVAILABLE(true, false, false);

  private final boolean listVisible;
  private final boolean loaderVisible;
  private final boolean serverActionsActive;

  ServerContentState(
      boolean listVisible, boolean loaderVisible, boolean serverActionsActive) {
    this.listVisible = listVisible;
    this.loaderVisible = loaderVisible;
    this.serverActionsActive = serverActionsActive;
  }

  boolean listVisible() {
    return listVisible;
  }

  boolean loaderVisible() {
    return loaderVisible;
  }

  boolean serverActionsActive() {
    return serverActionsActive;
  }

  static ServerContentState afterServerLoad(int serverCount) {
    return serverCount > 0 ? CONTENT : UNAVAILABLE;
  }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerContentStateTest
```

Expected: `BUILD SUCCESSFUL` and all four tests pass.

- [ ] **Step 5: Run the repository-required Gradle checks**

Run each command separately with escalated permissions:

```bash
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Expected: all three commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the tested state abstraction**

```bash
git add src/main/java/gg/grounds/connect/ui/servers/ServerContentState.java src/test/java/gg/grounds/connect/ui/servers/ServerContentStateTest.java
git commit -m "test(connect): define server loading states"
```

---

### Task 2: Wire the Vanilla loader into the Grounds server screen

**Files:**
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java:9-176`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java:206-231`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java:322-370`
- Test: `src/test/java/gg/grounds/connect/ui/servers/ServerContentStateTest.java`

**Interfaces:**
- Consumes: `ServerContentState` from Task 1 and Minecraft's `LoadingDotsWidget(Font, Component)`
- Produces: `beginLoading(Component)`, `showUnavailable(Component)`, `finishServerLoad()`, and `applyContentState()` inside `GroundsServersScreen`

- [ ] **Step 1: Add the loader and explicit widget state**

Add these imports to `GroundsServersScreen`:

```java
import java.util.ArrayList;
import net.minecraft.client.gui.components.LoadingDotsWidget;
```

Add these fields beside the existing screen state and widget fields:

```java
private final List<AbstractWidget> serverActionWidgets = new ArrayList<>();
private ServerContentState contentState = ServerContentState.UNAVAILABLE;
private Component loadingMessage =
    Component.translatable("grounds_connect.status.loadingProjects");

private LoadingDotsWidget loadingIndicator;
```

At the beginning of `init()`, clear widget references before rebuilding:

```java
serverActionWidgets.clear();
```

Immediately after adding `status`, create and center the Vanilla loader:

```java
loadingIndicator = new LoadingDotsWidget(this.font, loadingMessage);
loadingIndicator.setX(this.width / 2 - loadingIndicator.getWidth() / 2);
loadingIndicator.setY(listTop + Math.max(0, (listHeight - loadingIndicator.getHeight()) / 2));
addRenderableWidget(loadingIndicator);
```

Wrap the five server-related buttons with this helper instead of adding them directly:

```java
private <T extends AbstractWidget> T addServerActionWidget(T widget) {
  serverActionWidgets.add(widget);
  return addRenderableWidget(widget);
}
```

Use `addServerActionWidget(...)` for `Retry`, `Rollback`, `NATS`, `Join`, and `Logs`. Keep `Back`, refresh, project selection, logout, and search on `addRenderableWidget(...)`.

After the existing `applyView()` call at the end of widget construction, apply the current state:

```java
applyContentState();
```

- [ ] **Step 2: Add complete state-transition helpers**

Add these methods below `applyView()`:

```java
private void beginLoading(Component message) {
  contentState = ServerContentState.LOADING;
  loadingMessage = message;
  model.clear();
  if (serverList != null) {
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
    serverList.setSelected(null);
  }
  applyView();
  setStatusMessage(message);
  applyContentState();
}

private void finishServerLoad() {
  contentState = ServerContentState.afterServerLoad(model.entries().size());
  setStatusMessage(
      model.entries().isEmpty()
          ? Component.translatable("grounds_connect.status.noServers")
          : null);
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
  for (AbstractWidget widget : serverActionWidgets) {
    widget.active = contentState.serverActionsActive();
  }
}
```

- [ ] **Step 3: Route every load result through the state helpers**

Replace the initial project-loading text update with:

```java
beginLoading(Component.translatable("grounds_connect.status.loadingProjects"));
loader.loadProjects();
```

Replace the unauthenticated branch with:

```java
showUnavailable(Component.translatable("grounds_connect.control.login"));
```

Replace `reloadServers()` with:

```java
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
```

Replace `onProjectsError(...)` with:

```java
@Override
public void onProjectsError(Throwable error) {
  if (isCurrentScreen()) {
    showUnavailable(Component.translatable("grounds_connect.error.projects", msg(error)));
  }
}
```

After `model.replaceServers(servers)`, `applyView()`, and `pingAll(model.entries())` in `onServersLoaded(...)`, call:

```java
finishServerLoad();
```

Remove the old inline `setStatusMessage(...)` expression from that callback.

Replace the current-project error body in `onServersError(...)` with:

```java
showUnavailable(Component.translatable("grounds_connect.error.servers", msg(error)));
```

- [ ] **Step 4: Run focused tests and compilation**

Run both commands with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.ServerContentStateTest
./gradlew compileJava
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run all required Gradle verification**

Run each command separately with escalated permissions:

```bash
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Expected: all three commands end with `BUILD SUCCESSFUL`. Inspect the Spotless result, then confirm `git diff --check` reports no whitespace errors.

- [ ] **Step 6: Review the final diff against the acceptance criteria**

Run:

```bash
git diff --check
git diff -- src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java src/main/java/gg/grounds/connect/ui/servers/ServerContentState.java src/test/java/gg/grounds/connect/ui/servers/ServerContentStateTest.java
git status --short
```

Confirm that old rows are cleared before `loader.loadServers(projectId)`, the empty list surface remains visible behind the loader, only stale-safe callbacks finish loading, all five server actions follow `serverActionsActive()`, and `logs/` remains untracked.

- [ ] **Step 7: Commit the loading UI**

```bash
git add src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java
git commit -m "feat(connect): add server list loading indicator"
```

---

### Task 3: Perform client-level visual verification

**Files:**
- Verify only: built Grounds Connect client artifact and Minecraft 26.2 screen behavior

**Interfaces:**
- Consumes: the built mod and a logged-in Grounds account with at least two projects
- Produces: visual evidence for the loader, stale-list removal, and disabled action states

- [ ] **Step 1: Start the development client**

Run with escalated permissions:

```bash
./gradlew runClient
```

Expected: Minecraft 26.2 opens with Grounds Connect loaded.

- [ ] **Step 2: Verify all loading transitions**

Check these exact scenarios:

1. Open Grounds Connect and confirm the project-loading animation is centered in the list region without changing its background.
2. Wait for servers, switch to another project, and confirm the old rows disappear before the request completes.
3. While loading, confirm `Join`, `Logs`, `Retry`, `Rollback`, and `NATS` are disabled while project selection, search, refresh, logout, and back remain available.
4. Press refresh and confirm the same transition occurs.
5. Load an empty project or induce a request failure and confirm the loader stops, the localized message appears, and server actions remain disabled.

- [ ] **Step 3: Record any environment-limited scenarios accurately**

If the development client lacks stored Grounds credentials or suitable projects, report exactly which scenarios could not be exercised. Do not claim visual completion for scenarios that were not observed.
