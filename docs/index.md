---
layout: default
title: Welcome
nav_order: 1
---

# ClientCore Wiki

ClientCore is a Paper/Folia plugin for player-specific client-side gameplay: fake blocks, personal loot chests, personal
ground drops, client-owned mobs, and client-only NPCs.

The important idea is that most features are real server logic with client-specific visibility. For example, a block
regen node is sent as a fake block packet to one player, while another player may see normal world terrain. Client mobs
are still server entities, but ClientCore hides and protects them so only their owner can see and interact with them.

## Requirements

- Paper/Folia matching the plugin build target.
- PacketEvents installed as a separate plugin.
- Java version supported by your server build.
- SQLite or MySQL JDBC driver available on the server classpath if your selected database needs it.

Optional integrations:

- PlaceholderAPI for conditions.
- WorldGuard for block regen region flags.
- MMOItems for configured item rewards.
- MythicMobs for client-owned mobs and MythicMob NPCs.
- ModelEngine for MythicMobs models.
- Citizens or FancyNpcs for NPC providers.

## First Setup

1. Put `ClientCore-1.0.jar` in `plugins/`.
2. Put PacketEvents in `plugins/`.
3. Add any optional hook plugins you use.
4. Start the server once.
5. Edit files under `plugins/ClientCore/`.
6. Run `/clientcore reload`.
7. Use `/clientcore status` to confirm loaded block and mob rule counts.

## Main Topics

- [Configuration Layout](configuration-layout.md)
- [Main Configuration](config.md)
- [Conditions](conditions.md)
- [Commands](commands.md)
- [Blocks Regeneration](blocks.md)
- [Loot Chests](chests.md)
- [Ground Drops](drops.md)
- [Mobs](mobs.md)
- [NPCs](npcs.md)
- [Messages](messages.md)
- [Troubleshooting](troubleshooting.md)

## Fast Folder Example

All feature folders support multiple YAML files. This is valid:

```text
plugins/ClientCore/
  config.yml
  messages.yml
  blocks/
    coal.yml
    ores/deepslate.yml
  mobs/
    tutorial.yml
    bosses/wither.yml
  drops/
    tutorial.yml
```

Inside each feature file you can use either the full root, a `rules:` root, or direct rule IDs. See
[Configuration Layout](configuration-layout.md) for the exact rules.
