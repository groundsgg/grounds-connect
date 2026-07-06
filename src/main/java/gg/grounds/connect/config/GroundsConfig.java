package gg.grounds.connect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.grounds.connect.Constants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Non-secret mod settings persisted to {@code config/grounds_connect/config.json}. (Tokens live
 * separately in {@link gg.grounds.connect.auth.TokenStore}.)
 */
public final class GroundsConfig {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final GroundsConfig INSTANCE = new GroundsConfig();

  private String apiBaseUrl = Constants.DEFAULT_API_BASE_URL;
  private String selectedProjectId = null;

  /** Pinned server addresses (sorted to the top of the list). */
  private final Set<String> favorites = new LinkedHashSet<>();

  /** Velocity-proxy deployment names whose backend list is expanded (collapsed by default). */
  private final Set<String> expandedProxies = new LinkedHashSet<>();

  private GroundsConfig() {}

  public static GroundsConfig get() {
    return INSTANCE;
  }

  public static Path configDir() {
    return FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID);
  }

  private static Path configFile() {
    return configDir().resolve("config.json");
  }

  /** Resolved API base: env override wins, then stored value, then default. */
  public String apiBaseUrl() {
    String env = System.getenv("GROUNDS_API_URL");
    if (env != null && !env.isBlank()) {
      return normalizeApiBaseUrl(env);
    }
    return normalizeApiBaseUrl(apiBaseUrl);
  }

  public String selectedProjectId() {
    return selectedProjectId;
  }

  public void setSelectedProjectId(String id) {
    this.selectedProjectId = id;
    save();
  }

  public boolean isFavorite(String address) {
    return address != null && favorites.contains(address);
  }

  /** Toggles the pin for {@code address}, persists, and returns the new pinned state. */
  public boolean toggleFavorite(String address) {
    if (address == null) {
      return false;
    }
    boolean nowPinned;
    if (favorites.contains(address)) {
      favorites.remove(address);
      nowPinned = false;
    } else {
      favorites.add(address);
      nowPinned = true;
    }
    save();
    return nowPinned;
  }

  public boolean isExpanded(String proxyName) {
    return proxyName != null && expandedProxies.contains(proxyName);
  }

  /** Toggles whether a proxy's backends are shown, and persists. */
  public void toggleExpanded(String proxyName) {
    if (proxyName == null) {
      return;
    }
    if (!expandedProxies.add(proxyName)) {
      expandedProxies.remove(proxyName);
    }
    save();
  }

  public void load() {
    Path file = configFile();
    if (!Files.exists(file)) {
      return;
    }
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      if (obj.has("apiBaseUrl") && !obj.get("apiBaseUrl").isJsonNull()) {
        this.apiBaseUrl = normalizeApiBaseUrl(obj.get("apiBaseUrl").getAsString());
      }
      if (obj.has("selectedProjectId") && !obj.get("selectedProjectId").isJsonNull()) {
        this.selectedProjectId = obj.get("selectedProjectId").getAsString();
      }
      if (obj.has("favorites") && obj.get("favorites").isJsonArray()) {
        favorites.clear();
        for (JsonElement el : obj.getAsJsonArray("favorites")) {
          if (!el.isJsonNull()) {
            favorites.add(el.getAsString());
          }
        }
      }
      if (obj.has("expandedProxies") && obj.get("expandedProxies").isJsonArray()) {
        expandedProxies.clear();
        for (JsonElement el : obj.getAsJsonArray("expandedProxies")) {
          if (!el.isJsonNull()) {
            expandedProxies.add(el.getAsString());
          }
        }
      }
    } catch (Exception e) {
      Constants.LOG.warn("[grounds] Failed to read config.json, using defaults", e);
    }
  }

  public void save() {
    JsonObject obj = new JsonObject();
    obj.addProperty("apiBaseUrl", apiBaseUrl);
    if (selectedProjectId != null) {
      obj.addProperty("selectedProjectId", selectedProjectId);
    }
    if (!favorites.isEmpty()) {
      JsonArray arr = new JsonArray();
      favorites.forEach(arr::add);
      obj.add("favorites", arr);
    }
    if (!expandedProxies.isEmpty()) {
      JsonArray arr = new JsonArray();
      expandedProxies.forEach(arr::add);
      obj.add("expandedProxies", arr);
    }
    try {
      Files.createDirectories(configDir());
      Files.writeString(configFile(), GSON.toJson(obj), StandardCharsets.UTF_8);
    } catch (IOException e) {
      Constants.LOG.warn("[grounds] Failed to write config.json", e);
    }
  }

  static String normalizeApiBaseUrl(String value) {
    String normalized = stripTrailingSlash(value.trim());
    if (Constants.LEGACY_API_BASE_URL.equals(normalized)) {
      return Constants.DEFAULT_API_BASE_URL;
    }
    return normalized;
  }

  private static String stripTrailingSlash(String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}
