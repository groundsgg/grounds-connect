# Vanilla Server Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render Minecraft player counts, animated ping state, signal strength, and hover tooltips in Grounds server rows exactly like Minecraft 26.2 while retaining the independent Grounds deployment-health badge.

**Architecture:** Add a focused `VanillaServerStatusRenderer` that converts `ServerData` into a deterministic display record and renders the top-right Vanilla status cluster. `ServerEntry` delegates that cluster, keeps Grounds health at the bottom right, and receives its visible row index from `GroundsServerList` for Vanilla's staggered animation. A small `VanillaServerPingState` owns the testable `ServerData.State` mutations while `GroundsServersScreen` retains the existing off-thread network boundary.

**Tech Stack:** Java 25, Minecraft 26.2 client API, Fabric Loom, JUnit Jupiter 6, Gradle, Spotless

## Global Constraints

- Use Minecraft 26.2's existing `minecraft:server_list/*` sprites; add no image assets or dependencies.
- Animate `PINGING` with Vanilla's `1, 2, 3, 4, 5, 4, 3, 2` sequence every 100 milliseconds and offset the sequence by `rowIndex * 2`.
- Use Vanilla's successful-ping thresholds: `<150`, `<300`, `<600`, `<1000`, and `>=1000` milliseconds map to `ping_5` through `ping_1`.
- Keep Minecraft endpoint status at the top right and Grounds deployment health, including replica counts and existing colors, at the bottom right.
- Preserve `ServerPingScheduler`; do not initiate `ServerStatusPinger` work on the client thread.
- Hovering the ping sprite must use Minecraft's localized connecting, unreachable, incompatible, or exact-latency text; hovering the status text must use `ServerData.playerList` when present.
- Follow the repository logging rules and add no logs for expected ping failures.
- Use Conventional Commits and keep `.superpowers/` Visual Companion state out of commits.

---

### Task 1: Deterministic Vanilla status mapping and renderer

**Files:**
- Create: `src/main/java/gg/grounds/connect/ui/servers/VanillaServerStatusRenderer.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/VanillaServerStatusRendererTest.java`

**Interfaces:**
- Consumes: `ServerData.state()`, `ServerData.ping`, `ServerData.status`, `ServerData.version`, and `ServerData.playerList` populated by Minecraft's `ServerStatusPinger`.
- Produces: package-private `VanillaServerStatusRenderer.display(ServerData data, long nowMillis, int rowIndex)` and `VanillaServerStatusRenderer.render(...)` returning the left edge of the rendered status cluster.

- [ ] **Step 1: Write failing mapping tests**

Create `VanillaServerStatusRendererTest.java` with deterministic coverage for every latency boundary, the full triangular loading animation, the row offset, failure states, incompatible version text, and tooltips:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

final class VanillaServerStatusRendererTest {
  @Test
  void mapsSuccessfulLatencyToVanillaSignalStrength() {
    long[] pings = {149, 150, 299, 300, 599, 600, 999, 1000};
    String[] sprites = {
      "server_list/ping_5",
      "server_list/ping_4",
      "server_list/ping_4",
      "server_list/ping_3",
      "server_list/ping_3",
      "server_list/ping_2",
      "server_list/ping_2",
      "server_list/ping_1"
    };

    for (int i = 0; i < pings.length; i++) {
      ServerData data = serverData(ServerData.State.SUCCESSFUL);
      data.ping = pings[i];

      VanillaServerStatusRenderer.Display display =
          VanillaServerStatusRenderer.display(data, 0, 0);

      assertEquals(sprites[i], display.icon().getPath());
      assertEquals(Component.translatable("multiplayer.status.ping", pings[i]), display.pingTooltip());
    }
  }

  @Test
  void followsVanillaTriangularPingingAnimation() {
    String[] sprites = {
      "server_list/pinging_1",
      "server_list/pinging_2",
      "server_list/pinging_3",
      "server_list/pinging_4",
      "server_list/pinging_5",
      "server_list/pinging_4",
      "server_list/pinging_3",
      "server_list/pinging_2"
    };

    for (int step = 0; step < sprites.length; step++) {
      VanillaServerStatusRenderer.Display display =
          VanillaServerStatusRenderer.display(
              serverData(ServerData.State.PINGING), step * 100L, 0);

      assertEquals(sprites[step], display.icon().getPath());
    }
  }

  @Test
  void offsetsPingingAnimationByVisibleRowIndex() {
    VanillaServerStatusRenderer.Display display =
        VanillaServerStatusRenderer.display(serverData(ServerData.State.PINGING), 0, 1);

    assertEquals("server_list/pinging_3", display.icon().getPath());
  }

  @Test
  void mapsUnreachableAndIncompatibleStates() {
    assertEquals(
        "server_list/unreachable",
        VanillaServerStatusRenderer.display(serverData(ServerData.State.UNREACHABLE), 0, 0)
            .icon()
            .getPath());
    assertEquals(
        Component.translatable("multiplayer.status.no_connection"),
        VanillaServerStatusRenderer.display(serverData(ServerData.State.UNREACHABLE), 0, 0)
            .pingTooltip());
    assertEquals(
        "server_list/incompatible",
        VanillaServerStatusRenderer.display(serverData(ServerData.State.INCOMPATIBLE), 0, 0)
            .icon()
            .getPath());
    assertEquals(
        Component.translatable("multiplayer.status.incompatible"),
        VanillaServerStatusRenderer.display(serverData(ServerData.State.INCOMPATIBLE), 0, 0)
            .pingTooltip());
  }

  @Test
  void presentsIncompatibleVersionAndPropagatesPlayerTooltip() {
    ServerData data = serverData(ServerData.State.INCOMPATIBLE);
    data.version = Component.literal("26.1");
    data.playerList = List.of(Component.literal("Alex"), Component.literal("and 2 more"));

    VanillaServerStatusRenderer.Display display =
        VanillaServerStatusRenderer.display(data, 0, 0);

    assertEquals("26.1", display.statusText().getString());
    assertEquals(
        TextColor.fromLegacyFormat(ChatFormatting.RED), display.statusText().getStyle().getColor());
    assertEquals(data.playerList, display.playerTooltip());
  }

  @Test
  void presentsPlayerCountAndExactPingTooltipForSuccessfulServer() {
    ServerData data = serverData(ServerData.State.SUCCESSFUL);
    data.status = Component.literal("3/20");
    data.ping = 42;
    data.playerList = List.of(Component.literal("Alex"));

    VanillaServerStatusRenderer.Display display =
        VanillaServerStatusRenderer.display(data, 0, 0);

    assertSame(data.status, display.statusText());
    assertEquals(data.playerList, display.playerTooltip());
    assertEquals(Component.translatable("multiplayer.status.ping", 42L), display.pingTooltip());
  }

  private static ServerData serverData(ServerData.State state) {
    ServerData data = new ServerData("Test", "localhost", ServerData.Type.OTHER);
    data.setState(state);
    return data;
  }
}
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest
```

Expected: `compileTestJava` fails because `VanillaServerStatusRenderer` does not exist.

- [ ] **Step 3: Implement the focused renderer**

Create `VanillaServerStatusRenderer.java`. Keep state selection pure through `display(...)`, and keep all coordinate and tooltip behavior in `render(...)`:

```java
package gg.grounds.connect.ui.servers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class VanillaServerStatusRenderer {
  private static final int ICON_WIDTH = 10;
  private static final int ICON_HEIGHT = 8;
  private static final int STATUS_GAP = 5;
  private static final int STATUS_COLOR = 0xFF808080;
  private static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

  private VanillaServerStatusRenderer() {}

  static Display display(ServerData data, long nowMillis, int rowIndex) {
    Component statusText = data.status == null ? CommonComponents.EMPTY : data.status;
    List<Component> playerTooltip = List.of();
    Identifier icon;
    Component pingTooltip;

    switch (data.state()) {
      case INITIAL -> {
        icon = sprite("ping_1");
        pingTooltip = Component.translatable("multiplayer.status.pinging");
      }
      case PINGING -> {
        icon = pingingSprite(nowMillis, rowIndex);
        pingTooltip = Component.translatable("multiplayer.status.pinging");
      }
      case UNREACHABLE -> {
        icon = sprite("unreachable");
        pingTooltip = Component.translatable("multiplayer.status.no_connection");
      }
      case INCOMPATIBLE -> {
        icon = sprite("incompatible");
        pingTooltip = Component.translatable("multiplayer.status.incompatible");
        statusText =
            (data.version == null ? CommonComponents.EMPTY : data.version)
                .copy()
                .withStyle(ChatFormatting.RED);
        playerTooltip = copyPlayerTooltip(data.playerList);
      }
      case SUCCESSFUL -> {
        icon = successfulSprite(data.ping);
        pingTooltip = Component.translatable("multiplayer.status.ping", data.ping);
        playerTooltip = copyPlayerTooltip(data.playerList);
      }
      default -> throw new IllegalStateException("Unhandled server state: " + data.state());
    }

    return new Display(icon, statusText, pingTooltip, playerTooltip);
  }

  static int render(
      GuiGraphicsExtractor extractor,
      Font font,
      ServerData data,
      int rightEdge,
      int rowY,
      int mouseX,
      int mouseY,
      int rowIndex,
      long nowMillis) {
    Display display = display(data, nowMillis, rowIndex);
    int iconX = rightEdge - ICON_WIDTH;
    extractor.blitSprite(PIPELINE, display.icon(), iconX, rowY, ICON_WIDTH, ICON_HEIGHT);

    int statusWidth = font.width(display.statusText());
    int statusX = iconX - STATUS_GAP - statusWidth;
    extractor.text(font, display.statusText(), statusX, rowY + 1, STATUS_COLOR);

    if (inside(mouseX, mouseY, iconX, rowY, ICON_WIDTH, ICON_HEIGHT)) {
      extractor.setTooltipForNextFrame(display.pingTooltip(), mouseX, mouseY);
    } else if (!display.playerTooltip().isEmpty()
        && inside(mouseX, mouseY, statusX, rowY, statusWidth, font.lineHeight + 8)) {
      extractor.setTooltipForNextFrame(
          display.playerTooltip().stream().map(Component::getVisualOrderText).toList(),
          mouseX,
          mouseY);
    }

    return statusWidth == 0 ? iconX : statusX;
  }

  private static Identifier successfulSprite(long ping) {
    if (ping < 150) {
      return sprite("ping_5");
    }
    if (ping < 300) {
      return sprite("ping_4");
    }
    if (ping < 600) {
      return sprite("ping_3");
    }
    if (ping < 1000) {
      return sprite("ping_2");
    }
    return sprite("ping_1");
  }

  private static Identifier pingingSprite(long nowMillis, int rowIndex) {
    int frame = (int) ((nowMillis / 100L + rowIndex * 2L) & 7L);
    if (frame > 4) {
      frame = 8 - frame;
    }
    return sprite("pinging_" + (frame + 1));
  }

  private static Identifier sprite(String name) {
    return Identifier.withDefaultNamespace("server_list/" + name);
  }

  private static List<Component> copyPlayerTooltip(List<Component> playerList) {
    return playerList == null || playerList.isEmpty() ? List.of() : List.copyOf(playerList);
  }

  private static boolean inside(
      int mouseX, int mouseY, int x, int y, int width, int height) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }

  record Display(
      Identifier icon,
      Component statusText,
      Component pingTooltip,
      List<Component> playerTooltip) {}
}
```

- [ ] **Step 4: Run the focused test and make only compatibility corrections required by the actual Minecraft 26.2 API**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest
```

Expected: `VanillaServerStatusRendererTest` passes. If a Minecraft value object has a different equality surface, assert its stable path, visible string, style, or component equality without removing a behavioral case.

- [ ] **Step 5: Format, re-run the focused test, and commit the renderer**

Run with escalated permissions:

```bash
./gradlew spotlessApply
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

```bash
git add src/main/java/gg/grounds/connect/ui/servers/VanillaServerStatusRenderer.java src/test/java/gg/grounds/connect/ui/servers/VanillaServerStatusRendererTest.java
git commit -m "feat(connect): render vanilla server status"
```

### Task 2: Integrate the Vanilla cluster and preserve Grounds health

**Files:**
- Modify: `src/main/java/gg/grounds/connect/ui/servers/ServerEntry.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServerList.java`
- Test: `src/test/java/gg/grounds/connect/ui/servers/VanillaServerStatusRendererTest.java`

**Interfaces:**
- Consumes: `VanillaServerStatusRenderer.render(...)` from Task 1 and each entry's visible list index.
- Produces: `ServerEntry.setStatusRowIndex(int rowIndex)` and collision-free top-right Minecraft status plus bottom-right Grounds health layout.

- [ ] **Step 1: Extend the row-offset renderer contract before integrating it**

Extend `VanillaServerStatusRendererTest` so the expected phase is stated for more than one visible row:

```java
@Test
void keepsEveryVisibleRowOnVanillasTwoStepPhaseOffset() {
  ServerData data = serverData(ServerData.State.PINGING);

  assertEquals(
      "server_list/pinging_1",
      VanillaServerStatusRenderer.display(data, 0, 0).icon().getPath());
  assertEquals(
      "server_list/pinging_3",
      VanillaServerStatusRenderer.display(data, 0, 1).icon().getPath());
  assertEquals(
      "server_list/pinging_5",
      VanillaServerStatusRenderer.display(data, 0, 2).icon().getPath());
}
```

- [ ] **Step 2: Run the focused test before integration**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest
```

Expected: PASS, establishing the renderer contract before callers change.

- [ ] **Step 3: Move the right-aligned content to the approved rows**

In `ServerEntry.java`:

1. Delete the `pinging` field and the `pingText()` method.
2. Add a package-private visible-row index and setter:

```java
private int statusRowIndex;

void setStatusRowIndex(int statusRowIndex) {
  this.statusRowIndex = statusRowIndex;
}
```

3. Replace the existing right-column block inside `extractContent(...)` with:

```java
int vanillaLeft =
    VanillaServerStatusRenderer.render(
        extractor,
        font,
        data,
        rightEdge,
        line1Y - 1,
        mouseX,
        mouseY,
        statusRowIndex,
        net.minecraft.Util.getMillis());
int nameMaxRight = vanillaLeft - RIGHT_GAP;

int addressMaxRight = rightEdge;
if (deploymentState != null) {
  String label = badgeLabel();
  int bx = rightEdge - font.width(label);
  extractor.text(font, label, bx, line2Y, badgeColor());
  addressMaxRight = bx - RIGHT_GAP;
}
```

This makes the Vanilla status cluster reserve name width on the first line and the Grounds health badge reserve address width on the second line. Keep all existing left-column, child styling, proxy count, `fit(...)`, `badgeLabel()`, and `badgeColor()` behavior unchanged.

- [ ] **Step 4: Assign Vanilla's visible-row animation offset**

Replace `GroundsServerList.setServers(...)` with an indexed loop:

```java
public void setServers(List<ServerEntry> entries) {
  this.clearEntries();
  for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++) {
    ServerEntry entry = entries.get(rowIndex);
    entry.setStatusRowIndex(rowIndex);
    this.addEntry(entry);
  }
}
```

The index is recalculated whenever filtering, favorites, proxy expansion, project changes, or refreshes rebuild the visible list, matching Vanilla's use of the current child index.

- [ ] **Step 5: Compile and run the renderer tests**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest
```

Expected: `compileJava`, `compileTestJava`, and all renderer tests pass; there are no references to `ServerEntry.pinging` or `pingText()` in these two files.

- [ ] **Step 6: Format and commit the row integration**

Run with escalated permissions:

```bash
./gradlew spotlessApply
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add src/main/java/gg/grounds/connect/ui/servers/ServerEntry.java src/main/java/gg/grounds/connect/ui/servers/GroundsServerList.java src/test/java/gg/grounds/connect/ui/servers/VanillaServerStatusRendererTest.java
git commit -m "feat(connect): align server rows with vanilla"
```

### Task 3: Drive Vanilla `ServerData.State` from the background ping flow

**Files:**
- Create: `src/main/java/gg/grounds/connect/ui/servers/VanillaServerPingState.java`
- Modify: `src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java`
- Create: `src/test/java/gg/grounds/connect/ui/servers/VanillaServerPingStateTest.java`
- Verify: `src/test/java/gg/grounds/connect/ui/servers/ServerPingSchedulerTest.java`

**Interfaces:**
- Consumes: `ServerData`, Minecraft's current protocol version, and the existing `ServerPingScheduler.submit(Runnable)` boundary.
- Produces: package-private `VanillaServerPingState.start(ServerData)`, `complete(ServerData, int currentProtocol)`, and `fail(ServerData, Component failureMotd)` plus Vanilla-compatible `PINGING`, `SUCCESSFUL`, `INCOMPATIBLE`, and `UNREACHABLE` transitions.

- [ ] **Step 1: Write failing state-lifecycle tests**

Create `VanillaServerPingStateTest.java`:

```java
package gg.grounds.connect.ui.servers;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class VanillaServerPingStateTest {
  @Test
  void startsPingWithVanillaEmptyStatusFields() {
    ServerData data = serverData();
    data.motd = Component.literal("Old MOTD");
    data.status = Component.literal("3/20");

    VanillaServerPingState.start(data);

    assertSame(ServerData.State.PINGING, data.state());
    assertSame(CommonComponents.EMPTY, data.motd);
    assertSame(CommonComponents.EMPTY, data.status);
  }

  @Test
  void completesPingFromProtocolCompatibility() {
    ServerData matching = serverData();
    matching.protocol = 123;
    ServerData older = serverData();
    older.protocol = 122;
    ServerData newer = serverData();
    newer.protocol = 124;

    VanillaServerPingState.complete(matching, 123);
    VanillaServerPingState.complete(older, 123);
    VanillaServerPingState.complete(newer, 123);

    assertSame(ServerData.State.SUCCESSFUL, matching.state());
    assertSame(ServerData.State.INCOMPATIBLE, older.state());
    assertSame(ServerData.State.INCOMPATIBLE, newer.state());
  }

  @Test
  void failsPingWithVanillaFailureMessage() {
    ServerData data = serverData();
    Component failure = Component.translatable("multiplayer.status.cannot_connect");

    VanillaServerPingState.fail(data, failure);

    assertSame(ServerData.State.UNREACHABLE, data.state());
    assertSame(failure, data.motd);
  }

  private static ServerData serverData() {
    return new ServerData("Test", "localhost", ServerData.Type.OTHER);
  }
}
```

- [ ] **Step 2: Run the focused lifecycle test and verify the red state**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerPingStateTest
```

Expected: `compileTestJava` fails because `VanillaServerPingState` does not exist.

- [ ] **Step 3: Implement the minimal lifecycle helper**

Create `VanillaServerPingState.java`:

```java
package gg.grounds.connect.ui.servers;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class VanillaServerPingState {
  private VanillaServerPingState() {}

  static void start(ServerData data) {
    data.setState(ServerData.State.PINGING);
    data.motd = CommonComponents.EMPTY;
    data.status = CommonComponents.EMPTY;
  }

  static void complete(ServerData data, int currentProtocol) {
    data.setState(
        data.protocol == currentProtocol
            ? ServerData.State.SUCCESSFUL
            : ServerData.State.INCOMPATIBLE);
  }

  static void fail(ServerData data, Component failureMotd) {
    data.setState(ServerData.State.UNREACHABLE);
    data.motd = failureMotd;
  }
}
```

- [ ] **Step 4: Run lifecycle and scheduler tests**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerPingStateTest --tests gg.grounds.connect.ui.servers.ServerPingSchedulerTest
```

Expected: both test classes pass, establishing the state mutations and asynchronous execution boundary before screen integration.

- [ ] **Step 5: Update `pingAll(...)` to mirror Vanilla's state lifecycle**

Add imports for `java.net.UnknownHostException` and `net.minecraft.SharedConstants`. Replace `pingAll(...)` with:

```java
private void pingAll(List<ServerEntry> list) {
  pinger.removeAll();
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
          } catch (UnknownHostException e) {
            VanillaServerPingState.fail(
                entry.data, Component.translatable("multiplayer.status.cannot_resolve"));
          } catch (Exception e) {
            VanillaServerPingState.fail(
                entry.data, Component.translatable("multiplayer.status.cannot_connect"));
          }
        });
  }
}
```

The first callback is intentionally a no-op because Grounds entries are API-derived and have no saved-server file or favicon persistence action. The second callback is the completed ping response, as in Vanilla. Do not add logging for either failure branch.

- [ ] **Step 6: Run focused renderer, lifecycle, scheduler, and content-state tests**

Run with escalated permissions:

```bash
./gradlew test --tests gg.grounds.connect.ui.servers.VanillaServerStatusRendererTest --tests gg.grounds.connect.ui.servers.VanillaServerPingStateTest --tests gg.grounds.connect.ui.servers.ServerPingSchedulerTest --tests gg.grounds.connect.ui.servers.ServerContentStateTest
```

Expected: all tests pass, proving the deterministic status mapping, off-thread scheduling, and loading/list-surface behavior remain intact.

- [ ] **Step 7: Search for obsolete textual ping state and commit the lifecycle integration**

Run:

```bash
rg -n "\.pinging|pingText\(|grounds_connect\.server\.pinging" src/main/java
```

Expected: no Java matches. The translation key may remain in language resources for compatibility; do not edit unrelated locale files.

Run with escalated permissions:

```bash
./gradlew spotlessApply
```

Expected: `BUILD SUCCESSFUL`.

```bash
git add src/main/java/gg/grounds/connect/ui/servers/VanillaServerPingState.java src/main/java/gg/grounds/connect/ui/servers/GroundsServersScreen.java src/test/java/gg/grounds/connect/ui/servers/VanillaServerPingStateTest.java
git commit -m "fix(connect): mirror vanilla ping lifecycle"
```

### Task 4: Full verification and Minecraft acceptance

**Files:**
- Verify: all committed source and test files from Tasks 1-3
- Do not stage: `.superpowers/brainstorm/**`, `logs/**`, `run/**`, or other local runtime state

**Interfaces:**
- Consumes: the completed renderer, row layout, and background ping lifecycle.
- Produces: a verified feature branch ready for publication as a draft pull request.

- [ ] **Step 1: Run the mandatory repository verification in order**

Run each command separately with escalated permissions:

```bash
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Expected: all three commands finish with `BUILD SUCCESSFUL`. If `spotlessApply` changes tracked Java, inspect the diff, rerun `./gradlew test` and `./gradlew build`, then commit only the formatting change with a Conventional Commit.

- [ ] **Step 2: Verify the diff and repository hygiene**

Run:

```bash
git diff --check
git status --short --untracked-files=all
git diff origin/main...HEAD -- src/main src/test docs .gitignore
```

Expected: no whitespace errors; only intentional branch commits differ; `.superpowers/brainstorm/**` remains untracked and excluded from staging.

- [ ] **Step 3: Launch the Minecraft 26.2 development client with the installed aggregate Fabric API**

Ensure `/tmp/grounds-connect-fabric-api-26.2.jar` points to the Fabric API jar from the user's Minecraft 26.2 Modrinth profile, then run with escalated permissions:

```bash
JAVA_TOOL_OPTIONS=-Dfabric.addMods=/tmp/grounds-connect-fabric-api-26.2.jar ./gradlew runClient
```

Expected: Minecraft reaches the main menu and Grounds Connect reports the persisted authenticated session. Narrator, Realms, or account-service warnings that do not affect the Grounds screen are environment noise, not feature failures.

- [ ] **Step 4: Perform the visual and interaction acceptance pass**

In the Grounds server list:

1. Confirm each newly loaded row animates `pinging_1` through `pinging_5` and back down with adjacent rows phase-shifted.
2. Confirm a successful ping becomes the appropriate Vanilla signal-strength icon.
3. Hover the icon and confirm the connecting or exact millisecond tooltip.
4. Hover the player count and confirm the sampled player names/additional-player line.
5. Confirm incompatible or unreachable endpoints use Vanilla's matching sprite and tooltip.
6. Confirm `● online ready/desired` remains independently visible at the bottom right with existing Grounds colors.
7. Confirm long names stop before the top-right cluster and long addresses stop before the bottom-right Grounds badge.
8. Switch projects and refresh; confirm stale rows disappear, the Minecraft loader appears, and the list background does not change.

- [ ] **Step 5: Stop the client and record final branch state**

Close Minecraft normally, wait for Gradle to finish, then run:

```bash
git status --short --branch --untracked-files=all
git log --oneline --decorate origin/main..HEAD
```

Expected: the feature branch contains the intentional Conventional Commits and no tracked runtime artifacts. Proceed to the repository's branch-finishing and draft-PR workflow.
