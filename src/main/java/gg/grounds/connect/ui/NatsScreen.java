package gg.grounds.connect.ui;

import gg.grounds.connect.GroundsSession;
import gg.grounds.connect.api.Nats;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Project NATS snapshot ({@code GET /v1/cluster/nats}): broker stats, declared event subjects, live
 * connections and JetStream streams. Requires an active dev workspace for the project. A "Live tail"
 * button opens {@link NatsTailScreen} for the live message stream.
 */
public final class NatsScreen extends Screen {

    private final Screen parent;
    private final String projectId;
    private final String projectLabel;
    private LogConsoleScreen.LogList list;

    public NatsScreen(Screen parent, String projectId, String projectLabel) {
        super(Component.translatable("grounds_connect.nats.title", projectLabel));
        this.parent = parent;
        this.projectId = projectId;
        this.projectLabel = projectLabel;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(this.width / 2 - 150, 6, 300, 12, this.title, this.font));
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.control.refresh"), b -> load())
                .bounds(this.width / 2 - 102, 22, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("grounds_connect.nats.liveTail"), b -> openTail())
                .bounds(this.width / 2 + 2, 22, 100, 20).build());

        int listTop = 46;
        int listHeight = Math.max(20, this.height - 34 - listTop);
        list = new LogConsoleScreen.LogList(this.minecraft, this.width, listHeight, listTop, this.font.lineHeight + 2);
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        load();
    }

    private void load() {
        if (list != null) {
            list.clear();
        }
        addLine(Component.translatable("grounds_connect.nats.loading").getString());
        GroundsSession.get().fetchClusterNats(projectId, new GroundsSession.Callback<>() {
            @Override
            public void onResult(Nats.Snapshot snap) {
                if (isCurrent()) {
                    render(snap);
                }
            }

            @Override
            public void onError(Throwable error) {
                if (!isCurrent()) {
                    return;
                }
                list.clear();
                String m = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                addLine(m.contains("404")
                        ? Component.translatable("grounds_connect.nats.noWorkspace").getString()
                        : Component.translatable("grounds_connect.nats.error", m).getString());
            }
        });
    }

    private void render(Nats.Snapshot s) {
        list.clear();

        Nats.Broker b = s.broker();
        if (b == null) {
            addLine(I18n.get("grounds_connect.nats.broker.unreachable"));
        } else {
            addLine(I18n.get("grounds_connect.nats.broker.stats",
                    b.connections(), b.inMsgs(), human(b.inBytes()), b.outMsgs(), human(b.outBytes()),
                    b.subscriptions(), b.slowConsumers() > 0
                            ? I18n.get("grounds_connect.nats.broker.slow", b.slowConsumers()) : ""));
        }

        addLine("");
        Nats.JetStream js = s.jetstream();
        if (js == null || js.streams() == 0) {
            addLine(I18n.get("grounds_connect.nats.jetstream.empty"));
        } else {
            addLine(I18n.get("grounds_connect.nats.jetstream.stats",
                    js.streams(), js.messages(), human(js.bytes())));
            for (Nats.Stream st : js.list()) {
                addLine(I18n.get("grounds_connect.nats.jetstream.stream",
                        st.name(), String.join(", ", st.subjects()), st.messages(), st.consumers()));
            }
        }

        addLine("");
        List<Nats.Event> events = s.events();
        addLine(I18n.get("grounds_connect.nats.events.header", events.size()));
        if (events.isEmpty()) {
            addLine(I18n.get("grounds_connect.nats.events.empty"));
        }
        for (Nats.Event e : events) {
            addLine("  " + e.app() + " · " + e.subject() + " · " + e.dir());
        }

        addLine("");
        List<Nats.Conn> conns = s.connections();
        long appConns = conns.stream().filter(c -> !c.system()).count();
        addLine(I18n.get("grounds_connect.nats.connections.header", conns.size(), appConns));
        for (Nats.Conn c : conns) {
            String name = (c.name() == null || c.name().isBlank()) ? "cid#" + c.cid() : c.name();
            addLine("  " + (c.system() ? "[sys] " : "") + name + " · " + String.join(" ", c.subjects()));
        }

        list.setScrollAmount(0);
    }

    private void openTail() {
        this.minecraft.setScreen(new NatsTailScreen(this, projectId, projectLabel));
    }

    private void addLine(String line) {
        if (list != null) {
            list.addLine(line);
        }
    }

    private boolean isCurrent() {
        return minecraft != null && minecraft.screen == this;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    /** Compact byte count: 1234 -> "1.2K". */
    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        double k = bytes / 1024.0;
        if (k < 1024) {
            return String.format("%.1fK", k);
        }
        double m = k / 1024.0;
        return m < 1024 ? String.format("%.1fM", m) : String.format("%.1fG", m / 1024.0);
    }
}
