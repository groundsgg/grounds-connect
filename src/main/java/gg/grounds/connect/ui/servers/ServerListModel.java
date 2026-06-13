package gg.grounds.connect.ui.servers;

import gg.grounds.connect.api.GroundsServer;
import gg.grounds.connect.config.GroundsConfig;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ServerListModel {
    private List<ServerEntry> entries = new ArrayList<>();
    private List<ServerEntry> topLevel = new ArrayList<>();
    private String searchText = "";

    public List<ServerEntry> entries() {
        return entries;
    }

    public List<ServerEntry> topLevel() {
        return topLevel;
    }

    public String searchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText == null ? "" : searchText;
    }

    public void clear() {
        entries = new ArrayList<>();
        topLevel = new ArrayList<>();
    }

    public void replaceServers(List<GroundsServer> servers) {
        List<ServerEntry> built = new ArrayList<>();
        List<ServerEntry> proxies = new ArrayList<>();
        List<ServerEntry> backends = new ArrayList<>();
        List<ServerEntry> standalone = new ArrayList<>();
        for (GroundsServer server : servers) {
            ServerData data = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
            ServerEntry entry = new ServerEntry(server.name(), server.address(), data, server.state(), server.type());
            built.add(entry);
            if (server.isVelocityProxy()) {
                proxies.add(entry);
            } else if (server.isBackend()) {
                backends.add(entry);
            } else {
                standalone.add(entry);
            }
        }
        entries = built;
        topLevel = buildTree(proxies, backends, standalone);
    }

    /** Flattens the tree into the visible list: roots (pins first), with each expanded proxy's backends after it. */
    public List<ServerEntry> visibleEntries(GroundsConfig cfg) {
        String q = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);

        List<ServerEntry> roots = new ArrayList<>(topLevel);
        for (ServerEntry r : roots) {
            r.favorite = cfg.isFavorite(r.address);
            if (r.isProxy()) {
                r.expanded = cfg.isExpanded(r.name);
            }
        }
        roots.sort(Comparator.comparingInt((ServerEntry e) -> e.favorite ? 0 : 1)
                .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));

        List<ServerEntry> view = new ArrayList<>();
        for (ServerEntry r : roots) {
            boolean rootMatches = matches(r, q);
            List<ServerEntry> kids = r.children;
            boolean anyKidMatches = false;
            if (kids != null) {
                for (ServerEntry k : kids) {
                    if (matches(k, q)) {
                        anyKidMatches = true;
                        break;
                    }
                }
            }
            if (!q.isEmpty() && !rootMatches && !anyKidMatches) {
                continue;
            }
            view.add(r);
            if (kids != null && !kids.isEmpty() && (r.expanded || (!q.isEmpty() && anyKidMatches))) {
                List<ServerEntry> sortedKids = new ArrayList<>(kids);
                sortedKids.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
                for (ServerEntry k : sortedKids) {
                    if (q.isEmpty() || rootMatches || matches(k, q)) {
                        view.add(k);
                    }
                }
            }
        }
        return view;
    }

    /**
     * Builds the display tree: Velocity proxies and standalone servers are roots; backend game
     * servers nest under the project's proxy (single-proxy-per-project assumption -- see notes).
     * With no proxy in the project, backends stay top-level joinable (e.g. a standalone gamemode).
     */
    private List<ServerEntry> buildTree(List<ServerEntry> proxies, List<ServerEntry> backends,
                                        List<ServerEntry> standalone) {
        List<ServerEntry> roots = new ArrayList<>();
        roots.addAll(proxies);
        roots.addAll(standalone);
        if (proxies.isEmpty()) {
            roots.addAll(backends); // no proxy -> backends are directly joinable
        } else {
            ServerEntry proxy = proxies.get(0); // can't attribute to a specific proxy from the API
            proxy.children = new ArrayList<>();
            for (ServerEntry b : backends) {
                b.depth = 1;
                proxy.children.add(b);
            }
        }
        return roots;
    }

    private static boolean matches(ServerEntry e, String q) {
        return q.isEmpty()
                || e.name.toLowerCase(Locale.ROOT).contains(q)
                || e.address.toLowerCase(Locale.ROOT).contains(q);
    }
}
