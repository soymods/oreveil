<div align="center">

# Oreveil

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-Server%20Plugin-FFFFFF?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.org)

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
- Rewrite cached hidden ore and salt positions in newly delivered chunks, then prime those chunks from a cached protected-ore index so exposed ores can be revealed correctly.
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

- Built for Paper-based servers targeting Minecraft `1.21.x`.
- Requires Java `21+`.
- Integrates with ProtocolLib when installed. Runtime-incompatible chunk packet rewriting disables itself and falls back to chunk priming plus block update sync.

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

Artifacts are written to `build/libs/`. Current versioned targets are:

- `oreveil-paper-1.21-<version>.jar`
- `oreveil-paper-1.20.0-1.20.4-<version>.jar`
- `oreveil-paper-1.19.x-<version>.jar`
- `oreveil-paper-1.18.x-<version>.jar`
- `oreveil-paper-1.17.x-<version>.jar`

### One-Command Dev Server

Use the dev-server script to build Oreveil, provision a disposable Paper server under `build/dev-server/`, install ProtocolLib, copy the plugin jar, accept the EULA, and start the server:

```bash
node scripts/dev-server.mjs
```

To run a specific target, set `PAPER_VERSION`; the script picks the matching Oreveil jar target automatically:

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
- `PAPER_VERSION=<version>`: run a different Paper/Minecraft version than `gradle.properties`; versions `1.16.x`, `1.17.x`, `1.18.x`, `1.19.x`, `1.20.0`-`1.20.4`, and `1.21.x` map to matching published jar targets.
- `PAPER_URL=<url>`: use a specific Paper server jar URL instead of the Paper downloads API.
- `PROTOCOLLIB_URL=<url>`: override the default ProtocolLib download URL. By default the dev script uses ProtocolLib `4.8.0` for `1.16.x`/`1.17.x` and `5.4.0` for newer targets.
- `OREVEIL_TARGET=<target>`: deploy a specific versioned artifact target instead of inferring it from `PAPER_VERSION`.
- `SERVER_PORT=<port>`: write a specific dev server port; when omitted, the script starts at `25565` and picks the next available port.
- `JAVA_BIN=<path>` or `JAVA16_HOME`/`JAVA17_HOME`/`JAVA21_HOME`: choose the Java runtime used to start Paper. The script auto-selects Java 16 for `1.16.x`/`1.17.x`, Java 17 for `1.18.x`-`1.20.4`, and Java 21 for `1.21.x` when those runtimes are discoverable.

In game, run `/oreveil` to open the admin GUI, then use Diagnostics after joining and moving around. For chunk-packet testing, confirm that chunk rewrite packet/entry counters increase and failures stay at `0`.

### Project Layout

- `src/main/java/com/soymods/oreveil/bootstrap/`: plugin bootstrap
- `src/main/java/com/soymods/oreveil/world/`: server-authoritative world model
- `src/main/java/com/soymods/oreveil/exposure/`: reveal and exposure logic
- `src/main/java/com/soymods/oreveil/obfuscation/`: packet rewrite pipeline
- `src/main/java/com/soymods/oreveil/compat/`: version-neutral compatibility contracts and adapter loading
- `src/compatModern/java/`: Paper `1.21.x` compatibility adapter included in the `paper-1.21` jar
- `src/compatCaves/java/`: shared compatibility adapter used by the `paper-1.16.x`, `paper-1.17.x`, `paper-1.18.x`, `paper-1.19.x`, and `paper-1.20.0-1.20.4` jars
- `src/main/java/com/soymods/oreveil/listener/`: world and player event synchronization
- `src/main/java/com/soymods/oreveil/ui/`: in-game admin GUI
- `src/main/resources/`: plugin metadata and configuration

## Version Information

| Component | Version |
|-----------|---------|
| Plugin Version | `0.1.0-SNAPSHOT` |
| Target Minecraft Version | `1.16.x`-`1.20.4`, `1.21.x` |
| Java | `21+` |

## Notes

- Oreveil builds separate Paper target jars. The `paper-1.21` jar uses Java 21 bytecode; the `paper-1.16.x` and `paper-1.17.x` jars use Java 16 bytecode; the `paper-1.18.x`, `paper-1.19.x`, and `paper-1.20.0-1.20.4` jars use Java 17 bytecode and share the caves-era compatibility adapter. Paper `1.16.x` must be started with Java 16 because the Paper patcher rejects Java 21.
- The `paper-1.16.x` jar falls back to pre-Caves-and-Cliffs materials where newer materials such as deepslate, copper, amethyst, and spyglass do not exist.
- ProtocolLib transport is used automatically when available, depending on `transport.mode`.
- ProtocolLib chunk packet rewriting is best-effort across `1.21.x`; if the server or ProtocolLib exposes an incompatible chunk packet shape, Oreveil disables that rewrite path for the runtime and keeps the sync fallback active.
