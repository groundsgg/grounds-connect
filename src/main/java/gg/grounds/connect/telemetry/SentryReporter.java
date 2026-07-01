package gg.grounds.connect.telemetry;

import gg.grounds.connect.Constants;
import gg.grounds.connect.config.GroundsConfig;
import io.sentry.Breadcrumb;
import io.sentry.ITransaction;
import io.sentry.ProfileLifecycle;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.protocol.Message;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/** Central Sentry integration. Keeps telemetry tags low-cardinality and non-user-specific. */
public final class SentryReporter {
  private static final String DSN =
      "https://fa77fc5d8626245c5e0f3d7674a29d6b@o4511571193692160.ingest.de.sentry.io/4511652265328720";
  private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
  private static final Pattern AUTHORIZATION_HEADER =
      Pattern.compile("(?i)(authorization\\s*:\\s*)bearer\\s+[^\\s,;]+");
  private static final Pattern TOKEN_QUERY =
      Pattern.compile("(?i)(access_token|refresh_token|id_token|token|device_code)=([^\\s&#]+)");
  private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[^\\s,;]+");
  private static final Pattern LONG_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_-]{9,}");

  private SentryReporter() {}

  public static void init() {
    if (!INITIALIZED.compareAndSet(false, true)) {
      return;
    }

    Map<String, String> env = System.getenv();
    Sentry.init(
        options -> {
          options.setDsn(DSN);
          options.setRelease("grounds-connect@" + modVersion());
          options.setEnvironment(env.getOrDefault("GROUNDS_CONNECT_ENV", "production"));
          options.setDebug(Boolean.parseBoolean(env.getOrDefault("GROUNDS_SENTRY_DEBUG", "false")));
          options.setSendDefaultPii(false);
          options.setAttachServerName(false);
          options.setServerName(null);
          options.setEnableUncaughtExceptionHandler(true);
          options.setEnableAutoSessionTracking(true);
          options.setAttachStacktrace(true);
          options.setTracesSampleRate(sampleRate(env, "GROUNDS_SENTRY_TRACES_SAMPLE_RATE", 0.2));
          options.setProfilesSampleRate(
              sampleRate(env, "GROUNDS_SENTRY_PROFILES_SAMPLE_RATE", 0.05));
          options.setProfileSessionSampleRate(
              sampleRate(env, "GROUNDS_SENTRY_PROFILE_SESSION_SAMPLE_RATE", 0.05));
          options.setProfileLifecycle(ProfileLifecycle.TRACE);
          options.addInAppInclude("gg.grounds.connect");
          String apiHost = apiHostTag(GroundsConfig.get().apiBaseUrl());
          if (apiHost != null) {
            options.setTracePropagationTargets(java.util.List.of(apiHost));
          }
          options.getLogs().setEnabled(true);
          options.getMetrics().setEnabled(true);
          options.setBeforeSend(SentryReporter::beforeSend);
          options.setBeforeBreadcrumb(SentryReporter::beforeBreadcrumb);
        });

    Sentry.configureScope(
        scope -> {
          scope.setUser(null);
          setTag("mod.id", Constants.MOD_ID);
          setTag("mod.name", Constants.MOD_NAME);
          setTag("mod.version", modVersion());
          setTag("minecraft.version", minecraftVersion());
          setTag("fabric.loader.version", fabricLoaderVersion());
          setTag("java.version", System.getProperty("java.version", "unknown"));
          setTag("os.name", System.getProperty("os.name", "unknown"));
          String apiHost = apiHostTag(GroundsConfig.get().apiBaseUrl());
          if (apiHost != null) {
            setTag("api.host", apiHost);
          }
        });

    info("Sentry initialized successfully (component=telemetry)");
    metric("grounds_connect.sentry.initialized");

    if (isDevEnvironment()
        && Boolean.parseBoolean(env.getOrDefault("GROUNDS_SENTRY_TEST_EVENT", "false"))) {
      captureMessage("Sentry test event captured (component=telemetry)", SentryLevel.INFO);
    }
  }

  public static void captureHandled(Throwable throwable, String operation, String reason) {
    if (throwable == null || !Sentry.isEnabled()) {
      return;
    }
    String safeOperation = safeTag(operation);
    String safeReason = safeTag(reason);
    metric("grounds_connect.error.handled");
    error("Handled operation failed (operation={}, reason={})", safeOperation, safeReason);
    Sentry.captureException(
        throwable,
        scope -> {
          scope.setUser(null);
          scope.setTag("operation", safeOperation);
          scope.setTag("reason", safeReason);
          scope.setLevel(SentryLevel.WARNING);
        });
  }

  public static void captureApiFailure(Throwable throwable, String operation, Integer statusCode) {
    if (statusCode != null && !shouldCaptureApiFailure(statusCode)) {
      return;
    }
    captureHandled(throwable, operation, statusCode == null ? "api_failure" : "http_" + statusCode);
  }

  public static <T> T trace(String operation, String description, ThrowingSupplier<T> supplier)
      throws Exception {
    String safeOperation = safeTag(operation);
    String safeDescription = sanitizeMessage(description);
    ITransaction transaction = Sentry.startTransaction(safeDescription, safeOperation);
    try (var ignored = transaction.makeCurrent()) {
      T result = supplier.get();
      transaction.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable t) {
      transaction.setThrowable(t);
      transaction.setStatus(SpanStatus.INTERNAL_ERROR);
      throw t;
    } finally {
      transaction.finish();
    }
  }

  public static void trace(String operation, String description, ThrowingRunnable runnable)
      throws Exception {
    trace(
        operation,
        description,
        () -> {
          runnable.run();
          return null;
        });
  }

  public static void metric(String name) {
    if (Sentry.isEnabled()) {
      Sentry.metrics().count(name);
    }
  }

  public static void info(String message, Object... args) {
    if (Sentry.isEnabled()) {
      Sentry.logger().info(sanitizeMessage(message), sanitizeArgs(args));
    }
  }

  public static void warn(String message, Object... args) {
    if (Sentry.isEnabled()) {
      Sentry.logger().warn(sanitizeMessage(message), sanitizeArgs(args));
    }
  }

  public static void error(String message, Object... args) {
    if (Sentry.isEnabled()) {
      Sentry.logger().error(sanitizeMessage(message), sanitizeArgs(args));
    }
  }

  static String apiHostTag(String apiBaseUrl) {
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(apiBaseUrl);
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return null;
      }
      return uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static double sampleRate(Map<String, String> env, String key, double fallback) {
    String raw = env.get(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      double value = Double.parseDouble(raw);
      return Math.max(0.0, Math.min(1.0, value));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  static boolean shouldCaptureApiFailure(int statusCode) {
    return statusCode == 429 || statusCode >= 500;
  }

  static String sanitizeMessage(String message) {
    if (message == null) {
      return "";
    }
    String sanitized = AUTHORIZATION_HEADER.matcher(message).replaceAll("$1[Filtered]");
    sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer [Filtered]");
    sanitized = TOKEN_QUERY.matcher(sanitized).replaceAll("$1=[Filtered]");
    return sanitized;
  }

  public static String httpDescription(String method, String path) {
    String withoutQuery = path == null ? "" : path.split("\\?", 2)[0];
    StringBuilder safePath = new StringBuilder();
    for (String segment : withoutQuery.split("/")) {
      if (segment.isBlank()) {
        continue;
      }
      safePath.append('/');
      safePath.append(LONG_PATH_SEGMENT.matcher(segment).matches() ? ":id" : segment);
    }
    return method + " " + (safePath.length() == 0 ? "/" : safePath);
  }

  private static SentryEvent beforeSend(SentryEvent event, io.sentry.Hint hint) {
    event.setUser(null);
    event.setRequest(null);
    event.setServerName(null);
    Message message = event.getMessage();
    if (message != null) {
      message.setFormatted(sanitizeMessage(message.getFormatted()));
      message.setMessage(sanitizeMessage(message.getMessage()));
    }
    return event;
  }

  private static Breadcrumb beforeBreadcrumb(Breadcrumb breadcrumb, io.sentry.Hint hint) {
    breadcrumb.setMessage(sanitizeMessage(breadcrumb.getMessage()));
    Object url = breadcrumb.getData("url");
    if (url instanceof String value) {
      breadcrumb.setData("url", sanitizeMessage(value.split("\\?", 2)[0]));
    }
    return breadcrumb;
  }

  private static Object[] sanitizeArgs(Object[] args) {
    if (args == null || args.length == 0) {
      return args;
    }
    Object[] sanitized = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      sanitized[i] = args[i] instanceof String value ? sanitizeMessage(value) : args[i];
    }
    return sanitized;
  }

  private static void captureMessage(String message, SentryLevel level) {
    Sentry.captureMessage(sanitizeMessage(message), level);
  }

  private static void setTag(String key, String value) {
    if (value != null && !value.isBlank()) {
      Sentry.setTag(key, safeTag(value));
    }
  }

  private static String safeTag(String value) {
    return sanitizeMessage(Objects.toString(value, "unknown")).toLowerCase(Locale.ROOT);
  }

  private static String modVersion() {
    return FabricLoader.getInstance()
        .getModContainer(Constants.MOD_ID)
        .map(container -> container.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
  }

  private static String minecraftVersion() {
    return FabricLoader.getInstance()
        .getModContainer("minecraft")
        .map(container -> container.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
  }

  private static String fabricLoaderVersion() {
    return FabricLoader.getInstance()
        .getModContainer("fabricloader")
        .map(container -> container.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
  }

  private static boolean isDevEnvironment() {
    return FabricLoader.getInstance().isDevelopmentEnvironment();
  }

  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }
}
