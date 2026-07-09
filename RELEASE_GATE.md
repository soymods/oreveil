# Oreveil Release Gate

This gate defines the minimum evidence required before publishing a release build. Do not treat a build as release-ready until every required row has an owner, a date, and either a pass result or an explicit release-blocking failure.

## 1. Automated Gate (must pass)

| Check | Required evidence |
|---|---|
| Unit tests | `./gradlew test -q` exits `0`; attach or paste the final test summary. |
| Release build | `./gradlew build -q` exits `0`. |
| Artifact | `build/libs/oreveil-<pluginVersion>.jar` exists and is the jar being uploaded. |
| Version | `gradle.properties` `plugin_version` matches README, release notes, and uploaded artifact name. |
| Metadata | Generated `plugin.yml` contains the finalized release version, not `SNAPSHOT` or a placeholder. |
| CI | CI workflow is green on `main`, when CI is configured. |

Record:

- Date:
- Commit:
- Plugin version:
- Java used for build:
- Artifact path:
- Artifact SHA-256:

## 2. Compatibility Smoke Gate (must pass for claimed support)

Run the universal jar with ProtocolLib installed on each supported boundary. At minimum, smoke test Paper `1.16.5`, `1.17.1`, `1.18.2`, `1.19.4`, `1.20.4`, `1.20.6`, a representative `1.21.x` runtime, and `26.2`.

For each target, record:

| Field | Value |
|---|---|
| Paper version | |
| Minecraft client version | |
| Java runtime | |
| ProtocolLib version | |
| Oreveil artifact | |
| Transport mode selected | |
| Chunk packets rewritten | |
| Chunk block entries rewritten | |
| Chunk rewrite failures | |
| Chunk packets primed | |
| Prime corrections sent | |
| Console errors/warnings | |
| Pass/fail | |

Checklist for each target:

- Start a clean Paper server with Oreveil and ProtocolLib.
- Join with a Minecraft client matching the Paper version under test.
- Confirm Oreveil enables without stack traces.
- Move through newly loaded chunks and confirm one of:
  - chunk packet rewrite counters increase and rewrite failures stay at `0`; or
  - Oreveil logs a single chunk rewrite disablement and continues using fallback chunk priming plus block update sync.
- Mine into buried ore and confirm adjacent ore reveals only when legitimately exposed.
- Trigger common block updates: block break/place, fluids, explosions, pistons, teleport, respawn, and leaf decay.
- Run `/oreveil`, `/oreveil status`, `/oreveil diagnostics`, `/oreveil inspect`, and `/oreveil reload`.
- Confirm no hard client desync, server crash, or recurring console error.

## 3. Fallback Gate (must pass)

Run at least one current supported Paper target with ProtocolLib absent or `transport.mode=SYNC`.

Required evidence:

- Oreveil enables and logs the fallback transport path.
- `/oreveil diagnostics` shows chunk priming/block sync activity after movement or chunk load.
- Mining and exposure reveal still behave correctly.
- Release notes state that fallback transport does not rewrite full chunk payloads.

## 4. Seed-Resilience Gate (must pass when advertised)

Run this gate when release notes or marketing mention seed resilience, salted distribution, or managed-world ore remixing.

- Enable salted distribution with a private non-zero `world-model.salt-secret`.
- Enable managed-world generation with a private non-zero `world-generation.secret`.
- Generate fresh chunks in two worlds with the same public seed but different private secrets.
- Confirm protected ore/salt placement differs between private secrets.
- Confirm regenerated chunks are marked with the current generation pass and are not repeatedly remutated.
- Confirm release notes warn that zero/default secrets are not seed-resilient.

## 5. Bypass and Abuse Smoke Gate (recommended before public promotion)

Run the scenarios in [`docs/BYPASS_AND_PERFORMANCE_TEST_PLAN.md`](docs/BYPASS_AND_PERFORMANCE_TEST_PLAN.md) and record pass/fail notes for:

- x-ray resource pack or client
- seed-known pathing
- Baritone-style branch mining/pathing
- rapid chunk travel
- explosions and piston movement
- teleport, death/respawn, dimension changes
- ProtocolLib present and absent

## 6. Release Hygiene (must pass)

- `README.md` compatibility and known-limitations notes match the release behavior.
- Release notes include:
  - user-visible changes
  - known limitations
  - supported Minecraft and Paper versions
  - ProtocolLib compatibility status
  - fallback transport behavior
- Artifact naming follows `oreveil-<pluginVersion>.jar`.
- Modrinth license is set to the custom Oreveil License or All Rights Reserved.
- Any unsupported version is removed from badges, README, release notes, and upload metadata before publishing.

## 7. Optional But Recommended Before Major Release

- Profile chunk load and movement around high-density cave systems.
- Spot-check a fresh config reset with `/oreveil reset confirm`.
- Test with at least two player accounts in the same chunk to catch per-player reveal leaks.
- Test common server settings: view distance changes, simulation distance changes, restart/rejoin, and world unload/reload.
