package gg.grounds.serverlist;

import com.mojang.blaze3d.platform.InputConstants;
import gg.grounds.serverlist.config.GroundsConfig;
import gg.grounds.serverlist.ui.DeployToasts;
import gg.grounds.serverlist.ui.DeviceCodeScreen;
import gg.grounds.serverlist.ui.GroundsServersScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client entrypoint: load config + stored credentials, start the deploy watcher, bind the open key. */
public final class GroundsClient implements ClientModInitializer {

    // Unbound by default — the user assigns it under Controls → Multiplayer.
    private static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.grounds.open",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MULTIPLAYER);

    @Override
    public void onInitializeClient() {
        GroundsConfig.get().load();
        GroundsSession.get().loadFromStore();
        GroundsSession.get().startDeployWatcher(DeployToasts::onStatus);

        KeyMappingHelper.registerKeyMapping(OPEN_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.consumeClick()) {
                openGroundsScreen(client);
            }
        });

        Constants.LOG.info("[{}] ready (api={}, loggedIn={})",
                Constants.MOD_NAME, GroundsConfig.get().apiBaseUrl(), GroundsSession.get().isLoggedIn());
    }

    /** Opens the Grounds screen (logging in first if needed); closing it returns where we came from. */
    private static void openGroundsScreen(Minecraft client) {
        Screen back = client.screen; // null when in-game, so Back returns to gameplay
        if (GroundsSession.get().isLoggedIn()) {
            client.setScreen(new GroundsServersScreen(back));
        } else {
            client.setScreen(new DeviceCodeScreen(back,
                    () -> client.setScreen(new GroundsServersScreen(back))));
        }
    }
}
