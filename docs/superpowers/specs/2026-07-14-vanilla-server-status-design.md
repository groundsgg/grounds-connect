# Vanilla Server Status Design

## Goal

Grounds Connect server rows must present Minecraft reachability, latency, and player information with the same visual language and hover behavior as Minecraft 26.2's multiplayer list. The existing Grounds deployment-health badge remains visible as a separate platform signal.

## Row Layout

The selected layout keeps the Vanilla status cluster at the familiar top-right position:

- Server name at the top left after the existing expand and favorite controls
- Server address at the bottom left
- Vanilla player count followed by the 10-by-8-pixel ping icon at the top right
- Grounds deployment health, such as `● online 1/1`, at the bottom right

The name is truncated before the Vanilla status cluster. The address is truncated before the Grounds health badge. Nested backend styling, favorite stars, proxy expansion, health colors, and replica counts remain unchanged.

## Vanilla Status Behavior

A focused `VanillaServerStatusRenderer` owns the status-to-visual mapping and draws the right-aligned player count, ping sprite, and hover tooltips. `ServerEntry` supplies the row bounds and delegates the Vanilla status cluster to this component.

The renderer uses Minecraft's existing `server_list` sprites and state semantics:

- `PINGING`: cycle through `pinging_1` to `pinging_5` every 100 milliseconds
- `SUCCESSFUL`: select `ping_5`, `ping_4`, `ping_3`, `ping_2`, or `ping_1` using Vanilla's latency thresholds of 150, 300, 600, and 1000 milliseconds
- `UNREACHABLE`: show the Vanilla `unreachable` sprite
- `INCOMPATIBLE`: show the Vanilla `incompatible` sprite

The exact ping duration is not rendered as inline text. Hovering the ping icon shows Minecraft's localized ping, connecting, unreachable, or incompatible tooltip.

The player count uses the formatted `ServerData.status` supplied by `ServerStatusPinger`. Hovering that count shows `ServerData.playerList`, including Minecraft's localized additional-player line when the server provides only a sample.

## Ping Data Flow

Before a ping is submitted to the existing background scheduler, the entry's `ServerData` moves to `PINGING` and its transient status fields are reset as Vanilla does.

The existing `ServerStatusPinger` fills the MOTD, protocol, player count, player sample, favicon, and latency. Completion updates the `ServerData` state to `SUCCESSFUL` or `INCOMPATIBLE` by comparing the returned protocol with Minecraft's current protocol. Startup failures set the state to `UNREACHABLE`.

The renderer reads the resulting `ServerData`; it does not own network work or duplicate ping values. The old textual `pinging…`, player-count, and millisecond string is removed.

## Grounds Health Separation

Minecraft status and Grounds deployment health describe different systems and must not overwrite one another:

- The Vanilla cluster reports whether the Minecraft endpoint responds, its latency, protocol compatibility, and players.
- The Grounds badge reports the Forge deployment/runtime state and replica readiness.

A failed Minecraft ping may therefore appear alongside a green Grounds health badge. This is intentional and helps distinguish an endpoint/network problem from a platform deployment problem.

## Error Handling

Unknown-host and synchronous ping-start failures move the entry to `UNREACHABLE` without adding new log messages. Existing stale-project callback guards and background ping scheduling remain unchanged.

If player details are absent, the renderer omits the player tooltip while preserving the formatted player count when available. If no player count is available, it reserves only the ping-icon area.

## Testing and Verification

Unit tests cover the renderer's deterministic status mapping:

- All five successful latency thresholds
- All five 100-millisecond loading frames
- Unreachable and incompatible sprites
- Player-count presentation and player-tooltip propagation

Rendering integration must preserve collision-free name and address widths alongside the Vanilla top-right cluster and Grounds bottom-right badge.

Run the complete repository verification with escalated permissions:

```text
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Verify in the Minecraft 26.2 client:

1. Open the Grounds server list and observe the animated Vanilla ping frames.
2. Confirm the animation becomes the correct signal-strength sprite.
3. Hover the ping sprite and verify the localized exact-latency tooltip.
4. Hover the player count and verify the sampled player list.
5. Confirm the Grounds health badge remains at the bottom right and unchanged by ping results.
6. Switch projects and refresh, confirming old rows disappear while the list background remains stable.
