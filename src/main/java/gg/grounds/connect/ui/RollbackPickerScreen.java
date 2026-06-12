package gg.grounds.connect.ui;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.api.RollbackTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pick a prior ready build to roll a deployment back to. */
public final class RollbackPickerScreen extends Screen {

    private final Screen parent;
    private final String deploymentName;
    private final String projectId;
    private boolean requested;
    private TargetList list;
    private StringWidget status;

    public RollbackPickerScreen(Screen parent, String deploymentName, String projectId) {
        super(Component.literal("Rollback · " + deploymentName));
        this.parent = parent;
        this.deploymentName = deploymentName;
        this.projectId = projectId;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(this.width / 2 - 150, 8, 300, 12, this.title, this.font));

        int listTop = 26;
        int listHeight = Math.max(20, this.height - 36 - listTop);
        list = new TargetList(this.minecraft, this.width, listHeight, listTop, 28);
        addRenderableWidget(list);

        status = new StringWidget(this.width / 2 - 150, listTop + 16, 300, 12, Component.empty(), this.font);
        addRenderableWidget(status);

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("Roll back to selected"), b -> doRollback())
                .bounds(this.width / 2 - 153, by, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 + 3, by, 100, 20).build());

        if (!requested) {
            requested = true;
            setStatus(Component.literal("Loading builds…"));
            GroundsSession.get().fetchRollbackTargets(deploymentName, projectId, new GroundsSession.Callback<>() {
                @Override
                public void onResult(List<RollbackTarget> value) {
                    if (!isCurrent()) {
                        return;
                    }
                    list.setTargets(value);
                    setStatus(value.isEmpty()
                            ? Component.literal("No ready builds to roll back to.") : Component.empty());
                }

                @Override
                public void onError(Throwable error) {
                    if (isCurrent()) {
                        setStatus(Component.literal("Error: " + error.getMessage()));
                    }
                }
            });
        }
    }

    private void doRollback() {
        TargetEntry entry = list.getSelected();
        if (entry == null) {
            setStatus(Component.literal("Pick a build first."));
            return;
        }
        setStatus(Component.literal("Rolling back…"));
        GroundsSession.get().rollback(deploymentName, projectId, entry.target.id(),
                () -> {
                    if (isCurrent()) {
                        this.minecraft.setScreen(parent);
                    }
                },
                error -> {
                    if (isCurrent()) {
                        setStatus(Component.literal("Rollback failed: " + error));
                    }
                });
    }

    private void setStatus(Component message) {
        if (status != null) {
            status.setMessage(message);
        }
    }

    private boolean isCurrent() {
        return minecraft != null && minecraft.screen == this;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    static final class TargetList extends ObjectSelectionList<TargetEntry> {

        TargetList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return Math.min(308, this.getWidth() - 12);
        }

        void setTargets(List<RollbackTarget> targets) {
            clearEntries();
            for (int i = 0; i < targets.size(); i++) {
                addEntry(new TargetEntry(targets.get(i), i == 0));
            }
        }
    }

    static final class TargetEntry extends ObjectSelectionList.Entry<TargetEntry> {

        final RollbackTarget target;
        final boolean current;

        TargetEntry(RollbackTarget target, boolean current) {
            this.target = target;
            this.current = current;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label());
        }

        private String label() {
            String img = target.imageTag() != null ? target.imageTag() : "?";
            if (img.length() > 28) {
                img = "…" + img.substring(img.length() - 28);
            }
            return img + (current ? "  (current)" : "");
        }

        @Override
        public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = getContentX();
            int y = getContentY();
            extractor.text(font, label(), x + 4, y + 3, current ? 0xFF888888 : 0xFFFFFFFF);
            extractor.text(font, target.createdAt() != null ? target.createdAt() : "", x + 4, y + 5 + font.lineHeight, 0xFFA0A0A0);
        }
    }
}
