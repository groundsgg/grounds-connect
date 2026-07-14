# Grounds Connect

Grounds Connect is a **Minecraft Fabric client mod** for developers and teams using the
[Grounds](https://grounds.gg) developer platform. It brings your Grounds projects directly into
Minecraft, so you can sign in, browse project servers, inspect deployment state, follow logs, and
join test environments without switching back and forth between Minecraft and external dashboards.

This is not a gameplay content mod: it does not add blocks, items, mobs, or dimensions. It is a
developer and operator tool for working with Grounds-powered Minecraft servers.

> **Minecraft 26.2** · Fabric Loader ≥ 0.19 · Java 25+ · Fabric API required

## Features

- **Grounds login from Minecraft** — open the Grounds Connect screen from the main menu and sign in
  using a secure browser-based device code flow. No password is entered in-game.
- **Project server browser** — choose a Grounds project and view its Minecraft servers from a
  dedicated in-game screen.
- **One-click server joining** — join available servers from the Grounds screen, including
  wake-on-join for paused servers.
- **Live server status** — see health states such as `online`, `starting…`, `paused`, and `offline`,
  plus live ping, latency, and player counts.
- **Velocity proxy grouping** — backend Paper, gamemode, and Minestom servers are grouped under
  their proxy so project environments are easier to understand.
- **Build and deploy notifications** — in-progress builds and deployments appear as in-game toast
  updates.
- **In-game log console** — tail runtime or build logs from Minecraft, copy logs, or share them
  through [mclo.gs](https://mclo.gs).
- **NATS inspection tools** — view project broker status, event subjects, JetStream streams, live
  connections, and message tails.
- **Developer actions** — retry failed builds and roll back deployments when your Grounds account
  has permission.
- **Favorites and search** — pin important servers and filter longer project server lists.
- All network I/O runs off the render thread; results are marshaled back via `Minecraft.execute`.

## Why use it?

Grounds Connect keeps the most common development workflow inside the game: find the right project,
check whether servers are healthy, inspect logs, follow deployments, and connect to test
environments quickly.

Regular players usually only need this mod if they are part of a Grounds-based testing or
development workflow.

## Requirements

- Minecraft **26.2**
- **Fabric Loader** ≥ 0.19.0
- Java 25+
- Fabric API

> **Mappings note:** MC 26.2 ships deobfuscated (no `client_mappings`, no yarn), so the build uses
> official Mojang names with an identity mapping (`net.fabricmc:intermediary:0.0.0:v2`) and Fabric
> Loom `1.17.11`. There is no refmap — that is correct for this era.

## Build

```bash
./gradlew build
# -> build/libs/grounds-connect-0.2.1.jar
```

## Install

Drop `grounds-connect-<version>.jar` into your `.minecraft/mods/` folder alongside Fabric Loader.

## Usage

1. In the **main menu**, click **Grounds Connect** (under *Multiplayer*). If you're not signed in, a
   screen shows a code and a link — open the link, enter the code, approve.
2. Pick a **project** with the selector; its servers appear with live health + ping.
3. **Join** a server (button or double-click). **Logs** opens the runtime log console; **Refresh**
   reloads the list; **Log out** clears your stored token.
4. Build/deploy progress shows as toasts while the screen is (or was) open.

## Configuration

Stored under `.minecraft/config/grounds_connect/`:

| File | Contents |
| --- | --- |
| `config.json` | `apiBaseUrl` (default `https://api.grounds.gg`), `selectedProjectId` |
| `credentials.json` | OIDC tokens (written owner-only on POSIX filesystems) |

Environment override: `GROUNDS_API_URL` takes precedence over `config.json`.

## Architecture

A standalone `GroundsServersScreen` (not an injection into the vanilla list), opened from the only
mixin that touches a vanilla menu (`TitleScreenMixin`); a `MinecraftMixin` redirects the post-leave
screen. `Grounds.services()` exposes focused services for auth, projects, servers, deployments,
logs, and NATS while shared background work and authenticated API calls stay in the core service
layer. The server list is a custom `ObjectSelectionList`, so health badges are drawn directly.

See [CHANGELOG.md](CHANGELOG.md) for the 0.1.0 feature list.
