package gg.grounds.serverlist.ui;

import gg.grounds.serverlist.GroundsSession;
import gg.grounds.serverlist.api.Nats;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
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
        super(Component.literal("NATS · " + projectLabel));
        this.parent = parent;
        this.projectId = projectId;
        this.projectLabel = projectLabel;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(this.width / 2 - 150, 6, 300, 12, this.title, this.font));
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> load())
                .bounds(this.width / 2 - 102, 22, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Live tail ▸"), b -> openTail())
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
        addLine("Loading…");
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
                        ? "No active dev workspace for this project — push to create one."
                        : "Could not load NATS: " + m);
            }
        });
    }

    private void render(Nats.Snapshot s) {
        list.clear();

        Nats.Broker b = s.broker();
        if (b == null) {
            addLine("Broker: unreachable");
        } else {
            addLine("Broker: " + b.connections() + " conns · in " + b.inMsgs() + " msgs/" + human(b.inBytes())
                    + " · out " + b.outMsgs() + " msgs/" + human(b.outBytes())
                    + " · subs " + b.subscriptions()
                    + (b.slowConsumers() > 0 ? " · slow " + b.slowConsumers() : ""));
        }

        addLine("");
        Nats.JetStream js = s.jetstream();
        if (js == null || js.streams() == 0) {
            addLine("JetStream: no streams yet (core pub/sub)");
        } else {
            addLine("JetStream: " + js.streams() + " streams · " + js.messages() + " msgs · " + human(js.bytes()));
            for (Nats.Stream st : js.list()) {
                addLine("  " + st.name() + "  [" + String.join(", ", st.subjects()) + "]  msgs=" + st.messages()
                        + " consumers=" + st.consumers());
            }
        }

        addLine("");
        List<Nats.Event> events = s.events();
        addLine("Declared events (" + events.size() + ")");
        if (events.isEmpty()) {
            addLine("  (none declared)");
        }
        for (Nats.Event e : events) {
            addLine("  " + e.app() + " · " + e.subject() + " · " + e.dir());
        }

        addLine("");
        List<Nats.Conn> conns = s.connections();
        long appConns = conns.stream().filter(c -> !c.system()).count();
        addLine("Connections (" + conns.size() + ", " + appConns + " app)");
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
