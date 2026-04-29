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
- This is a transport boundary, not the final packet rewrite implementation. Full chunk-packet obfuscation should plug in behind the same `ObfuscationTransport` interface.

## ProtocolLib Path

- When ProtocolLib is present and `transport.mode` resolves to `AUTO` or `PROTOCOLLIB`, Oreveil now rewrites outbound `BLOCK_CHANGE` and `MULTI_BLOCK_CHANGE` packets per player.
- By default, outbound chunk packets are not structurally rewritten. Oreveil primes the affected chunk for that player immediately after the chunk packet is sent so hidden ores are corrected to their host blocks as soon as possible.
- `obfuscation.experimental-chunk-rewrite` exists for development testing, but it should be treated as unstable until validated across real worlds and packet edge cases.
