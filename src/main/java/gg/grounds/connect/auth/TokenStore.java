package gg.grounds.connect.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.grounds.connect.Constants;
import gg.grounds.connect.config.GroundsConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Persists {@link Credentials} to {@code config/grounds_connect/credentials.json} (owner-only where
 * supported).
 */
public final class TokenStore {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private TokenStore() {}

  private static Path file() {
    return GroundsConfig.configDir().resolve("credentials.json");
  }

  public static Credentials load() {
    Path file = file();
    if (!Files.exists(file)) {
      return null;
    }
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      Credentials c = GSON.fromJson(json, Credentials.class);
      if (c == null || c.accessToken == null) {
        return null;
      }
      return c;
    } catch (Exception e) {
      Constants.LOG.warn("[grounds] Failed to read credentials.json", e);
      return null;
    }
  }

  public static void save(Credentials credentials) {
    Path file = file();
    try {
      Files.createDirectories(GroundsConfig.configDir());
      Files.writeString(file, GSON.toJson(credentials), StandardCharsets.UTF_8);
      restrictPermissions(file);
    } catch (IOException e) {
      Constants.LOG.warn("[grounds] Failed to write credentials.json", e);
    }
  }

  public static void clear() {
    try {
      Files.deleteIfExists(file());
    } catch (IOException e) {
      Constants.LOG.warn("[grounds] Failed to delete credentials.json", e);
    }
  }

  private static void restrictPermissions(Path file) {
    try {
      Set<PosixFilePermission> ownerOnly =
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(file, PosixFilePermissions.asFileAttribute(ownerOnly).value());
    } catch (UnsupportedOperationException | IOException ignored) {
      // Non-POSIX filesystem (Windows) — best effort only.
    }
  }
}
