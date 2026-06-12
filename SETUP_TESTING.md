# ClientCore setup and testing notes

## Server requirements

- Paper/Folia API target: `26.1.2` only.
- Required plugin: `PacketEvents` `2.12.1`.
- Optional hooks: `PlaceholderAPI`, `WorldGuard`, `MMOItems`, `SinceEnchantments`, `MythicMobs`.
- Build command: `.\gradlew.bat build`.
- Output jar: `build/libs/ClientCore-1.0-all.jar`.

## Basic install

1. Put `ClientCore-1.0-all.jar` in `plugins/`.
2. Put `PacketEvents` in `plugins/`.
3. Add optional hooks if you need them.
4. Start server once so `plugins/ClientCore/config.yml` and the SQL tables can be created.
5. Edit `config.yml`, then run `/clientcore reload`.

## SQL storage

`config.yml` has a `storage` section. Default is SQLite:

```yaml
storage:
  type: sqlite
```

For MySQL, set `storage.type: mysql` and fill host, port, database, username and password. Luck/top data is stored in
SQL through HikariCP.
Client mob spawn points are also stored in SQL. If an old `spawns.yml` exists and the SQL spawn table is empty,
ClientCore migrates those spawn points into SQL on startup.

The plugin bundles HikariCP only. JDBC drivers are intentionally not shaded to keep `ClientCore-1.0-all.jar` small.
Provide the selected driver on the server/plugin classpath:

- SQLite: `org.xerial:sqlite-jdbc`
- MySQL: `com.mysql:mysql-connector-j`

## Block regen test

1. Install PlaceholderAPI and an MMOCore placeholder provider if you want `%mmocore_level%`.
2. In `config.yml`, check a rule under `block-regen.rules`.
3. Stand near `STONE` or `DEEPSLATE`.
4. Run `/clientcore refresh`.
5. Break a matching block.

Expected result:

- Real block is not removed server-side.
- Only the breaking player sees the fake air/regenerated block.
- Regen block update is sent through PacketEvents.
- A `BlockDisplay` grow animation is shown only to that player.
- Drop is given directly to that player.

## WorldGuard flag test

ClientCore registers a WorldGuard state flag named `clientcore-regen` during plugin load. Set it on a region:

```text
/rg flag <region> clientcore-regen allow
```

If the flag is missing, ClientCore allows the rule so setup mistakes do not hard-lock mining during testing.

## MMOItems drop test

Use this drop section in a block rule:

```yaml
drops:
  - type: mmoitems
    mmo-type: MATERIAL
    mmo-id: RARE_STONE
    material: STONE
```

ClientCore calls the MMOItems API directly. If MMOItems cannot return the item, it falls back to the vanilla `material`
entry.

## MythicMobs client mob test

1. Create MythicMobs mobs named `ZombieA` and `ZombieB`, or edit `client-mobs.rules.*.mythicmob-id`.
2. Run `/clientcore mobspawn 1`.

Expected result:

- The mob is spawned via MythicMobs API if available.
- Non-owner players receive destroy packets through PacketEvents and the entity is also hidden server-side.
- Damage/targeting is cancelled unless the player is the mob owner.

## Fixed spawn commands

Use these commands in-game:

```text
/clientcore setspawn mine_zombies
/clientcore spawnattr mine_zombies rule low-level-zombie
/clientcore spawnattr mine_zombies amount 3
/clientcore spawnattr mine_zombies radius 8
/clientcore spawnattr mine_zombies batch-size 2
/clientcore spawnattr mine_zombies interval-ticks 100
/clientcore spawnattr mine_zombies max-alive 6
/clientcore spawnattr mine_zombies level 1
/clientcore spawnattr mine_zombies activation-range 48
/clientcore spawns
/clientcore delspawn mine_zombies
```

Spawn data is cached in memory for fast Folia ticks and saved to SQL when edited. The mob spawn loop never queries SQL
every tick.

## Luck commands

Player commands:

```text
/clientcore luck profile
/clientcore luck top
/clientcore luck t
```

Admin commands:

```text
/clientcore luck set <player> <amount>
/clientcore luck add <player> <amount>
/clientcore luck remove <player> <amount>
/clientcore luck reset <player>
/clientcore luck top-exclude <player> <true|false>
/clientcore luck giveitem <player> <luck> <amount>
```

Luck affects weighted variants marked `rare: true`. The config key `luck.max-rare-weight-bonus-percent` caps how much
rare weights can be boosted.
Luck top ignores players with `luck <= 0`.
Luck items are configured under `luck.item` and support the same vanilla ItemMeta config builder as normal drops.

## Conditions and weighted variants

Single conditions use semicolon syntax:

```yaml
condition: "%mmocore_level%;>=;2"
```

Multi-condition format:

```yaml
conditions:
  - "%mmocore_level%;>=;2"
  - "%mmocore_level%;<=;5"
  - "%some_optional_placeholder%;==;yes;optional"
  - "vip_bonus;%luckperms_has_permission_clientcore.vip%;==;yes;optional"
```

Required lines must pass. Lines ending with `optional` are evaluated but do not block the rule if they fail.
Optional lines can start with an id. Variants can require those ids with:

```yaml
required-condition-ids: [ vip_bonus ]
```

Block and mob rules can use weighted `variants`. A common example is `weight: 90` and a rare option with `weight: 10`,
`rare: true`, and `luck-multiplier: 1.0`.

## GUI editor

Open the in-game editor:

```text
/clientcore editor
```

Current GUI pages:

- Main menu: block rules, mob rules, spawn points, reload.
- Block rules: paginated rule list, rule detail, full Minecraft block selector for display/source blocks, source block
  removal with shift-right, condition list editor, and weighted variant editor.
- Mob rules: paginated rule list, MythicMobs selector populated from the MythicMobs API, fallback entity selector,
  health/damage controls, condition list editor, and weighted variant editor.
- Spawn list: open every fixed spawn point.
- Spawn detail: toggle enabled and adjust amount, batch size, radius, interval, max alive and activation range.
- Drop editor: add/remove drops, choose every Minecraft item material, choose MMOItems type and item from the MMOItems
  API, and edit common ItemMeta fields such as unbreakable, hide tooltip, glint override, glider, fire resistance,
  custom model data, max stack size, rarity and enchantable.

For long free text such as conditions, item display names and lore, use commands or YAML. The GUI handles the safe
structured choices and numeric/toggle fields.

## Visibility command

Hide/show the executing player from every other online player:

```text
/clientcore visibility toggle
/clientcore visibility hide
/clientcore visibility show
```

This is separate from client mobs. It tracks only UUIDs and cleans up on quit.

Spawn attributes:

- `rule`: rule id or MythicMobs id; empty means auto-pick by condition.
- `amount`: maximum spawn attempts per tick cycle.
- `radius`: random spawn radius around the saved location.
- `batch-size`: how many mobs can spawn at once per player.
- `interval-ticks`: spawn interval.
- `max-alive`: max alive mobs from this spawn for each player.
- `level`: MythicMobs level passed to the MythicMobs API.
- `activation-range`: player range needed for spawning.
- `enabled`: `true` or `false`.

## Notes

- Reflection is intentionally not used. PlaceholderAPI, WorldGuard, MMOItems, MythicMobs, and PacketEvents are called
  through their APIs.
- PacketEvents is used for fake block changes and destroy packets. The current grow animation still uses Paper
  `BlockDisplay` entities because display entity metadata is version-sensitive and much easier to keep stable on Folia
  through Paper.
- For fully packet-only mobs, the next step is a dedicated PacketEvents virtual entity tracker with movement, metadata,
  hit detection, and Mythic skill proxying. MythicMobs itself is server-side, so "all MythicMobs skills fully
  packet-only" requires re-implementing or intercepting major parts of its runtime.
