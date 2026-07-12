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
  <strong>Server-side ore protection for x-ray and seed-aware mining.</strong>
</p>

<p align="center">
  <strong>Recommended:</strong> Install <a href="https://www.spigotmc.org/resources/protocollib.1997/">ProtocolLib</a> on Paper for the strongest packet transport where supported. ProtocolLib is not used on Folia.
</p>

---

# What Is Oreveil?

Oreveil is a server-side ore protection plugin for Paper, with experimental Folia support. It helps reduce the value of x-ray clients, transparent resource packs, Baritone-assisted mining, and public seed-based ore prediction.

Instead of showing clients the true state of every hidden ore, Oreveil presents buried protected ores as natural blocks until normal gameplay exposes them. Hidden ores can appear as stone, deepslate, netherrack, tuff, granite, or other configured host blocks.

Oreveil ships as one universal JAR. Paper is supported from Minecraft 1.16.x through 1.21.x and 26.x. Folia support is experimental and should be smoke tested on your server version.

# Main Features

- Per-player ore hiding
- Exposure-based ore reveals
- Natural host block replacement
- Protected raw ore blocks included by default
- Optional private-seed fake ore signals
- Optional managed-world ore remixing on Paper
- ProtocolLib support on compatible Paper servers
- Fallback sync when packet rewriting is unavailable
- Experimental Folia support
- In-game admin GUI
- Block inspection and live diagnostics
- One universal JAR for supported Paper-family servers

# How It Works

- **Hidden until exposed** - Buried protected ores stay disguised until they become legitimately visible.
- **Per-player view** - Oreveil decides what each player should see instead of globally revealing underground ore.
- **Believable replacement** - Hidden ore is replaced with host blocks that fit the surrounding dimension.
- **Seed protection** - Optional salted distribution and Paper managed worlds make public seeds less useful for finding ores.
- **Compatibility fallback** - If packet rewriting is unavailable, Oreveil keeps protecting ores with chunk priming and block updates.

<details>
<summary><strong>See Seed Protection in Action</strong></summary>

Public world seeds can be used to predict naturally generated ore locations.

With Oreveil's optional private salted distribution enabled, fake ore signals make those predictions less reliable while preserving normal mining.

| Seed Protection Disabled | Seed Protection Enabled |
| :---: | :---: |
| ![](https://cdn.modrinth.com/data/iioRchJL/images/edf92ff79acfff838abf821b04012add31778dd5.png) | ![](https://cdn.modrinth.com/data/iioRchJL/images/3d32ce6de1d8b6c0a840219072161304506dddd5.png) |

</details>

# Quick Start

1. Download the universal Oreveil JAR.
2. Place it inside your server's `plugins` folder.
3. For Paper servers, optionally install a compatible version of ProtocolLib.
4. Restart your server.
5. Run `/oreveil` as an operator or a player with `oreveil.admin`.
6. Use `/oreveil diagnostics` and `/oreveil inspect` to verify behavior.

# Compatibility

| Item | Status |
|---|---|
| Paper | Supported for Minecraft 1.16.x through 1.21.x and 26.x |
| Folia | Experimental support using fallback sync |
| ProtocolLib | Recommended on Paper, optional, and not used on Folia |

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

# Commands

- `/oreveil`
- `/oreveil status`
- `/oreveil inspect`
- `/oreveil diagnostics`
- `/oreveil reload`
- `/oreveil reset confirm`

Use `/oreveil help` or `/oreveil help <topic>` for the complete command reference.

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

<details>
<summary><strong>Advanced Notes</strong></summary>

- Oreveil uses ProtocolLib packet rewriting on compatible Paper runtimes.
- If packet rewriting is unavailable, Oreveil automatically uses its fallback sync path.
- Folia support is experimental and uses fallback sync.
- Managed-world ore remixing is currently Paper-only.
- Oreveil is compiled for Java 16 compatibility, but your server's Java requirement depends on the Minecraft version.

</details>
