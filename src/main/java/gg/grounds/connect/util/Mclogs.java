package gg.grounds.connect.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uploads text to mclo.gs (https://api.mclo.gs/) and returns the share URL. */
public final class Mclogs {

    private static final String ENDPOINT = "https://api.mclo.gs/1/log";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "grounds-mclogs");
        t.setDaemon(true);
        return t;
    });

    private Mclogs() {}

    public interface Callback {
        void onUrl(String url);
        void onError(String message);
    }

    /** Uploads off the render thread; callbacks are delivered on the client thread. */
    public static void uploadAsync(String content, Callback cb) {
        EXECUTOR.submit(() -> {
            try {
                String url = upload(content);
                onClient(() -> cb.onUrl(url));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                onClient(() -> cb.onError(msg));
            }
        });
    }

    private static String upload(String content) throws Exception {
        String body = "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "Grounds Developer Platform/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (o.has("success") && o.get("success").getAsBoolean() && o.has("url")) {
            return o.get("url").getAsString();
        }
        throw new RuntimeException(o.has("error") && !o.get("error").isJsonNull()
                ? o.get("error").getAsString() : "HTTP " + res.statusCode());
    }

    private static void onClient(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
