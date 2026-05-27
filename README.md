# ClientCore

ClientCore is a Paper and Folia plugin for player-specific client-side gameplay. It uses PacketEvents to send per-player
block changes and entity visibility updates, while the server keeps authoritative state for rewards, cooldowns, mobs,
loot, and persistence.

## Features

- Client-side block regeneration with ready and cooldown block states.
- Server-side custom mining for client-side blocks, including tool requirements, MMOItems tools, per-tool drops, and
  TextDisplay progress bars.
- Optional custom mining for real vanilla blocks through PacketEvents crack animations, with per-tool timings and
  per-tool custom drops.
- Live client-side build capture with `/packetmode`, `/packetsave`, and `/packetapply`.
- Player-specific loot chests that open virtual inventories.
- Player-specific ground drops with cooldowns and protected pickup logic.
- Client-owned mobs for tutorials, personal bosses, and instanced encounters.
- Conditional NPC visibility through Citizens or FancyNpcs providers.
- Luck weighting for rare block and mob variants.
- PlaceholderAPI, WorldGuard, MMOItems, MythicMobs, ModelEngine, Citizens, and FancyNpcs hooks.
- Folia-aware scheduling for player, entity, region, global, and storage work.

## Installation

1. Build or download `ClientCore-<version>-all.jar`.
2. Place the jar in your server `plugins/` folder.
3. Start the server once to generate `plugins/ClientCore/`.
4. Edit `config.yml`, `messages.yml`, and the feature folders.
5. Run `/clientcore reload`.

PacketEvents is bundled into the shaded ClientCore jar. You do not need to install PacketEvents separately. If a server
already has a compatible PacketEvents plugin, ClientCore will use the existing API instead of creating its own instance.

## Configuration

Every feature ships with example rules under `src/main/resources`:

- `config.yml` controls storage, luck, debug logging, and optional hooks.
- `blocks/blocks.yml` contains block regeneration examples, vanilla mining examples, variants, drops, and conditions.
- `chests/chests.yml` contains virtual chest examples, cooldown tiers, and GUI loot.
- `drops/drops.yml` contains protected client-side ground drop examples.
- `mobs/mobs.yml` contains client-owned mob and MythicMobs fallback examples.
- `npcs/npcs.yml` contains Citizens and FancyNpcs visibility examples.
- `builds/builds.yml` contains client-side build examples, visibility conditions, and generated build settings.
- `messages.yml` contains all user-facing command and console text.

All feature folders support multiple YAML files, nested folders, full roots such as `block-regen.rules`, a plain `rules`
root, or direct rule IDs.

## Commands

- `/clientcore reload` reloads all configuration files.
- `/clientcore status` shows loaded rule counts.
- `/clientcore refresh` resends client-side block state around the sender.
- `/clientcore build mode <on|off>` captures real block edits into a client-side build session.
- `/clientcore build save <name>` saves the captured build and restores the real world.
- `/clientcore build apply <name> <player> <on|off>` applies or removes a saved build for one player.
- Builds can also use `auto-apply: true` so every player who passes the build conditions sees it automatically.
- Legacy aliases `/packetmode`, `/packetsave`, and `/packetapply` are also registered.
- `/clientcore visibility <toggle|hide|show>` controls player visibility.
- `/clientcore mobspawn <amount>` spawns matching client-owned mobs near the sender.
- `/clientcore mythicspawn <viewer> <mob> [level] [amount]` spawns a MythicMob for one viewer.
- `/clientcore setspawn <id>` creates a persistent mob spawn point.
- `/clientcore delspawn <id>` deletes a persistent mob spawn point.
- `/clientcore spawns` lists persistent spawn points.
- `/clientcore spawnattr <id> <attribute> <value>` edits a spawn point.
- `/clientcore debug <on|off>` toggles debug logging.
- `/clientcore luck ...` manages luck profiles and luck items.

## Documentation

The wiki is in the `docs/` folder and can be published with the included GitHub Pages workflow.

- [Wiki Home](docs/index.md)
- [Configuration Layout](docs/configuration-layout.md)
- [Main Configuration](docs/config.md)
- [Conditions](docs/conditions.md)
- [Commands](docs/commands.md)
- [Blocks](docs/blocks.md)
- [Folia Compatibility](docs/folia-compatibility.md)
- [Changelog](CHANGELOG.md)
- [Chests](docs/chests.md)
- [Drops](docs/drops.md)
- [Mobs](docs/mobs.md)
- [NPCs](docs/npcs.md)
- [Troubleshooting](docs/troubleshooting.md)

## Development

Build with:

```bash
./gradlew clean build
```

The shaded output is written to `build/libs/` and includes PacketEvents plus ClientCore runtime dependencies.
