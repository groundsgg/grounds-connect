package gg.grounds.connect.ui.servers;

import gg.grounds.connect.api.DeploymentRuntime;
import gg.grounds.connect.api.Project;
import gg.grounds.connect.core.GroundsServices;
import gg.grounds.connect.ui.nats.NatsScreen;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

final class ServerScreenActions {
  private final GroundsServersScreen screen;
  private final GroundsServices services;
  private final Supplier<ServerEntry> selectedEntry;
  private final Supplier<String> currentProjectId;

  ServerScreenActions(
      GroundsServersScreen screen,
      GroundsServices services,
      Supplier<ServerEntry> selectedEntry,
      Supplier<String> currentProjectId) {
    this.screen = screen;
    this.services = services;
    this.selectedEntry = selectedEntry;
    this.currentProjectId = currentProjectId;
  }

  void joinSelected() {
    ServerEntry entry = selectedEntry.get();
    if (entry == null) {
      return;
    }
    if (entry.depth > 0 || entry.isMinestom()) {
      screen.setStatusMessage(Component.translatable("grounds_connect.status.joinViaProxy"));
      return;
    }
    DeploymentRuntime.Health health =
        DeploymentRuntime.healthFor(entry.deploymentState, entry.runtime);
    if (health == DeploymentRuntime.Health.PAUSED && currentProjectId.get() != null) {
      wakeThenConnect(entry);
    } else {
      connect(entry);
    }
  }

  void openNats() {
    String projectId = currentProjectId.get();
    Project selected = services.projects().selectedProject();
    if (selected == null || projectId == null) {
      screen.setStatusMessage(Component.translatable("grounds_connect.status.noProjects"));
      return;
    }
    screen.client().setScreenAndShow(new NatsScreen(screen, projectId, selected.displayName()));
  }

  void logout() {
    services.logout();
    screen.client().setScreenAndShow(new TitleScreen());
  }

  private void connect(ServerEntry entry) {
    services.servers().markGroundsJoin(entry.address);
    ConnectScreen.startConnecting(
        screen, screen.client(), ServerAddress.parseString(entry.address), entry.data, false, null);
  }

  private void wakeThenConnect(ServerEntry entry) {
    String projectId = currentProjectId.get();
    screen.setStatusMessage(Component.translatable("grounds_connect.status.waking", entry.name));
    services
        .servers()
        .resumeAndAwait(
            entry.name,
            projectId,
            progress -> {
              if (screen.isCurrentScreen()) {
                screen.setStatusMessage(
                    Component.translatable(
                        "grounds_connect.status.wakingProgress", entry.name, progress));
              }
            },
            () -> {
              if (screen.isCurrentScreen()) {
                connect(entry);
              }
            },
            error -> {
              if (screen.isCurrentScreen()) {
                screen.setStatusMessage(
                    Component.translatable("grounds_connect.error.servers", error));
              }
            });
  }
}
