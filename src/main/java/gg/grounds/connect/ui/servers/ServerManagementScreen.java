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
      Screen parent, String serverName, String projectId, String projectName, String projectRole) {
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
                        Component.translatable("grounds_connect.action.logs"), button -> openLogs())
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
          Tooltip.create(Component.translatable("grounds_connect.status.rollbackRoleRequired")));
    }
    status = new StringWidget(width / 2 - 150, y + 78, 300, 12, Component.empty(), font);
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
