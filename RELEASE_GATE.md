# Oreveil Release Gate

This gate defines the minimum bar for promoting a build from beta to full release.

## 1. Automated Gate (must pass)

- `./gradlew test`
- `./gradlew build`
- CI workflow is green on `main`, when available.
- One non-sources plugin jar exists in `build/libs/`.
- Generated `plugin.yml` contains the finalized release version, not a placeholder or `SNAPSHOT`.

## 2. Manual Smoke Gate (must pass)

Run this on Paper `1.21.4` with ProtocolLib installed.

Checklist:
- Start a clean Paper server with Oreveil and ProtocolLib.
- Join with a Minecraft `1.21.4` client.
- Confirm Oreveil enables without stack traces.
- Move through newly loaded chunks and confirm no packet rewrite errors are logged.
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
- Artifact naming follows `oreveil-<pluginVersion>.jar`.
- Modrinth license is set to the custom Oreveil License or All Rights Reserved.

## 4. Optional But Recommended Before Major Release

- Run a smoke test with ProtocolLib absent to verify fallback transport behavior.
- Run a smoke test with salted distribution enabled and a non-zero private salt.
- Profile chunk load and movement around high-density cave systems.
- Spot-check a fresh config reset with `/oreveil reset confirm`.
