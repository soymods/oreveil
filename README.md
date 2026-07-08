<div align="center">

# Oreveil

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.x--1.21.x%20%7C%2026.x-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-Server%20Plugin-FFFFFF?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-Universal%20Jar-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.org)

Deterministic server-side ore obfuscation and seed-resilient world integrity for Minecraft servers.

Created by `soymods`.

</div>

## What Oreveil Is

Oreveil is a server-side Minecraft plugin that prevents x-ray and seed-assisted resource discovery by ensuring unrevealed ore data is never sent to the client in the first place.

Instead of trusting the client to ignore hidden blocks, Oreveil keeps the server authoritative and rewrites block updates before they reach players. Hidden ores are replaced with contextually valid host blocks such as stone, deepslate, or netherrack until normal gameplay legitimately exposes them. With ProtocolLib, cached hidden ore and salt positions are also rewritten in outgoing chunk data before delivery when the server runtime exposes compatible block-state IDs.

## Core Model

### Packet-Level Obfuscation

- Intercept outgoing chunk and block update traffic.
- Rewrite hidden ore states per-player before transmission.
- Prevent transient packet leakage that traditional x-ray tools depend on.

### Exposure-Gated Reveal Rules

- Reveal ore only when it becomes legitimately exposed.
- Avoid proximity-based disclosure and similar side channels.
- Preserve vanilla-consistent behavior for ordinary mining and exploration.

### Seed-Resilient Authority

- Maintain a server-authoritative record of protected ore positions.
- Support salted or non-vanilla ore placement strategies using a server-private salt so fake ore signals do not derive from the public world seed alone.
- Reduce the value of seed-cracking pipelines and pathing heuristics.

## Feature Overview

### Per-Player Obfuscation

- Replace hidden ores with believable host blocks on a per-player basis.
- Keep legitimate exposed ores visible without globally revealing underground data.
- Support different host material behavior across the Overworld, Nether, and End.

### Configurable Reveal Logic

- Define exactly which ore blocks are protected.
- Control what counts as exposure, including air, fluids, transparent blocks, and non-occluding neighbors.
- Reload rules in-game without forcing server owners to hand-edit config for every change.

### Live World Synchronization

- React to mining, explosions, fluids, pistons, placement, teleportation, and player movement.
- Keep the client view aligned with current reveal state as the world changes.
- Prime newly viewed areas so players do not gain value from stale underground information.

### Packet-Aware Transport

- Rewrite outbound single-block and multi-block update traffic on a per-player basis.
- Support ProtocolLib-backed transport when available, with a fallback sync path for compatibility.
- Rewrite cached hidden ore and salt positions in newly delivered chunks when the runtime exposes a compatible chunk format, then prime those chunks from a cached protected-ore index so exposed ores can be revealed correctly.
- Preserve a clean transport boundary for deeper packet integrations.

## Threat Model

Oreveil is intended to neutralize or materially weaken:

- classic x-ray clients
- resource pack transparency exploits
- Baritone-assisted ore hunting strategies
- seed-cracking workflows that rely on predictable underground distribution
- packet leakage from naive server-side reveal implementations

The goal is to reduce adversarial clients back to standard survival constraints rather than trying to detect every cheat directly.

## Administration

Oreveil is designed to be manageable from config files, an in-game admin GUI, and advanced commands.

- Use `/oreveil` to open the admin GUI. Console users can use `/oreveil help`.
- Use the GUI for the common setup flow: runtime toggles, protected ores, exposure rules, host blocks, xray profile, sync settings, diagnostics, and managed-world actions.
- Use commands for precise edits, diagnostics, console administration, and workflows that are easier to script.
- Use `/oreveil help <topic>` for focused command examples. Topics include `settings`, `exposure`, `host`, `ores`, `profile`, `world`, and `diagnostics`.
- Use `/oreveil inspect` to inspect how a targeted block is currently classified and presented to the client.
- Use `/oreveil diagnostics` to inspect packet rewrite counters, chunk priming counters, and cached ore/salt index sizes.
- Use `/oreveil reset confirm` to restore bundled defaults. Oreveil backs up the current `config.yml` first.

### Admin GUI

Run `/oreveil` in game as an operator or a player with `oreveil.admin`.

The GUI includes:

- runtime controls for obfuscation, reveal behavior, salted distribution, world generation, and transport mode
- protected ore toggles
- xray profile selection
- sync radius controls with plus/minus buttons and exact sign input
- exposure material selectors with paging
- dimension host block defaults and per-ore host overrides
- diagnostics refresh
- managed-world controls with confirmation screens for create, regenerate, delete, and default-world changes
- config reload

### Advanced Commands

Compact status, inspection, and diagnostics:

```mcfunction
/oreveil status
/oreveil inspect
/oreveil diagnostics
/oreveil reload
/oreveil reset confirm
```

Editable scalar settings:

```mcfunction
/oreveil
/oreveil settings
/oreveil settings controls
/oreveil get live_sync_radius
/oreveil explain salt_density
/oreveil set live_sync_radius 96
/oreveil toggle reveal_on_exposure
```

Protected ores:

```mcfunction
/oreveil
/oreveil ores
/oreveil ore add DIAMOND_ORE
/oreveil ore remove COPPER_ORE
/oreveil ore toggle ANCIENT_DEBRIS
```

Exposure rules:

```mcfunction
/oreveil
/oreveil exposure
/oreveil exposure adjacent add WATER
/oreveil exposure adjacent remove LAVA
/oreveil exposure transparent toggle GLASS
```

Host block mapping:

```mcfunction
/oreveil
/oreveil host
/oreveil host default NORMAL STONE
/oreveil host default NETHER NETHERRACK
/oreveil host override ANCIENT_DEBRIS NETHERRACK
/oreveil host override DIAMOND_ORE clear
```

Managed world tools:

```mcfunction
/oreveil
/oreveil world status
/oreveil world target oreveil
/oreveil world seed random
/oreveil world create
/oreveil world regenerate confirm
```

## Compatibility

- Distributed as one universal Paper plugin jar for Minecraft `1.16.x` through `1.21.x` and `26.x`.
- Selects its server compatibility adapter at runtime; the artifact itself uses Java 16-compatible bytecode.
- The server's required Java runtime still depends on its Paper version: Java 16 for `1.16.x`/`1.17.x`, Java 17+ for `1.18.x`-`1.20.4`, Java 21+ for `1.20.5`-`1.21.x`, and Java 25+ for `26.x`.
- Integrates with ProtocolLib when installed. Runtime-incompatible chunk packet rewriting disables itself and falls back to chunk priming plus block update sync; `26.x` currently uses that fallback path for full chunk delivery.

## Release Readiness

Use [`RELEASE_GATE.md`](RELEASE_GATE.md) before promoting a build.

## License

This project is distributed under the custom **Oreveil License (All Rights Reserved)** in [`LICENSE.txt`](LICENSE.txt).

In short:

- Redistribution, modification, or re-uploading is not allowed without explicit written permission.
- Videos featuring the plugin are allowed, including monetized videos.
- Modpack and server-pack inclusion is allowed under the limits described in the license.
- The plugin is provided as-is without warranty.

## Development

### Build From Source

```bash
git clone https://github.com/soymods/oreveil.git
cd oreveil
./gradlew build
```

The universal artifact is written to `build/libs/oreveil-<version>.jar`.

### One-Command Dev Server

Use the dev-server script to build Oreveil, provision a disposable Paper server under `build/dev-server/`, install ProtocolLib, copy the plugin jar, accept the EULA, and start the server:

```bash
node scripts/dev-server.mjs
```

To run a specific server version, set `PAPER_VERSION`; the script deploys the same universal Oreveil jar:

```bash
PAPER_VERSION=1.18.2 node scripts/dev-server.mjs
PAPER_VERSION=1.21.4 node scripts/dev-server.mjs
```

If `25565` is already in use, the script automatically picks the next available port and prints the join address. Use `SERVER_PORT` to pin a specific port:

```bash
PAPER_VERSION=1.18.2 SERVER_PORT=25566 node scripts/dev-server.mjs
```

Then join the printed `localhost:<port>` from a client matching the Paper version under test. To auto-op your offline-mode test player on first setup:

```bash
MC_USERNAME=YourName node scripts/dev-server.mjs
```

For edit/test loops, use watch mode. It rebuilds, redeploys, stops the current dev server, and starts it again when source files change:

```bash
node scripts/dev-server.mjs --watch
```

Useful options:

- `--reset-world`: delete the disposable `world`, `world_nether`, and `world_the_end` folders before starting.
- `--no-build`: skip Gradle build and deploy the existing `build/libs/` jar. Run `./gradlew build -q` first if you need fresh code in the jar.
- `--prepare-only`: download/provision the dev server and deploy the jar, then exit without starting Paper.
- `PAPER_VERSION=<version>`: run a different Paper/Minecraft version than `gradle.properties`; supported versions are `1.16.x` through `1.21.x` and `26.x`.
- `PAPER_URL=<url>`: use a specific Paper server jar URL instead of the Paper downloads API.
- `PROTOCOLLIB_URL=<url>`: override the default ProtocolLib download URL. By default the dev script uses ProtocolLib `4.8.0` for `1.16.x`/`1.17.x` and `5.4.0` for newer targets.
- `SERVER_PORT=<port>`: write a specific dev server port; when omitted, the script starts at `25565` and picks the next available port.
- `JAVA_BIN=<path>` or `JAVA16_HOME`/`JAVA17_HOME`/`JAVA21_HOME`/`JAVA25_HOME`: choose the Java runtime used to start Paper. The script requires exact Java 16 for `1.16.x`/`1.17.x`, Java 17 or newer for `1.18.x`-`1.20.4`, Java 21 or newer for `1.20.5+`, and Java 25 or newer for `26.x`.

In game, run `/oreveil` to open the admin GUI, then use Diagnostics after joining and moving around. For chunk-packet testing, confirm that chunk rewrite packet/entry counters increase and failures stay at `0`, or that Oreveil logs one chunk rewrite disablement and continues using chunk priming on runtimes where full chunk rewriting is disabled.

### Project Layout

- `src/main/java/com/soymods/oreveil/bootstrap/`: plugin bootstrap
- `src/main/java/com/soymods/oreveil/world/`: server-authoritative world model
- `src/main/java/com/soymods/oreveil/exposure/`: reveal and exposure logic
- `src/main/java/com/soymods/oreveil/obfuscation/`: packet rewrite pipeline
- `src/main/java/com/soymods/oreveil/compat/`: version-neutral compatibility contracts and adapter loading
- `src/compatModern/java/`: runtime adapter selected for `1.21.x` and `26.x`
- `src/compatCaves/java/`: runtime adapter selected for `1.16.x` through `1.20.x`
- `src/main/java/com/soymods/oreveil/listener/`: world and player event synchronization
- `src/main/java/com/soymods/oreveil/ui/`: in-game admin GUI
- `src/main/resources/`: plugin metadata and configuration

## Version Information

| Component | Version |
|-----------|---------|
| Plugin Version | `0.1.1` |
| Target Minecraft Version | `1.16.x`-`1.21.x`, `26.x` |
| Plugin bytecode | Java 16 |

## Notes

- Oreveil builds one Java 16-compatible jar. Each Paper server still requires its own supported Java runtime; Paper `1.16.x` must be started with Java 16 because its patcher rejects Java 21.
- On `1.16.x`, Oreveil falls back to pre-Caves-and-Cliffs materials where newer materials such as deepslate, copper, amethyst, and spyglass do not exist.
- ProtocolLib transport is used automatically when available, depending on `transport.mode`.
- ProtocolLib chunk packet rewriting is best-effort. If the server or ProtocolLib exposes an incompatible chunk packet shape, Oreveil disables that rewrite path for the runtime and keeps the sync fallback active. On `26.x`, the modern adapter disables full chunk packet rewriting by default and uses chunk priming plus block update sync.
