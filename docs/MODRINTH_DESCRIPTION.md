<p align="center">
  <a href="https://soymods.com/oreveil/">
    <img src="https://github.com/soymods/assets/blob/main/soymods-assets/oreveil/oreveil_render.png?raw=true" alt="Oreveil" width="75%" />
  </a>
</p>

<p align="center">
  <a href="https://papermc.io/">
    <img src="https://img.shields.io/badge/Supports-Paper%20%2F%20Folia-ffffff?style=for-the-badge" alt="Supports Paper and Folia" />
  </a>
  <a href="https://www.spigotmc.org/resources/protocollib.1997/">
    <img src="https://img.shields.io/badge/Recommended-ProtocolLib-2ea44f?style=for-the-badge" alt="ProtocolLib recommended" />
  </a>
  <a href="https://discord.gg/7nGRX2d8a6">
    <img src="https://img.shields.io/badge/Discord-Join%20server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join the Discord server" />
  </a>
</p>

<p align="center">
  <strong>Server-authoritative ore protection for x-ray and seed-aware mining.</strong>
</p>

<p align="center">
  <strong>Recommended:</strong> Install <a href="https://www.spigotmc.org/resources/protocollib.1997/">ProtocolLib</a> on Paper to enable Oreveil's packet-rewriting transport where supported. ProtocolLib is not used on Folia.
</p>

---

# What Is Oreveil?

Oreveil is a server-side ore protection plugin for Paper, with experimental Folia support, that helps defend against x-ray clients, transparent resource packs, Baritone-assisted mining, and seed-based ore prediction.

Instead of sending every client the true state of hidden underground ores, Oreveil keeps protected ore state server-authoritative and presents buried ores as natural host blocks until they are legitimately exposed through gameplay. Hidden ore can look like stone, deepslate, netherrack, tuff, or other configured host blocks depending on the dimension and ore type.

Optional server-private salted distribution can add fake ore signals that are not derived from the public world seed alone. For managed worlds on Paper, Oreveil can also remix vanilla ore placement with a private generation secret so regenerated ore locations are not predictable from the public seed.

Oreveil is distributed as one universal JAR. Paper is supported from Minecraft 1.16.x through 1.21.x and 26.x. Folia support is experimental and available for Folia versions published by PaperMC.

# Why Oreveil?

Oreveil is built for server owners who want comprehensive ore protection without sacrificing compatibility, configurability, or performance.

- Designed to reduce value from common x-ray clients and resource-pack x-ray
- Reduces public seed-based ore prediction
- Per-player ore presentation instead of global reveal state
- Legitimate exposure-based reveal rules
- Believable natural host-block replacement
- Protected raw ore blocks included by default
- ProtocolLib packet rewriting on compatible Paper runtimes
- Fallback chunk priming and block update sync when packet rewriting is unavailable
- Experimental Folia support with region-aware scheduling
- In-game administration GUI
- Live diagnostics and block inspection tools
- One universal JAR for supported Paper-family servers

# How It Works

### Server-Authoritative Protection

Oreveil tracks protected ores on the server and decides what each player is allowed to see.

- **Exposure-Gated Reveals** - Ores reveal only when adjacent exposure rules say they should, such as air, fluids, transparent blocks, or configured non-occluding neighbors.
- **Per-Player Obfuscation** - Every player can receive an independent block presentation, so one player's reveal does not automatically leak underground data to everyone else.
- **Natural Block Replacement** - Hidden ores are replaced with dimension-appropriate host blocks such as stone, deepslate, netherrack, granite, or tuff.
- **Private-Seed Protection** - Optional salted distribution and Paper managed-world remixing use server-private secrets so public seed knowledge is less useful.

### Smart Packet Transport

Oreveil automatically selects the best transport available for your server.

- On compatible Paper + ProtocolLib runtimes, Oreveil rewrites outgoing chunk and block update data before the client receives it.
- When full chunk rewriting is unavailable, Oreveil primes loaded chunks and sends synchronized block updates to keep the client view aligned.
- On Folia, ProtocolLib packet transport is disabled even if ProtocolLib is installed. Folia uses region-aware scheduling and block update sync.
- Paper 26.x uses the same runtime compatibility probe as modern 1.21.x builds and falls back automatically if full chunk rewriting is unavailable.

### Live World Synchronization

Oreveil continuously reacts to gameplay events, including:

- Mining
- Explosions
- Fluids
- Pistons
- Block placement
- Teleportation
- Respawning
- Player movement
- Chunk loading
- Natural block transformations

### Administration Tools

Everything can be managed directly in-game.

- **Administration GUI** - Configure Oreveil through `/oreveil`.
- **Protected Ore Management** - Enable or disable protected ores individually.
- **Exposure Rule Editor** - Define what counts as legitimate exposure.
- **Host Block Mapping** - Customize replacement blocks by ore or dimension.
- **Diagnostics** - Monitor transport status, chunk priming, packet rewrite counters, synthetic block sends, failures, and cache statistics.
- **Block Inspector** - See exactly how Oreveil classifies and presents any targeted block.
- **Managed Worlds** - On Paper, create, regenerate, delete, and configure Oreveil-managed worlds.

<details>
<summary><strong>See Seed Protection in Action</strong></summary>

Public world seeds can be used to predict naturally generated ore locations.

With Oreveil's optional server-private salted distribution enabled, fake ore signals make those predictions significantly less reliable while preserving normal mining.

| Seed Protection Disabled | Seed Protection Enabled |
| :---: | :---: |
| ![](https://cdn.modrinth.com/data/iioRchJL/images/edf92ff79acfff838abf821b04012add31778dd5.png) | ![](https://cdn.modrinth.com/data/iioRchJL/images/3d32ce6de1d8b6c0a840219072161304506dddd5.png) |

</details>

# Quick Start

### Requirements

- **Server:** Paper, or experimental Folia
- **ProtocolLib:** Recommended for Paper, optional, and not used on Folia
- **Java:** Matches the server version you're running

### Installation

1. Download the universal Oreveil JAR.
2. Place it inside your server's `plugins` folder.
3. For Paper servers, optionally install a compatible version of ProtocolLib.
4. Restart your server.
5. Run `/oreveil` as an operator or a player with `oreveil.admin`.
6. Use `/oreveil diagnostics` and `/oreveil inspect` to verify transport and reveal behavior.

# Supported Server Software

| Software | Status | Notes |
|---|---|---|
| Paper | Supported | Minecraft 1.16.x through 1.21.x and 26.x |
| Folia | Experimental | Uses region-aware scheduling and fallback block update sync |

# Java Requirements

| Minecraft | Java |
|---|---:|
| 1.16.x | Java 16 |
| 1.17.x | Java 16 |
| 1.18.x | Java 17+ |
| 1.19.x | Java 17+ |
| 1.20.0-1.20.4 | Java 17+ |
| 1.20.5-1.21.x | Java 21+ |
| 26.x | Java 25+ |

Oreveil is compiled with Java 16-compatible bytecode. The Java requirement comes from the corresponding server version.

# Designed to Help Protect Against

- Common x-ray clients
- Transparent x-ray resource packs
- Baritone-assisted ore hunting
- Public seed-based ore prediction
- Packet-based reveal leaks

# Current Folia Limitations

- Folia support is experimental.
- Folia does not use ProtocolLib packet transport.
- Managed-world ore remixing is disabled on Folia in the current compatibility pass.
- Folia relies on chunk priming and block update sync, not packet-level initial chunk sanitization.

# Commands

### Main Commands

- `/oreveil`
- `/oreveil status`
- `/oreveil inspect`
- `/oreveil diagnostics`
- `/oreveil reload`
- `/oreveil reset confirm`

Use `/oreveil help` or `/oreveil help <topic>` for the complete command reference.

### Examples

```text
/oreveil set live_sync_radius 96
/oreveil toggle reveal_on_exposure
/oreveil ore add DIAMOND_ORE
/oreveil ore toggle ANCIENT_DEBRIS
/oreveil exposure adjacent add WATER
/oreveil host default NORMAL STONE
/oreveil host override ANCIENT_DEBRIS NETHERRACK
/oreveil world status
```
