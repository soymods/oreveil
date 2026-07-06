# Reveal Rules

Oreveil's visibility model is intentionally direct:

- A block is eligible for obfuscation only if its material is listed in `protected-ores`.
- A protected ore is revealed only when one of its six cardinal neighbors satisfies an exposure rule.
- Exposure rules are driven by `exposure.reveal-adjacent-materials`, `exposure.reveal-transparent-materials`, and the `obfuscation.reveal-next-to-non-occluding-blocks` toggle.
- If a protected ore is not revealed, the outgoing client-visible block should be replaced by a host block chosen from `host-blocks.ore-overrides` or the current dimension default.

This rule set keeps reveal semantics explicit and config-driven while leaving room for later event-driven updates and packet interception.

## Current Live Sync Path

- World changes that can affect exposure now trigger targeted resends for nearby players.
- The current transport mode is `BLOCK_UPDATE_SYNC`, which uses `Player#sendBlockChange` for live correction.
- Loaded chunks are indexed once for protected ore positions, so join/teleport/reload priming can avoid rescanning every block in the chunk.
- Block placement, breaks, explosions, piston movement, fluid flow, falling-block changes, and natural block transformations refresh the cached protected-ore index.
- `/oreveil diagnostics` reports rewrite counters, chunk prime counters, synthetic block sends, ProtocolLib wrapper failures, and cache sizes.
- ProtocolLib chunk rewriting uses the cached protected-ore and salt index to rewrite outgoing chunk block-state data before send when runtime block-state IDs are available.
- Post-send chunk priming still runs after chunk delivery so legitimately exposed ores can be revealed according to the per-player exposure rules.

## ProtocolLib Path

- When ProtocolLib is present and `transport.mode` resolves to `AUTO` or `PROTOCOLLIB`, Oreveil rewrites outbound `BLOCK_CHANGE` and `MULTI_BLOCK_CHANGE` packets per player.
- Outbound chunk packets are rewritten directly for cached hidden ore/salt positions, then followed by a targeted prime pass for that player.
- If the server runtime cannot expose block-state IDs or the chunk buffer format changes, Oreveil leaves that chunk packet untouched, records a diagnostics failure, and still uses post-send chunk priming.

## Salted Distribution

- Salted distribution uses `world-model.salt-secret` in addition to the world seed and chunk coordinates.
- Server owners should set `salt-secret` to a private random long before enabling `world-model.salted-distribution`.
- Changing the secret changes fake ore placement and forces clients to be resynced on reload.
