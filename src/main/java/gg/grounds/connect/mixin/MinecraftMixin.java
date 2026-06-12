package gg.grounds.connect.mixin;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.ui.GroundsServersScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When leaving a Grounds server, return to the Grounds screen instead of the vanilla multiplayer
 * list. {@code disconnectFromWorld} (the pause-menu "Disconnect") hardcodes
 * {@code new JoinMultiplayerScreen(...)} for regular servers, so we override the screen at the tail
 * if the server we just left was one we joined from the Grounds screen.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private boolean grounds$leftGroundsServer;

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void grounds$captureLeave(Component reason, CallbackInfo ci) {
        ServerData current = ((Minecraft) (Object) this).getCurrentServer();
        grounds$leftGroundsServer = current != null && GroundsSession.get().isGroundsAddress(current.ip);
    }

    @Inject(method = "disconnectFromWorld", at = @At("TAIL"))
    private void grounds$afterLeave(Component reason, CallbackInfo ci) {
        if (grounds$leftGroundsServer) {
            grounds$leftGroundsServer = false;
            ((Minecraft) (Object) this).setScreen(new GroundsServersScreen(new TitleScreen()));
        }
    }
}
