plugins {
    id("fabric-loom") version "1.17.11"
    id("com.diffplug.spotless") version "8.8.0"
    id("io.sentry.jvm.gradle") version "6.15.0"
}

base { archivesName.set(project.property("archives_base_name") as String) }
version = project.property("mod_version") as String
group = project.property("maven_group") as String

dependencies {
    // Minecraft 26.2 ships deobfuscated in this timeline — the version manifest
    // carries no client_mappings and meta.fabricmc.net returns no yarn. The named
    // namespace therefore equals the official jar names; intermediary 0.0.0 is the
    // identity mapping that loom needs to wire up the (now trivial) namespaces.
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:intermediary:0.0.0:v2")

    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    // Keybind uses fabric-key-mapping-api-v1 + fabric-lifecycle-events-v1. We pull the individual
    // module jars with plain `implementation` (not loom's modImplementation): MC 26.2 is
    // deobfuscated, so these jars are already in official names and need no remap — and
    // modImplementation would fail trying to remap the fabric-api sources jar against the identity
    // mappings (there is no "named" namespace to target). Provided at runtime by the full
    // fabric-api (declared in fabric.mod.json), which the player's modpack ships.
    implementation("net.fabricmc.fabric-api:fabric-key-mapping-api-v1:2.0.5+e2bdee789e")
    implementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.1.3+4575b05f9e")

    // Provided by fabric-loader at runtime; needed on the compile classpath for the Mixin
    // annotations (loom does not auto-inject it for this loader/MC combination).
    implementation("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")

    implementation("io.sentry:sentry:8.49.0")
    include("io.sentry:sentry:8.49.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    // No sourcesJar: with the identity (deobfuscated) mappings there is no "named" namespace
    // for loom's source remapper to target, and a sources jar isn't needed to run the mod.
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.35.0")
        removeUnusedImports()
    }
}

val sentryAuthToken = System.getenv("SENTRY_AUTH_TOKEN")?.trim()

sentry {
    includeSourceContext.set(!sentryAuthToken.isNullOrBlank())
    org.set("groundsgg")
    projectName.set("grounds-connect")
    if (!sentryAuthToken.isNullOrBlank()) {
        authToken.set(sentryAuthToken)
    }
}

tasks.register("format") {
    group = "formatting"
    description = "Formats Java sources."
    dependsOn("spotlessApply")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("loader_version"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
