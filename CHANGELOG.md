# Changelog

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
