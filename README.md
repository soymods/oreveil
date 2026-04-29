<div align="center">

# Oreveil

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-Server%20Plugin-FFFFFF?style=for-the-badge&logo=papermc&logoColor=black)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![Status](https://img.shields.io/badge/Status-Scaffold%20Phase-4C8EDA?style=for-the-badge)](#development)

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

## Threat Model

Oreveil is intended to neutralize or materially weaken:

- classic x-ray clients
- resource pack transparency exploits
- Baritone-assisted ore hunting strategies
- seed-cracking workflows that rely on predictable underground distribution
- packet leakage from naive server-side reveal implementations

The goal is to reduce adversarial clients back to standard survival constraints rather than trying to detect every cheat directly.

## Project Status

This repository is currently in scaffold phase.

The current codebase includes:

- a Gradle-based Paper plugin setup
- plugin metadata and build workflow wiring
- placeholder services for authoritative world state, exposure control, and packet obfuscation
- initial configuration and documentation structure

## Planned Architecture

- `AuthoritativeWorldModel`: owns hidden ore state and future persistence/distribution hooks
- `ExposureService`: decides when a block is legitimately visible
- `NetworkObfuscationService`: handles packet rewrite entrypoints and per-player visible state

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
- `src/main/resources/`: plugin metadata and configuration

## Version Information

| Component | Version |
|-----------|---------|
| Plugin Version | `0.1.0-SNAPSHOT` |
| Target Minecraft Version | `1.21.4` |
| Java | `21+` |

## Notes

- This scaffold currently targets Paper API `1.21.4-R0.1-SNAPSHOT`.
- Packet interception internals are not implemented yet.
- The current classes are intentionally minimal and exist to establish the plugin boundaries cleanly.
