# Grounds DevMod

A **Minecraft Fabric client mod** that brings the [Grounds](https://grounds.gg) developer platform
into the game. Log in once, then browse, monitor, and join your project's Minecraft servers — with
live health, build/deploy notifications, and an in-game log console — without leaving Minecraft.

> **Minecraft 26.1.2** · Fabric Loader ≥ 0.19 · Java 21+ · Fabric API **not** required

```
Main menu                    Grounds (dedicated screen)
┌──────────────────┐         ┌──────────────────────────────────────────────┐
│  Singleplayer    │         │ [Project: duels ▾]  [Refresh]      [Log out]   │
│  Multiplayer     │         │ ● duels-lobby                ● online 1/1      │
│ [Grounds Platform]│  ───►   │   duels-lobby-….mc.grnds.io   12/100  45ms    │
│  Options  Quit   │         │ ● duels-arena                ● paused          │
└──────────────────┘         │              [ Join ] [ Logs ] [ Back ]        │
                             └──────────────────────────────────────────────┘
```

## Features

- **Main-menu login** — a "Grounds Platform" button under *Multiplayer* opens the platform. Sign in
  with the Keycloak **device-authorization grant + PKCE** (public client `grounds-cli`): a short code
  + link you approve in your browser. No password is entered in-game.
- **Dedicated Grounds screen** — pick a **project**; its connectable Minecraft servers
  (deployments whose `publicUrl` uses the `minecraft://` scheme) are listed. **Join** with the button
  or by **double-clicking** a row. Leaving a Grounds server returns you here, not to the vanilla list.
- **Live health badges** — `online` / `starting…` / `paused` / `offline` with replica counts, derived
  from the deployment state + `GET /v1/deployments/:name/runtime`, refreshed every ~5 s.
- **Live Minecraft ping** — real player count and latency, via the vanilla `ServerStatusPinger`.
- **Build/deploy toasts** — in-flight pushes are streamed (`GET /v1/pushes/:id/logs`, SSE) and shown
  as toasts (`Building… → Deploying… → Deployed ✓`, or failures). Streams outlive the screen.
- **In-game log console** — a server's runtime logs (`GET /v1/deployments/:name/logs`, SSE),
  auto-tailing, with **Copy** to clipboard and **Share** to [mclo.gs](https://mclo.gs).
- All network I/O runs off the render thread; results are marshaled back via `Minecraft.execute`.

## Requirements

- Minecraft **26.1.2**
- **Fabric Loader** ≥ 0.19.0
- Java 21+ (the game itself runs on Java 25)
- Fabric API is **not** required.

> **Mappings note:** MC 26.1.2 ships deobfuscated (no `client_mappings`, no yarn), so the build uses
> official Mojang names with an identity mapping (`net.fabricmc:intermediary:0.0.0:v2`) and Fabric
> Loom `1.16.3`. There is no refmap — that is correct for this era.

## Build

```bash
./gradlew build
# -> build/libs/grounds-devmod-0.1.0.jar
```

## Install

Drop `grounds-devmod-<version>.jar` into your `.minecraft/mods/` folder alongside Fabric Loader.

## Usage

1. In the **main menu**, click **Grounds Platform** (under *Multiplayer*). If you're not signed in, a
   screen shows a code and a link — open the link, enter the code, approve.
2. Pick a **project** with the selector; its servers appear with live health + ping.
3. **Join** a server (button or double-click). **Logs** opens the runtime log console; **Refresh**
   reloads the list; **Log out** clears your stored token.
4. Build/deploy progress shows as toasts while the screen is (or was) open.

## Configuration

Stored under `.minecraft/config/grounds/`:

| File | Contents |
| --- | --- |
| `config.json` | `apiBaseUrl` (default `https://platform.grnds.io`), `selectedProjectId` |
| `credentials.json` | OIDC tokens (written owner-only on POSIX filesystems) |

Environment override: `GROUNDS_API_URL` takes precedence over `config.json`.

## Architecture

A standalone `GroundsServersScreen` (not an injection into the vanilla list), opened from the only
mixin that touches a vanilla menu (`TitleScreenMixin`); a `MinecraftMixin` redirects the post-leave
screen. `GroundsSession` owns auth (device-code + token refresh) and all async forge calls
(`ForgeApiClient`, including a generic mutator and an SSE streamer). The server list is a custom
`ObjectSelectionList`, so health badges are drawn directly.

See [CHANGELOG.md](CHANGELOG.md) for the 0.1.0 feature list.
