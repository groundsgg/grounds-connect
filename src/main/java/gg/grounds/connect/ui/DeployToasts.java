package gg.grounds.connect.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Shows build/deploy progress as a toast per push (updated in place as the status changes). */
public final class DeployToasts {

  private static final Map<String, SystemToast.SystemToastId> IDS = new ConcurrentHashMap<>();

  private DeployToasts() {}

  /** Called on the client thread with a push status update. */
  public static void onStatus(String pushId, String appName, String status) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) {
      return;
    }
    SystemToast.SystemToastId id =
        IDS.computeIfAbsent(pushId, k -> new SystemToast.SystemToastId());
    SystemToast.addOrUpdate(
        mc.getToastManager(), id, Component.literal("Grounds · " + appName), message(status));
    if (isTerminal(status)) {
      IDS.remove(pushId); // build finished — stop tracking its toast id
    }
  }

  private static boolean isTerminal(String status) {
    return "ready".equals(status)
        || "build_failed".equals(status)
        || "deploy_failed".equals(status)
        || "rejected".equals(status);
  }

  private static Component message(String status) {
    return switch (status == null ? "" : status) {
      case "received", "building" -> Component.translatable("grounds_connect.toast.building");
      case "build_succeeded", "deploying" ->
          Component.translatable("grounds_connect.toast.deploying");
      case "ready" -> Component.translatable("grounds_connect.toast.deployed");
      case "build_failed" -> Component.translatable("grounds_connect.toast.buildFailed");
      case "deploy_failed" -> Component.translatable("grounds_connect.toast.deployFailed");
      case "rejected" -> Component.translatable("grounds_connect.toast.rejected");
      default -> Component.literal(status);
    };
  }
}
