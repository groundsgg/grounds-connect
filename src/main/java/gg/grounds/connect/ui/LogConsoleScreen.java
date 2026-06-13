package gg.grounds.connect.ui;

import gg.grounds.connect.Grounds;
import gg.grounds.connect.api.Push;
import gg.grounds.connect.core.AsyncCallback;
import gg.grounds.connect.util.Mclogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-tailing log console with a Runtime/Build toggle. Runtime tails
 * {@code /v1/deployments/:name/logs}; Build resolves the deployment's latest push and tails
 * {@code /v1/pushes/:id/logs}. Copy to clipboard, or Share to mclo.gs.
 */
public final class LogConsoleScreen extends Screen {

    private static final int MAX_LINES = 1000;

    private enum Source { RUNTIME, BUILD }

    private final Screen parent;
    private final String deploymentName;
    private final String projectId;

    private Source source = Source.RUNTIME;
    private AtomicBoolean streamCancelled;
    private LogList list;
    private Button runtimeBtn;
    private Button buildBtn;
    private StringWidget shareStatus;

    public LogConsoleScreen(Screen parent, String deploymentName, String projectId) {
        super(Component.translatable("grounds_connect.logs.title", deploymentName));
        this.parent = parent;
        this.deploymentName = deploymentName;
        this.projectId = projectId;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(this.width / 2 - 150, 6, 300, 12, this.title, this.font));

        runtimeBtn = Button.builder(Component.translatable("grounds_connect.logs.runtime"), b -> setSource(Source.RUNTIME))
                .bounds(this.width / 2 - 102, 22, 100, 20).build();
        buildBtn = Button.builder(Component.translatable("grounds_connect.logs.build"), b -> setSource(Source.BUILD))
                .bounds(this.width / 2 + 2, 22, 100, 20).build();
        addRenderableWidget(runtimeBtn);
        addRenderableWidget(buildBtn);
        updateToggle();

        int listTop = 46;
        int listHeight = Math.max(20, this.height - 48 - listTop);
        list = new LogList(this.minecraft, this.width, listHeight, listTop, this.font.lineHeight + 2);
        addRenderableWidget(list);

        shareStatus = new StringWidget(this.width / 2 - 150, this.height - 42, 300, 12, Component.empty(), this.font);
        addRenderableWidget(shareStatus);

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.logs.copy"), b -> copyAll())
                .bounds(this.width / 2 - 153, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.logs.share"), b -> shareLogs())
                .bounds(this.width / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 + 53, by, 100, 20).build());

        startStream();
    }

    private void setSource(Source newSource) {
        if (newSource == source) {
            return;
        }
        source = newSource;
        updateToggle();
        startStream();
    }

    private void updateToggle() {
        runtimeBtn.active = source != Source.RUNTIME;
        buildBtn.active = source != Source.BUILD;
    }

    private void startStream() {
        if (streamCancelled != null) {
            streamCancelled.set(true); // stop the previous stream
        }
        streamCancelled = new AtomicBoolean(false);
        final AtomicBoolean flag = streamCancelled;
        if (list != null) {
            list.clear();
        }
        if (source == Source.RUNTIME) {
            String path = "/v1/deployments/" + enc(deploymentName) + "/logs?projectId=" + enc(projectId);
            Grounds.services().logs().stream(path, this::onLine, flag::get);
        } else {
            addLine(Component.translatable("grounds_connect.logs.resolvingBuild").getString());
            Grounds.services().deployments().fetchLatestPush(deploymentName, projectId, new AsyncCallback<>() {
                @Override
                public void onResult(Push push) {
                    if (source != Source.BUILD || !isCurrent() || flag.get()) {
                        return;
                    }
                    if (push == null) {
                        addLine(Component.translatable("grounds_connect.logs.noBuild").getString());
                        return;
                    }
                    Grounds.services().logs().stream("/v1/pushes/" + enc(push.id()) + "/logs", LogConsoleScreen.this::onLine, flag::get);
                }

                @Override
                public void onError(Throwable error) {
                    addLine(Component.translatable("grounds_connect.logs.error", error.getMessage()).getString());
                }
            });
        }
    }

    private void onLine(String line) {
        if (!isCurrent()) {
            return;
        }
        addLine(line);
    }

    private void addLine(String line) {
        if (list == null) {
            return;
        }
        list.addLine(line);
        while (list.children().size() > MAX_LINES) {
            list.children().remove(0);
        }
        list.setScrollAmount(list.maxScrollAmount());
    }

    private void copyAll() {
        if (minecraft != null && list != null) {
            minecraft.keyboardHandler.setClipboard(list.joined());
        }
    }

    private void shareLogs() {
        if (list == null) {
            return;
        }
        String content = list.joined();
        if (content.isBlank()) {
            return;
        }
        setShareStatus(Component.translatable("grounds_connect.logs.uploading"));
        Mclogs.uploadAsync(content, new Mclogs.Callback() {
            @Override
            public void onUrl(String url) {
                if (minecraft != null) {
                    minecraft.keyboardHandler.setClipboard(url);
                }
                setShareStatus(Component.translatable("grounds_connect.logs.uploaded", url));
            }

            @Override
            public void onError(String message) {
                setShareStatus(Component.translatable("grounds_connect.logs.uploadFailed", message));
            }
        });
    }

    private void setShareStatus(Component message) {
        if (shareStatus != null) {
            shareStatus.setMessage(message);
        }
    }

    private boolean isCurrent() {
        return minecraft != null && minecraft.screen == this;
    }

    @Override
    public void removed() {
        if (streamCancelled != null) {
            streamCancelled.set(true);
        }
    }

    @Override
    public void onClose() {
        if (streamCancelled != null) {
            streamCancelled.set(true);
        }
        this.minecraft.setScreen(parent);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    static final class LogList extends ObjectSelectionList<LogList.Line> {

        LogList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 12;
        }

        void addLine(String text) {
            addEntry(new Line(text));
        }

        void clear() {
            clearEntries();
        }

        String joined() {
            StringBuilder sb = new StringBuilder();
            for (Line line : this.children()) {
                sb.append(line.text).append('\n');
            }
            return sb.toString();
        }

        static final class Line extends ObjectSelectionList.Entry<Line> {

            final String text;

            Line(String text) {
                this.text = text;
            }

            @Override
            public Component getNarration() {
                return Component.literal(text);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = Minecraft.getInstance().font;
                extractor.text(font, text, getContentX() + 2, getContentY() + 1, 0xFFE0E0E0);
            }
        }
    }
}
