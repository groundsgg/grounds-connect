package gg.grounds.connect.ui;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.api.Nats;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live NATS message tail ({@code GET /v1/cluster/nats/tail}, SSE). A subject filter (default
 * {@code >}, up to 16 subjects, comma/space separated) plus Pause and Clear. Requires an active
 * dev workspace for the project.
 */
public final class NatsTailScreen extends Screen {

    private static final int MAX_LINES = 1000;

    private final Screen parent;
    private final String projectId;
    private final String projectLabel;

    private EditBox subjectBox;
    private LogConsoleScreen.LogList list;
    private Button pauseBtn;
    private AtomicBoolean streamCancelled;
    private boolean paused;
    private String subjects = ">";

    public NatsTailScreen(Screen parent, String projectId, String projectLabel) {
        super(Component.translatable("grounds_connect.nats.tailTitle", projectLabel));
        this.parent = parent;
        this.projectId = projectId;
        this.projectLabel = projectLabel;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(this.width / 2 - 150, 6, 300, 12, this.title, this.font));

        subjectBox = new EditBox(this.font, this.width / 2 - 150, 22, 222, 20,
                Component.translatable("grounds_connect.nats.subject"));
        subjectBox.setHint(Component.translatable("grounds_connect.nats.subjectHint"));
        subjectBox.setMaxLength(200);
        subjectBox.setValue(subjects);
        addRenderableWidget(subjectBox);
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.nats.apply"), b -> restart())
                .bounds(this.width / 2 + 76, 22, 74, 20).build());

        int listTop = 48;
        int listHeight = Math.max(20, this.height - 34 - listTop);
        list = new LogConsoleScreen.LogList(this.minecraft, this.width, listHeight, listTop, this.font.lineHeight + 2);
        addRenderableWidget(list);

        pauseBtn = Button.builder(Component.translatable("grounds_connect.nats.pause"), b -> togglePause())
                .bounds(this.width / 2 - 153, this.height - 28, 100, 20).build();
        addRenderableWidget(pauseBtn);
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.nats.clear"), b -> {
            if (list != null) {
                list.clear();
            }
        }).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 + 53, this.height - 28, 100, 20).build());

        restart();
    }

    private List<String> parseSubjects() {
        subjects = subjectBox != null ? subjectBox.getValue() : subjects;
        List<String> out = new ArrayList<>();
        for (String s : subjects.split("[,\\s]+")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            out.add(">");
        }
        return out.size() > 16 ? out.subList(0, 16) : out;
    }

    private void restart() {
        if (streamCancelled != null) {
            streamCancelled.set(true);
        }
        streamCancelled = new AtomicBoolean(false);
        final AtomicBoolean flag = streamCancelled;
        if (list != null) {
            list.clear();
        }
        List<String> subs = parseSubjects();
        addLine(Component.translatable("grounds_connect.nats.tailing", String.join(",", subs)).getString());
        GroundsSession.get().streamNatsTail(projectId, subs, new GroundsSession.NatsTailSink() {
            @Override
            public void onMessage(Nats.TailMessage m) {
                if (isCurrent() && !flag.get() && !paused) {
                    addLine(m.subject() + "  " + oneLine(m.data()));
                }
            }

            @Override
            public void onInfo(String line) {
                if (isCurrent() && !flag.get()) {
                    addLine(line);
                }
            }
        }, flag::get);
    }

    private void togglePause() {
        paused = !paused;
        pauseBtn.setMessage(Component.translatable(paused
                ? "grounds_connect.nats.resume" : "grounds_connect.nats.pause"));
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

    private static String oneLine(String data) {
        if (data == null) {
            return "";
        }
        String s = data.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 240 ? s.substring(0, 240) + "…" : s;
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
}
