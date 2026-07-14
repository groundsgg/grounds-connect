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
