# Oreveil Release Gate

This gate defines the minimum bar for promoting a build from beta to full release.

## 1. Automated Gate (must pass)

- `./gradlew test`
- `./gradlew build`
- CI workflow is green on `main`, when available.
- One non-sources plugin jar exists in `build/libs/`.
- Generated `plugin.yml` contains the finalized release version, not a placeholder or `SNAPSHOT`.

## 2. Manual Smoke Gate (must pass)

Run this on each released target jar with ProtocolLib installed. At minimum, smoke test Paper `1.18.2`, the oldest supported `1.21` runtime, and the newest available `1.21.x` runtime for the release.

Checklist:
- Start a clean Paper server with Oreveil and ProtocolLib.
- Join with a Minecraft client matching the Paper version under test.
- Confirm Oreveil enables without stack traces.
- Move through newly loaded chunks and confirm either chunk packet rewrite counters increase without failures or Oreveil logs a single chunk rewrite disablement and continues using the fallback path.
- Mine into buried ore and confirm adjacent ore reveals only when legitimately exposed.
- Trigger common block updates, including block break/place, fluids, explosions, pistons, teleport, respawn, and leaf decay.
- Run `/oreveil`, `/oreveil status`, `/oreveil diagnostics`, `/oreveil inspect`, and `/oreveil reload`.
- Confirm no hard client desync, server crash, or recurring console error.

## 3. Release Hygiene (must pass)

- `README.md` compatibility notes match the current release intent.
- Plugin version in `gradle.properties` is finalized for the upload.
- Release notes include:
  - User-visible changes
  - Known limitations
  - Supported Minecraft and Paper versions
  - ProtocolLib compatibility status
- Artifact naming follows `oreveil-<target>-<pluginVersion>.jar`, for example `oreveil-paper-1.21-<pluginVersion>.jar`.
- Modrinth license is set to the custom Oreveil License or All Rights Reserved.

## 4. Optional But Recommended Before Major Release

- Run a smoke test with ProtocolLib absent to verify fallback transport behavior.
- Run a smoke test with salted distribution enabled and a non-zero private salt.
- Profile chunk load and movement around high-density cave systems.
- Spot-check a fresh config reset with `/oreveil reset confirm`.
