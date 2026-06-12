# Changelog

## [0.2.3](https://github.com/groundsgg/grounds-connect/compare/v0.2.2...v0.2.3) (2026-06-12)


### Bug Fixes

* **release:** attach mod jar to github releases ([#3](https://github.com/groundsgg/grounds-connect/issues/3)) ([a85da15](https://github.com/groundsgg/grounds-connect/commit/a85da156926e8c47f7cf9adcea5c1edc70300375))

## [0.2.2](https://github.com/groundsgg/grounds-connect/compare/v0.2.1...v0.2.2) (2026-06-12)


### Bug Fixes

* **modrinth:** rename project to grounds connect ([#1](https://github.com/groundsgg/grounds-connect/issues/1)) ([f7d75d1](https://github.com/groundsgg/grounds-connect/commit/f7d75d1f23c3ce258ad11cb232890fb359650de5))

## 0.2.1

Maintenance release — no gameplay changes. The publish pipeline now runs on the Node 24 runtime.

## 0.2.0

> **Minecraft 26.1.2** · Fabric Loader ≥ 0.19 · Java 21+ · Fabric API **now required**

### Added

- **Velocity proxy grouping** — backend game servers (Paper / gamemode / Minestom) are hidden from
  the top level and nest under an expandable proxy row (with a backend count). Backends show live
  health but are inspection-only; you join through the proxy.
- **NATS view** — a per-project panel with broker stats, declared event subjects, live connections
  and JetStream streams, plus a **live message tail** (subject filter, pause/clear).
- **Wake-on-join** — joining a paused server resumes it first, then connects.
- **Retry** a failed build and **Rollback** to a prior build (owner/editor only).
- **Log console Runtime/Build toggle** — tail runtime logs or the latest build's logs.
- **Search/filter** box and **favorites/pins** (★) that sort to the top.
- **Platform-unreachable banner** (polls the platform's readiness endpoint).
- **Quick-connect keybind** (Controls → Multiplayer, unbound by default).
- A mod **icon**.

### Changed

- **Fabric API is now required** (used by the quick-connect keybind).

### Fixed

- Server rows no longer overlap the status/ping when names or addresses are long.
- Deploy-status toasts are cleaned up on completion; quieter diagnostic logging.

## 0.1.0

A Minecraft **Fabric client mod** that brings the [Grounds](https://grounds.gg) developer platform
into the game. Log in once, then browse, monitor, and join your project's Minecraft servers — with
live health, build/deploy notifications, and an in-game log console — without leaving Minecraft.

> **Minecraft 26.1.2** · Fabric Loader ≥ 0.19 · Java 21+ · Fabric API **not** required

### Added

- **Log in from the main menu** — a "Grounds Platform" button under *Multiplayer*; secure device-code
  login (PKCE), confirmed in your browser (no in-game password).
- **Dedicated Grounds screen** — pick a project and see its connectable Minecraft servers.
- **Connect** via the Join button or by **double-clicking** a server.
- **Live health badges** — `online` / `starting…` / `paused` / `offline` with replica counts, polled
  from the platform (~5 s).
- **Live Minecraft ping** — real player count and latency per server.
- **Return to the Grounds screen** after leaving a Grounds server (instead of the vanilla list).
- **Build/deploy toasts** — in-flight builds stream their status (`Building… → Deployed ✓` / failures)
  and keep updating even after the screen closes.
- **In-game log console** — auto-tailing runtime logs with **Copy** and **Share to mclo.gs**
  (uploads the buffer and copies the link).
- English-only UI.
