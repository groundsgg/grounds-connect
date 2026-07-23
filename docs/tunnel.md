# Gated Network Access via Cloudflare Tunnel

**Status:** concept — nothing here is implemented.
**Scope:** a narrow, opt-in hardening mode. Not a change to how Grounds serves Minecraft in general.

## Summary

Every Minecraft workload on Grounds is reachable today over a public TCP load balancer.
For day-to-day development that is the right trade: it is simple, it works from a vanilla
client, and the blast radius of a dev workspace is small.

It is the wrong trade for a narrow class of events — tournaments, competitive matches,
embargoed builds, customer demos — where the open socket *is* the attack surface and where
an interruption is not recoverable by "try again in five minutes". Application-level
whitelisting does not help: it rejects players *after* they have already opened a TCP
connection and completed a handshake against our infrastructure.

This document proposes a **gated** mode. For a workload marked gated, the only network path
is a Cloudflare Tunnel, and Grounds Connect is the tunnel client. There is no public
address for that workload at all.

## Non-goals

- **Not the default.** `dev`, `staging`, and `production` targets keep the public path.
- **Not a platform-wide DDoS strategy.** This protects specific workloads during specific
  events, not the platform.
- **Not a Spectrum evaluation.** Cloudflare Spectrum is the vendor's answer for proxying
  raw TCP. This proposal deliberately avoids it (Enterprise-only) by putting the tunnel
  client inside the mod. See *Open questions*.
- **Not a replacement for Velocity-side whitelisting.** It is a layer in front of it.

## The path today

```
Minecraft client
      │ raw TCP 25565, public DNS
      ▼
UpCloud LoadBalancer  (grounds-dev-mc, plan "development")
      ▼
mc-router (ServiceWatcher mode)
      │ routes by the hostname in the Minecraft handshake
      ▼
Velocity → Paper / Minestom backends
```

Relevant facts, from `grounds-pulumi/dev/src/platform/mc-router.ts`:

- The wildcard `*.mc.grnds.io` record is **DNS-only**. Cloudflare cannot proxy raw
  Minecraft TCP, so the load balancer's address is public by necessity.
- mc-router runs in ServiceWatcher mode and forwards by the hostname carried in the
  Minecraft handshake, read from the `mc-router.itzg.me/externalServerName` annotation
  that forge stamps onto each workspace's Velocity Service.
- There is **no `defaultServer`** — an unmatched hostname is dropped.

The module's own comment states the constraint this proposal removes:

> raw Minecraft TCP, which the Cloudflare Tunnel cannot carry (a tunnel needs
> `cloudflared access` on the client side; a game client has none)

## Why the mod changes the equation

A vanilla Minecraft client has no way to speak the tunnel protocol, which is why the public
load balancer exists. **Grounds Connect runs on the client.** It can be the tunnel client
itself, which makes a tunnel-only path possible for anyone running the mod — and a
tournament already mandates a specific client build, so the mod requirement costs nothing
in that context.

## Proposed architecture

```
Minecraft client
      │ 127.0.0.1:<ephemeral>
      ▼
Grounds Connect — in-process TCP ↔ WebSocket bridge
      │ WSS, Cf-Access-Client-Id / -Secret
      ▼
Cloudflare edge  ──► Access policy denies unauthenticated connections here
      │ tunnel
      ▼
cloudflared connector (in-cluster)
      │ tcp://<workspace>-velocity.<namespace>:25565
      ▼
Velocity → backends
```

Nothing between the edge and Velocity is reachable from the public internet.

### Components

**1. Client bridge — new `gg.grounds.connect.tunnel` package**

A loopback `ServerSocket` on an ephemeral port. Each accepted connection opens one WSS
connection to the gated hostname and copies bytes in both directions as binary WebSocket
frames. That framing *is* what `cloudflared access tcp` does on the wire, so the bridge is
protocol-compatible with the connector we already run.

`java.net.http.WebSocket` covers this. Java 25 is already a hard requirement (see README),
so this adds no dependency.

**2. Credential broker — forge**

    GET /v1/servers/{name}/tunnel?projectId=…
    → 200 { hostname, clientId, clientSecret, expiresAt }
    → 403 if the caller is not on the roster for this workload

The mod already authenticates against Keycloak via the device-code flow (`Constants.java`,
client `grounds-cli`) and already holds a bearer token for `api.grounds.gg`. Authorization
stays forge's decision; Cloudflare Access only enforces the credential it is handed.

**Credentials are never shipped in the JAR.** The mod artifact is public.

**3. Infrastructure — grounds-pulumi**

Per gated workload: one tunnel ingress entry plus one `ZeroTrustAccessApplication` with
`decision: "non_identity"` and a service-token include. This exact shape already exists
three times in `grounds-pulumi/core/src/platform/cloudflared.ts` (forge DB, `mimir-push`,
`forge-blobs`), and the in-cluster consumer side is `grounds-pulumi/core/src/platform/grounds-forge.ts`
(the `vcluster-bridge-access` deployment running `cloudflared access tcp`). This proposal
adds a fourth instance of a pattern that is already load-bearing in production — not a new
mechanism.

**4. Join path — mod**

`ServerScreenActions.connect()` currently does:

```java
ConnectScreen.startConnecting(
    screen, screen.client(), ServerAddress.parseString(entry.address), entry.data, false, null);
```

For a gated entry it starts the bridge first and connects to the loopback address instead.

## The handshake-hostname problem

mc-router routes on the hostname inside the Minecraft handshake and drops what it cannot
match. A client connecting to `127.0.0.1` puts `127.0.0.1` in the handshake, and the
connection is dropped. Two ways out:

**(a) Point the tunnel ingress straight at the workspace's Velocity Service.**
mc-router leaves the gated path entirely; hostname routing is replaced by one tunnel
ingress entry per gated workload. Simple, and the extra Pulumi resources are per-event
rather than permanent. **Recommended for a first version.**

**(b) Mixin on the client's address resolver** (`ServerAddressResolver` /
`ServerNameResolver` in `net.minecraft.client.multiplayer.resolver`) so the socket goes to
loopback while the handshake keeps the original hostname.
Cleaner and keeps mc-router in the path, but couples us to internal client names — the
exact shape on MC 26.2 needs to be verified before this is chosen.

## Authorization model

1. Player authenticates to Keycloak — already implemented.
2. Forge decides whether that identity is on the roster for the gated workload.
3. Forge hands out a short-lived Access service token scoped to that one hostname.
4. Cloudflare Access enforces it at the edge, before any traffic enters our network.

Properties worth having during an event:

- **Instant kill switch.** Revoking the token or deleting the Access application removes
  the path immediately — no DNS TTL, no load-balancer reconfiguration.
- **Audit trail.** Access logs every connection attempt, including the ones it denied.
- **No standing exposure.** The path exists only while the event does.

Open trade-off: one token per gated workload is cheap but has no per-person revocation;
one token per participant gives that, at the cost of many account-scoped Cloudflare objects
to create and reap around each event.

## What this actually buys

| | Public path (today) | Gated path |
|---|---|---|
| Reachable by | anyone who resolves the name | roster members with the mod |
| First rejection happens | in Velocity, after a handshake | at the Cloudflare edge |
| Volumetric traffic lands on | UpCloud LB and the node behind it | Cloudflare |
| Origin address | public | not in DNS |
| Revocation | DNS/LB change | delete Access app, effective immediately |
| Vanilla client | works | cannot connect |

## Costs and open questions

**Latency is the item that can kill this.** An extra hop through the Cloudflare edge plus
WebSocket framing adds RTT and possibly jitter. A tournament is the least forgiving place
to spend either. This must be measured against the direct path before the design goes any
further; if the delta is not clearly acceptable, the rest of the plan is moot.

Also unresolved:

- **Bridge quality.** A naive Java copy loop will add jitter. Buffer sizing, `TCP_NODELAY`,
  and keeping the copy off the render thread all matter. (The mod's existing convention —
  all network I/O off-thread, results marshaled via `Minecraft.execute` — already applies.)
- **Cloudflare terms.** Non-HTTP traffic is officially Spectrum's domain. Tunnel + Access is
  a different posture than proxying game traffic through the CDN, but this needs a written
  answer before it is offered routinely rather than used internally.
- **Connector placement.** The connector runs on core; workspaces run on the dev spoke.
  Either the spoke gets its own connector, or the gated path reuses the existing
  core↔spoke bridge and pays a second hop. The latter is almost certainly too slow.
- **No fallback, by design.** If the tunnel is down, the event is down. Decide per event
  whether a break-glass public path stays configured but undisclosed.
- **Spectators and streamers.** Competitors will run the mod. Observers may not. Either
  they are excluded, or a separate non-gated observer entry point exists — which partly
  reopens what this closes.
- **Reconnects and transfers.** Kick/reconnect and Velocity server transfers must survive
  the bridge. Transfers stay on the same TCP session, so this is likely fine, but it is
  untested.

## Phasing

1. **Spike.** A standalone Java WebSocket↔TCP bridge against the existing `vcproxy-dev`
   Access application. Measure RTT and jitter against the direct path.
   *Gate: is the latency delta acceptable for competitive play?* Everything below depends
   on this answer.
2. **Infrastructure.** One gated hostname, one Access application, one tunnel ingress
   entry in grounds-pulumi, against a single throwaway workload.
3. **Forge.** The credential endpoint and a `gated` flag on the deployment or target.
4. **Mod.** The `tunnel` package, gated entries marked in the server list, and the join
   path switch.
5. **Trial.** One internal tournament before this is offered to anyone else.

## Not yet decided

- Whether `gated` is a property of a target, a project, or a single deployment.
- Per-workload versus per-participant tokens.
- Whether the public path stays live in parallel during a gated event.
