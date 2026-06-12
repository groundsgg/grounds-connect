package gg.grounds.connect.mixin;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.ui.DeviceCodeScreen;
import gg.grounds.connect.ui.GroundsServersScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Grounds Connect" button to the main menu, directly under the Multiplayer button.
 * Re-runs on every (re)layout because {@code TitleScreen} inherits {@code Screen.repositionElements}
 * which rebuilds via {@code init()} — so finding the freshly-placed Multiplayer button and nudging
 * the buttons below it down stays idempotent across resizes.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void grounds$addButton(CallbackInfo ci) {
        Button button = Button.builder(Component.literal("Grounds Connect"), b -> grounds$onClick())
                .bounds(0, 0, 200, 20)
                .build();

        AbstractWidget multiplayer = grounds$find(Component.translatable("menu.multiplayer"));
        if (multiplayer != null) {
            int stride = multiplayer.getHeight() + 4;
            int targetY = multiplayer.getY() + stride;
            // Push the buttons below Multiplayer down to make room.
            for (GuiEventListener child : this.children()) {
                if (child instanceof AbstractWidget w && w != multiplayer && w.getY() >= targetY) {
                    w.setY(w.getY() + stride);
                }
            }
            button.setX(multiplayer.getX());
            button.setWidth(multiplayer.getWidth());
            button.setY(targetY);
        } else {
            button.setX(this.width / 2 - 100);
            button.setY(this.height / 4 + 96);
        }
        this.addRenderableWidget(button);
    }

    @Unique
    private AbstractWidget grounds$find(Component label) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w && label.equals(w.getMessage())) {
                return w;
            }
        }
        return null;
    }

    @Unique
    private void grounds$onClick() {
        GroundsSession session = GroundsSession.get();
        Screen self = this;
        if (session.isLoggedIn()) {
            this.minecraft.setScreen(new GroundsServersScreen(self));
        } else {
            this.minecraft.setScreen(new DeviceCodeScreen(self,
                    () -> this.minecraft.setScreen(new GroundsServersScreen(self))));
        }
    }
}
