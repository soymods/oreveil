<div align="center">

# Oreveil

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-Server%20Plugin-FFFFFF?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.org)

Deterministic server-side ore obfuscation and seed-resilient world integrity for Minecraft servers.

Created by `soymods`.

</div>

## What Oreveil Is

Oreveil is a server-side Minecraft plugin that prevents x-ray and seed-assisted resource discovery by ensuring unrevealed ore data is never sent to the client in the first place.

Instead of trusting the client to ignore hidden blocks, Oreveil keeps the server authoritative and rewrites chunk or block data before it reaches players. Hidden ores are replaced with contextually valid host blocks such as stone, deepslate, or netherrack until normal gameplay legitimately exposes them.

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
- Support salted or non-vanilla ore placement strategies that do not derive from the public world seed.
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

- Rewrite outbound block update traffic on a per-player basis.
- Support ProtocolLib-backed transport when available, with a fallback sync path for compatibility.
- Preserve a clean transport boundary for deeper packet integrations.
- Keep direct initial chunk rewrite behind an explicit experimental toggle.

## Threat Model

Oreveil is intended to neutralize or materially weaken:

- classic x-ray clients
- resource pack transparency exploits
- Baritone-assisted ore hunting strategies
- seed-cracking workflows that rely on predictable underground distribution
- packet leakage from naive server-side reveal implementations

The goal is to reduce adversarial clients back to standard survival constraints rather than trying to detect every cheat directly.

## Administration

Oreveil is designed to be manageable both from configuration files and in-game administration commands.

- Use config-driven policies for protected ores, exposure rules, host block mapping, and transport behavior.
- Use `/oreveil reload` to apply rule changes without restarting the server.
- Use `/oreveil inspect` to inspect how a targeted block is currently classified and presented to the client.

## Compatibility

- Built for Paper-based servers targeting Minecraft `1.21.4`.
- Requires Java `21+`.
- Integrates with ProtocolLib when installed.

## Development

### Build From Source

```bash
git clone https://github.com/soymods/oreveil.git
cd oreveil
./gradlew build
```

Artifacts are written to `build/libs/`.

### Project Layout

- `src/main/java/com/soymods/oreveil/bootstrap/`: plugin bootstrap
- `src/main/java/com/soymods/oreveil/world/`: server-authoritative world model
- `src/main/java/com/soymods/oreveil/exposure/`: reveal and exposure logic
- `src/main/java/com/soymods/oreveil/obfuscation/`: packet rewrite pipeline
- `src/main/java/com/soymods/oreveil/listener/`: world and player event synchronization
- `src/main/resources/`: plugin metadata and configuration

## Version Information

| Component | Version |
|-----------|---------|
| Plugin Version | `0.1.0-SNAPSHOT` |
| Target Minecraft Version | `1.21.4` |
| Java | `21+` |

## Notes

- Oreveil targets Paper API `1.21.4-R0.1-SNAPSHOT`.
- ProtocolLib transport is used automatically when available, depending on `transport.mode`.
