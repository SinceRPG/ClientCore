<div align="center">

# ClientCore

**The Ultimate Client-Side Instancing Plugin for Paper & Folia**

[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Folia-blue)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-1.21+-success)](https://papermc.io/)

</div>

---

## 📖 Overview

**ClientCore** is a powerful Minecraft plugin designed specifically for modern servers running **Paper** or **Folia**.
It allows server administrators to create highly immersive, personalized, and instanced experiences for their players.

By manipulating packets under the hood, ClientCore sends "fake" blocks, entities, drops, and interactables directly to
individual clients. This means you can create personalized resource nodes, unique player-bound loot chests, hidden quest
NPCs, or instanced boss fights—all happening in the same world, but visible only to the specific players you choose
based on powerful condition systems.

## ✨ Key Features

- ⛏️ **Client-Side Block Regeneration:** Create personal, instanced ores or resource nodes (e.g., a diamond ore that
  only VIPs can see and mine).
- 🧰 **Client-Side Loot Chests:** Spawn virtual chests with rich GUI menus. Perfect for daily rewards or hidden
  level-restricted loot.
- 💎 **Client-Side Item Drops:** Drop physical items on the ground that belong exclusively to the viewer. Other players
  won't even know it's there.
- 🧟 **Client-Side Mobs:** Spawn entities (Vanilla or custom MythicMobs) that are fully instanced. Great for personal
  tutorial mobs or instanced storyline bosses.
- 🗣️ **Client-Side NPCs:** Generate localized guides and quest givers using Vanilla, Citizens, MythicMobs, or FancyNpcs.
- 🍀 **Luck System:** Integrated luck mechanics to boost a player's chance of getting "rare" drops or better mob
  variants.
- ⚡ **Highly Optimized:** Built ground-up for extreme performance, supporting the latest multi-threaded **Folia**
  architectures alongside Paper.

## 🚀 Installation

1. Download the latest `ClientCore-xxx.jar` release.
2. **[REQUIRED]** Download and install the [PacketEvents plugin](https://modrinth.com/plugin/packetevents) into your
   `plugins/` directory. **ClientCore will not work without it.**
3. Place the ClientCore jar file into your server's `plugins/` directory.
4. Start or restart your server to generate the configuration files.
5. Customize the setups in the `plugins/ClientCore/` directory (see the Configuration section below).
6. Use `/clientcore reload` in-game or from the console to apply changes.

> **Note:** ClientCore also requires an SQL database to store player data (SQLite is used by default and works out of
> the box).

## ⚙️ Configuration & Wiki

ClientCore provides extremely extensive and flexible configurations. Every single feature supports conditional checks (
via PlaceholderAPI) and complex tiered cooldowns.

We have set up a complete and comprehensive Wiki generated via GitHub Pages. Please refer to our documentation to master
the plugin:

📚 **[View the Official ClientCore Wiki Here](https://SinceRPG.github.io/ClientCore/)**

### Quick Links to Specific Docs:

- [Main Config (`config.yml`)](https://SinceRPG.github.io/ClientCore/config.html)
- [Configuration Layout](https://SinceRPG.github.io/ClientCore/configuration-layout.html)
- [Conditions](https://SinceRPG.github.io/ClientCore/conditions.html)
- [Commands](https://SinceRPG.github.io/ClientCore/commands.html)
- [Block Regen Rules](https://SinceRPG.github.io/ClientCore/blocks.html)
- [Loot Chests](https://SinceRPG.github.io/ClientCore/chests.html)
- [Ground Drops](https://SinceRPG.github.io/ClientCore/drops.html)
- [Mob Spawns](https://SinceRPG.github.io/ClientCore/mobs.html)
- [NPC Spawns](https://SinceRPG.github.io/ClientCore/npcs.html)

## 🔌 Supported Integrations & Hooks

ClientCore truly shines when hooked into your server's broader ecosystem. We natively support:

- **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI):** Absolutely required for the conditional
  engine (e.g., checking `%mmocore_level%;>=;5`).
- **[WorldGuard](https://dev.bukkit.org/projects/worldguard):** Introduces custom flags like `clientcore-regen` to
  protect specific regions.
- **[MythicMobs](https://mythiccraft.io/):** Use your custom MythicMobs as client-sided bosses or NPCs!
- **[MMOItems](https://mythiccraft.io/):** Drop heavily customized MMOItems via loot chests, block regen, or ground
  drops.
- **[Citizens](https://citizensnpcs.co/) & [FancyNpcs](https://modrinth.com/plugin/fancynpcs):** Use these advanced NPC
  systems to render your client-sided guides.

## 💻 Commands & Permissions

### Base Command

- `/clientcore` (Alias: `/ccore`)

### User Commands

- `/clientcore status` — View active loaded rules and spawns around you.
- `/clientcore hide` — Hide yourself from other players (visibility toggle).
- `/clientcore show` — Unhide yourself.

### Admin Commands (Requires `clientcore.admin`)

- `/clientcore reload` — Safely reload all YAML configurations and caches.
- `/clientcore refresh` — Force an immediate re-send of packet blocks around you.
- `/clientcore spawnmob <ruleId> [amount]` — Force-spawn a specific client mob from the config.
- `/clientcore luck <player> set/add/remove <amount>` — Manage a player's internal Luck stats.
- `/clientcore luck giveitem <player> <luckValue> <amount>` — Give a consumable Luck Token.
- `/clientcore setspawn <spawnId> <radius> <ruleId> [amount]` — Set a persistent spawn point in the world for mobs.
- `/clientcore listspawns` — View all configured spawn points.
- `/clientcore deletespawn <spawnId>` — Remove a specific spawn point.
- `/clientcore updatespawn <spawnId> <attribute> <value>` — Live edit a spawn point's radius, amount, or rule.
- `/clientcore debug` — Toggle detailed console debugging.

## 🐛 Bug Reports & 💡 Feature Requests

If you encounter a bug or have a brilliant idea for a new feature, please let us know! We use GitHub's issue tracker.

- **[Report a Bug](../../issues/new?template=bug_report.yml)**
- **[Request a Feature](../../issues/new?template=feature_request.yml)**

Please ensure you provide as much detail as possible, including your exact server version and the plugin version.
