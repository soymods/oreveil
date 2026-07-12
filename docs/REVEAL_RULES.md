# Reveal Rules

Oreveil's visibility model is intentionally direct:

- A block is eligible for obfuscation only if its material is listed in `protected-ores`.
- A protected ore is revealed only when one of its six cardinal neighbors satisfies an exposure rule.
- Exposure rules are driven by `exposure.reveal-adjacent-materials`, `exposure.reveal-transparent-materials`, and the `obfuscation.reveal-next-to-non-occluding-blocks` toggle.
- If a protected ore is not revealed, the outgoing client-visible block should be replaced by a host block chosen from `host-blocks.ore-overrides` or the current dimension default.

This rule set keeps reveal semantics explicit and config-driven while leaving room for later event-driven updates and packet interception.

## Current Live Sync Path

- World changes that can affect exposure now trigger targeted resends for nearby players.
- The default transport mode is `AUTO`, which uses ProtocolLib when available and falls back to `Player#sendBlockChange` live correction.
- Loaded chunks are indexed once for protected ore positions, so join/teleport/reload priming can avoid rescanning every block in the chunk.
- On Folia, Oreveil always uses region-aware chunk priming and block update sync; ProtocolLib packet transport is disabled.
- Block placement, breaks, explosions, piston movement, fluid flow, falling-block changes, and natural block transformations refresh the cached protected-ore index.
- `/oreveil diagnostics` reports rewrite counters, chunk prime counters, synthetic block sends, ProtocolLib wrapper failures, and cache sizes.
- ProtocolLib chunk rewriting uses the cached protected-ore and salt index to rewrite outgoing chunk block-state data before send when runtime block-state IDs and the chunk buffer format are compatible.
- Post-send chunk priming still runs after chunk delivery so legitimately exposed ores can be revealed according to the per-player exposure rules.

## ProtocolLib Path

- When ProtocolLib is present and `transport.mode` resolves to `AUTO` or `PROTOCOLLIB`, Oreveil rewrites outbound `BLOCK_CHANGE` and `MULTI_BLOCK_CHANGE` packets per player.
- Outbound chunk packets are rewritten directly for cached hidden ore/salt positions when the runtime supports that packet format, then followed by a targeted prime pass for that player.
- If the server runtime cannot expose block-state IDs or the chunk buffer format changes, Oreveil leaves that chunk packet untouched, records a diagnostics failure, and still uses post-send chunk priming.
- On Paper `26.x`, Oreveil attempts the same ProtocolLib chunk rewrite path as modern `1.21.x` builds. If ProtocolLib does not expose a compatible chunk buffer, Oreveil disables chunk rewriting for that runtime and keeps chunk priming plus block update sync active.
- On Folia, Oreveil does not use ProtocolLib transport even if ProtocolLib is installed. Folia compatibility relies on the fallback sync path and region scheduler.

## Salted Distribution

- Salted distribution uses `world-model.salt-secret` in addition to the world seed and chunk coordinates.
- Server owners should set `salt-secret` to a private random long before enabling `world-model.salted-distribution`.
- Changing the secret changes fake ore placement and forces clients to be resynced on reload.

## Managed World Generation

- Managed-world ore remixing uses `world-generation.secret` in addition to the world seed, chunk coordinates, and mutation pass name.
- Server owners should set `world-generation.secret` to a private random long before enabling `world-generation.enabled`.
- Public seed knowledge is not enough to predict Oreveil's managed-world ore remix layer when the generation secret is private.
- Oreveil writes a persistent generation-pass marker to each managed chunk after mutation, so already-processed chunks are not remixed again on later loads or reloads.
- Managed-world generation is disabled on Folia in the current compatibility pass.
