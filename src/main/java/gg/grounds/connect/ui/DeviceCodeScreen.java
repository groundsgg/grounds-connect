package gg.grounds.connect.ui;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.auth.KeycloakClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

/**
 * Device-authorization login screen: shows the verification URL + user code, lets the player
 * open/copy them, and polls in the background until authorized (or cancelled).
 */
public final class DeviceCodeScreen extends Screen {

    private final Screen parent;
    private final Runnable onComplete;
    private GroundsSession.LoginHandle handle;
    private KeycloakClient.DeviceCode code;
    private Component status = Component.translatable("grounds_connect.device.requesting");
    private boolean started;

    /**
     * @param parent     screen to return to on cancel
     * @param onComplete run on successful login (e.g. open a fresh screen reflecting the new state)
     */
    public DeviceCodeScreen(Screen parent, Runnable onComplete) {
        super(Component.translatable("grounds_connect.device.title"));
        this.parent = parent;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = this.width / 2;
        int y = this.height / 4;

        addRenderableWidget(new StringWidget(cx - 150, y, 300, 12, this.title, this.font));
        addRenderableWidget(new StringWidget(cx - 150, y + 22, 300, 12,
                Component.translatable("grounds_connect.device.instructions"), this.font));

        if (code != null) {
            addRenderableWidget(new StringWidget(cx - 150, y + 44, 300, 12,
                    Component.literal(code.verificationUri()), this.font));
            addRenderableWidget(new StringWidget(cx - 150, y + 62, 300, 12,
                    Component.translatable("grounds_connect.device.code", code.userCode()), this.font));
        }

        addRenderableWidget(new StringWidget(cx - 150, y + 90, 300, 12, status, this.font));

        int by = y + 116;
        if (code != null) {
            addRenderableWidget(Button.builder(Component.translatable("grounds_connect.device.open"),
                            b -> openVerificationPage())
                    .bounds(cx - 154, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("grounds_connect.device.copyCode"),
                            b -> copy(code.userCode()))
                    .bounds(cx - 50, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("grounds_connect.device.copyUrl"),
                            b -> copy(code.verificationUriComplete()))
                    .bounds(cx + 54, by, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.device.cancel"), b -> onClose())
                .bounds(cx - 50, by + 24, 100, 20).build());

        if (!started) {
            started = true;
            handle = GroundsSession.get().startLogin(new GroundsSession.LoginListener() {
                @Override
                public void onCode(KeycloakClient.DeviceCode c) {
                    code = c;
                    status = Component.translatable("grounds_connect.device.waiting");
                    rebuildWidgets();
                }

                @Override
                public void onSuccess() {
                    onComplete.run();
                }

                @Override
                public void onError(String message) {
                    status = Component.translatable("grounds_connect.error.login", message);
                    rebuildWidgets();
                }
            });
        }
    }

    private void openVerificationPage() {
        if (code != null) {
            ConfirmLinkScreen.confirmLinkNow(this, URI.create(code.verificationUriComplete()), true);
        }
    }

    private void copy(String text) {
        if (text != null && minecraft != null) {
            minecraft.keyboardHandler.setClipboard(text);
            status = Component.translatable("grounds_connect.device.copied");
            rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        if (handle != null) {
            handle.cancel();
        }
        minecraft.setScreen(parent);
    }
}
