package gg.grounds.serverlist.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.grounds.serverlist.Constants;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST client for the forge platform API ({@code GET /v1/projects}, {@code GET /v1/deployments}). */
public final class ForgeApiClient {

    private static final int MAX_DEPLOYMENT_PAGES = 10;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String baseUrl;

    public ForgeApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Unauthenticated readiness probe ({@code GET /readyz}); true only on a 2xx (not 503 degraded). */
    public boolean isReady() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/readyz"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    /** {@code GET /v1/projects} -> the user's projects. */
    public List<Project> listProjects(String accessToken) throws Exception {
        JsonObject body = getJson("/v1/projects", accessToken);
        List<Project> out = new ArrayList<>();
        for (JsonElement el : itemsOf(body)) {
            JsonObject o = el.getAsJsonObject();
            out.add(new Project(
                    str(o, "id"),
                    str(o, "slug"),
                    str(o, "name"),
                    str(o, "role")));
        }
        StringBuilder names = new StringBuilder();
        for (Project p : out) {
            names.append(' ').append(p.slug()).append('(').append(p.id()).append(')');
        }
        Constants.LOG.debug("[{}] /v1/projects -> {} project(s):{}", Constants.MOD_NAME, out.size(), names);
        return out;
    }

    /**
     * Lists a project's Minecraft servers — deployments with a {@code minecraft://} publicUrl
     * (Paper / Velocity / gamemode) plus {@code minestom} deployments, which forge renders as a
     * {@code service} with an {@code https://} URL so they can only be identified via {@code /v1/apps}
     * (their manifest {@code publicType}). Skips soft-deleted rows; follows pagination.
     */
    public List<GroundsServer> listMinecraftServers(String accessToken, String projectId) throws Exception {
        Map<String, String> publicTypes = fetchPublicTypes(accessToken, projectId);
        List<GroundsServer> out = new ArrayList<>();
        String cursor = null;
        int total = 0;
        StringBuilder raw = new StringBuilder();
        for (int page = 0; page < MAX_DEPLOYMENT_PAGES; page++) {
            StringBuilder path = new StringBuilder("/v1/deployments?limit=100&projectId=")
                    .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8));
            if (cursor != null) {
                path.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            }
            JsonObject body = getJson(path.toString(), accessToken);

            for (JsonElement el : itemsOf(body)) {
                JsonObject o = el.getAsJsonObject();
                String name = str(o, "name");
                String type = str(o, "type");
                String state = str(o, "state");
                String publicUrl = str(o, "publicUrl");
                boolean deleted = o.has("deletedAt") && !o.get("deletedAt").isJsonNull();
                total++;
                raw.append("\n  - ").append(name)
                        .append(" [type=").append(type)
                        .append(" state=").append(state)
                        .append(" url=").append(publicUrl)
                        .append(deleted ? " DELETED]" : "]");
                if (deleted) {
                    continue; // soft-deleted
                }
                String address = minecraftAddress(publicUrl);
                if (address != null) {
                    out.add(new GroundsServer(name, address, state, type)); // paper / velocity / gamemode
                } else if ("minestom".equals(publicTypes.get(name))) {
                    // Minestom: forge typed it 'service' (https URL, no minecraft routing). It's a
                    // backend reachable only via the proxy, so we show it for inspection (non-joinable).
                    out.add(new GroundsServer(name, hostOf(publicUrl), state, "minestom"));
                }
                // else: a real HTTP/gRPC service — not a Minecraft server, skip.
            }

            cursor = str(body, "nextCursor");
            if (cursor == null) {
                break;
            }
        }
        Constants.LOG.debug("[{}] project {}: {} deployment(s) total, {} minecraft server(s):{}",
                Constants.MOD_NAME, projectId, total, out.size(), raw.length() == 0 ? " (none)" : raw);
        return out;
    }

    /**
     * Maps deploymentName -&gt; manifest {@code publicType} (e.g. "minestom") via {@code /v1/apps}.
     * Best-effort: returns an empty map if apps can't be read, so the minecraft:// path still works.
     */
    private Map<String, String> fetchPublicTypes(String accessToken, String projectId) {
        Map<String, String> map = new HashMap<>();
        try {
            JsonObject body = getJson("/v1/apps?projectId=" + enc(projectId), accessToken);
            for (JsonElement appEl : itemsOf(body)) {
                JsonElement flavors = appEl.getAsJsonObject().get("flavors");
                if (flavors == null || !flavors.isJsonArray()) {
                    continue;
                }
                for (JsonElement fEl : flavors.getAsJsonArray()) {
                    JsonObject f = fEl.getAsJsonObject();
                    String depName = str(f, "deploymentName");
                    String pub = publicTypeOf(f);
                    if (depName != null && pub != null) {
                        map.put(depName, pub);
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOG.debug("[{}] could not read /v1/apps publicTypes: {}", Constants.MOD_NAME, e.toString());
        }
        return map;
    }

    /** The flavor's manifest {@code publicType}, falling back to its flavorKey (== publicType when inferred). */
    private static String publicTypeOf(JsonObject flavor) {
        JsonElement lp = flavor.get("latestPush");
        if (lp != null && lp.isJsonObject()) {
            JsonElement m = lp.getAsJsonObject().get("manifest");
            if (m != null && m.isJsonObject()) {
                String pt = str(m.getAsJsonObject(), "publicType");
                if (pt != null && !pt.isBlank()) {
                    return pt;
                }
            }
        }
        return str(flavor, "key");
    }

    /** Strips scheme + path from a URL, leaving {@code host[:port]} — for displaying non-minecraft backends. */
    static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        int scheme = url.indexOf("://");
        String rest = scheme >= 0 ? url.substring(scheme + 3) : url;
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    /** Returns the connect address for a {@code minecraft://host[:port]} publicUrl, or null. */
    static String minecraftAddress(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(Constants.MINECRAFT_URL_SCHEME)) {
            return null;
        }
        String rest = publicUrl.substring(Constants.MINECRAFT_URL_SCHEME.length());
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        return rest.isBlank() ? null : rest;
    }

    /** {@code GET /v1/pushes?statusGroup=in_flight} -> builds/deploys currently in progress. */
    public List<Push> listInFlightPushes(String accessToken, String projectId) throws Exception {
        JsonObject body = getJson("/v1/pushes?statusGroup=in_flight&limit=20&projectId=" + enc(projectId), accessToken);
        List<Push> out = new ArrayList<>();
        for (JsonElement el : itemsOf(body)) {
            JsonObject o = el.getAsJsonObject();
            out.add(new Push(str(o, "id"), str(o, "status"), pushAppName(o)));
        }
        return out;
    }

    private static String pushAppName(JsonObject o) {
        JsonElement m = o.get("manifest");
        if (m != null && m.isJsonObject()) {
            String n = str(m.getAsJsonObject(), "name");
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        String id = str(o, "id");
        return id != null ? id : "build";
    }

    /** {@code POST /v1/deployments/:name/resume} -> wake a paused deployment. */
    public void resume(String accessToken, String name, String projectId) throws Exception {
        mutate(accessToken, "POST", "/v1/deployments/" + enc(name) + "/resume?projectId=" + enc(projectId), null);
    }

    /** {@code POST /v1/pushes/:id/retry} -> re-run a failed build/deploy. */
    public void retryPush(String accessToken, String pushId, String projectId) throws Exception {
        mutate(accessToken, "POST", "/v1/pushes/" + enc(pushId) + "/retry?projectId=" + enc(projectId), null);
    }

    /** Latest push for a deployment via {@code GET /v1/apps} (flavor.deploymentName -> latestPush). */
    public Push getLatestPush(String accessToken, String projectId, String deploymentName) throws Exception {
        JsonObject body = getJson("/v1/apps?projectId=" + enc(projectId), accessToken);
        for (JsonElement appEl : itemsOf(body)) {
            JsonObject app = appEl.getAsJsonObject();
            JsonElement flavors = app.get("flavors");
            if (flavors == null || !flavors.isJsonArray()) {
                continue;
            }
            for (JsonElement fEl : flavors.getAsJsonArray()) {
                JsonObject f = fEl.getAsJsonObject();
                if (deploymentName.equals(str(f, "deploymentName"))) {
                    JsonElement lp = f.get("latestPush");
                    if (lp != null && lp.isJsonObject()) {
                        JsonObject p = lp.getAsJsonObject();
                        return new Push(str(p, "id"), str(p, "status"), str(app, "name"));
                    }
                    return null;
                }
            }
        }
        return null;
    }

    /** App name for a deployment, via {@code GET /v1/apps} (flavor.deploymentName -> app.name). */
    public String getAppName(String accessToken, String projectId, String deploymentName) throws Exception {
        JsonObject body = getJson("/v1/apps?projectId=" + enc(projectId), accessToken);
        for (JsonElement appEl : itemsOf(body)) {
            JsonObject app = appEl.getAsJsonObject();
            JsonElement flavors = app.get("flavors");
            if (flavors == null || !flavors.isJsonArray()) {
                continue;
            }
            for (JsonElement fEl : flavors.getAsJsonArray()) {
                if (deploymentName.equals(str(fEl.getAsJsonObject(), "deploymentName"))) {
                    String n = str(app, "name");
                    return n != null ? n : deploymentName;
                }
            }
        }
        return deploymentName;
    }

    /** Ready dev pushes for an app (rollback candidates), newest first. */
    public List<RollbackTarget> listRollbackTargets(String accessToken, String projectId, String appName) throws Exception {
        JsonObject body = getJson("/v1/pushes?app=" + enc(appName) + "&target=dev&statusGroup=ready&limit=20&projectId="
                + enc(projectId), accessToken);
        List<RollbackTarget> out = new ArrayList<>();
        for (JsonElement el : itemsOf(body)) {
            JsonObject o = el.getAsJsonObject();
            out.add(new RollbackTarget(str(o, "id"), str(o, "imageTag"), str(o, "createdAt")));
        }
        return out;
    }

    /** {@code POST /v1/deployments/:name/rollback} -> pin the deployment to a prior push's image. */
    public void rollback(String accessToken, String name, String projectId, String pushId) throws Exception {
        mutate(accessToken, "POST", "/v1/deployments/" + enc(name) + "/rollback?projectId=" + enc(projectId),
                "{\"pushId\":\"" + pushId + "\"}");
    }

    /** {@code GET /v1/deployments/:name/runtime} -> live replicas + devCluster state. */
    public DeploymentRuntime getRuntime(String accessToken, String name, String projectId) throws Exception {
        JsonObject o = getJson("/v1/deployments/" + enc(name) + "/runtime?projectId=" + enc(projectId), accessToken);
        int ready = 0;
        int desired = 0;
        if (o.has("replicas") && o.get("replicas").isJsonObject()) {
            JsonObject r = o.getAsJsonObject("replicas");
            ready = intOr(r, "ready");
            desired = intOr(r, "desired");
        }
        String devClusterState = null;
        if (o.has("devCluster") && o.get("devCluster").isJsonObject()) {
            devClusterState = str(o.getAsJsonObject("devCluster"), "state");
        }
        return new DeploymentRuntime(ready, desired, devClusterState, str(o, "imageTag"));
    }

    /** {@code GET /v1/cluster/nats} -> broker stats, declared events, live connections, JetStream. */
    public Nats.Snapshot getClusterNats(String accessToken, String projectId) throws Exception {
        JsonObject o = getJson("/v1/cluster/nats?projectId=" + enc(projectId), accessToken);

        Nats.Broker broker = null;
        if (o.has("broker") && o.get("broker").isJsonObject()) {
            JsonObject b = o.getAsJsonObject("broker");
            broker = new Nats.Broker(intOr(b, "connections"), longOr(b, "inMsgs"), longOr(b, "outMsgs"),
                    longOr(b, "inBytes"), longOr(b, "outBytes"), intOr(b, "subscriptions"), intOr(b, "slowConsumers"));
        }

        List<Nats.Event> events = new ArrayList<>();
        if (o.has("events") && o.get("events").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("events")) {
                JsonObject e = el.getAsJsonObject();
                events.add(new Nats.Event(str(e, "app"), str(e, "subject"), str(e, "dir")));
            }
        }

        List<Nats.Conn> connections = new ArrayList<>();
        if (o.has("connections") && o.get("connections").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("connections")) {
                JsonObject c = el.getAsJsonObject();
                connections.add(new Nats.Conn(longOr(c, "cid"), str(c, "name"),
                        stringList(c, "subjects"), c.has("system") && c.get("system").getAsBoolean()));
            }
        }

        Nats.JetStream jetstream = null;
        if (o.has("jetstream") && o.get("jetstream").isJsonObject()) {
            JsonObject j = o.getAsJsonObject("jetstream");
            List<Nats.Stream> streams = new ArrayList<>();
            if (j.has("list") && j.get("list").isJsonArray()) {
                for (JsonElement el : j.getAsJsonArray("list")) {
                    JsonObject s = el.getAsJsonObject();
                    streams.add(new Nats.Stream(str(s, "name"), stringList(s, "subjects"),
                            longOr(s, "messages"), longOr(s, "bytes"), intOr(s, "consumers"),
                            longOr(s, "firstSeq"), longOr(s, "lastSeq")));
                }
            }
            jetstream = new Nats.JetStream(intOr(j, "streams"), longOr(j, "messages"), longOr(j, "bytes"), streams);
        }

        return new Nats.Snapshot(broker, events, connections, jetstream);
    }

    /** Generic mutation (POST/PATCH/PUT/DELETE). Returns the parsed body, or an empty object. */
    public JsonObject mutate(String accessToken, String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");
        if (jsonBody != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new ForgeApiException(res.statusCode(),
                    method + " " + path + " -> HTTP " + res.statusCode() + ": " + truncate(res.body()));
        }
        String body = res.body();
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /**
     * Consumes a {@code text/event-stream} endpoint, invoking {@code handler} per SSE frame until
     * the stream ends or {@code cancelled} returns true. Blocking — call on a background thread.
     */
    public void streamSse(String accessToken, String path, SseHandler handler,
                          java.util.function.BooleanSupplier cancelled) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(10))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<java.util.stream.Stream<String>> res =
                http.send(req, HttpResponse.BodyHandlers.ofLines());
        if (res.statusCode() / 100 != 2) {
            throw new ForgeApiException(res.statusCode(), "SSE " + path + " -> HTTP " + res.statusCode());
        }
        String event = "message";
        StringBuilder data = new StringBuilder();
        java.util.Iterator<String> it = res.body().iterator();
        while (it.hasNext()) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            String line = it.next();
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    handler.onEvent(event, data.toString());
                }
                event = "message";
                data.setLength(0);
            } else if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            }
        }
    }

    /** SSE frame sink. */
    public interface SseHandler {
        void onEvent(String event, String data);
    }

    // --- helpers ------------------------------------------------------------

    private JsonObject getJson(String path, String accessToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new ForgeApiException(res.statusCode(),
                    "GET " + path + " -> HTTP " + res.statusCode() + ": " + truncate(res.body()));
        }
        try {
            return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ForgeApiException(res.statusCode(),
                    "GET " + path + " -> invalid JSON: " + truncate(res.body()));
        }
    }

    private static JsonArray itemsOf(JsonObject body) {
        JsonElement items = body.get("items");
        return (items != null && items.isJsonArray()) ? items.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }

    private static int intOr(JsonObject o, String key) {
        JsonElement el = o.get(key);
        try {
            return (el == null || el.isJsonNull()) ? 0 : el.getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long longOr(JsonObject o, String key) {
        JsonElement el = o.get(key);
        try {
            return (el == null || el.isJsonNull()) ? 0L : el.getAsLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static List<String> stringList(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement el = o.get(key);
        if (el != null && el.isJsonArray()) {
            for (JsonElement s : el.getAsJsonArray()) {
                if (!s.isJsonNull()) {
                    out.add(s.getAsString());
                }
            }
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
