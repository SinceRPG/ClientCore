---
layout: default
title: Commands
nav_order: 5
---

# Commands

Base command:

```text
/clientcore
/ccore
```

Most admin commands require `clientcore.admin`. Luck profile/top use `clientcore.luck`.

## Admin

Reload configuration:

```text
/clientcore reload
```

Show loaded block regen, vanilla mining, farming, mob rule counts, MMOItems/SinceEnchantments status, and custom block hook status:

```text
/clientcore status
```

Refresh client-side blocks around yourself:

```text
/clientcore refresh
```

Toggle debug:

```text
/clientcore debug on
/clientcore debug off
```

When enabled, mining and farming logs include the matched tool rule, whether the tool match was vanilla or MMOItems,
configured enchant requirement levels, SinceEnchantments hook state, and whether drops came from the tool, the block, or
the natural break fallback.

## Client Builds

Capture a temporary build session:

```text
/clientcore build mode on
/packetmode on
```

While capture mode is enabled, placed blocks are recorded and existing block breaks are shown as client-side air to the
builder. Saving writes the layout to `client-builds.yml`, restores the real world blocks, and turns capture mode off.

```text
/clientcore build save tutorial_bridge
/packetsave tutorial_bridge
```

Apply or remove the saved build for a single online player:

```text
/clientcore build apply tutorial_bridge Steve on
/clientcore build apply tutorial_bridge Steve off
/packetapply tutorial_bridge Steve on
/packetapply tutorial_bridge Steve off
```

Applied builds persist by player UUID in `client-build-players.yml` and are resent after join. The visual block changes
are sent with PacketEvents.

## Visibility

```text
/clientcore visibility toggle
/clientcore visibility hide
/clientcore visibility show
```

This hides or shows the executing player from other players. It is separate from client-side mobs.

## Client Mobs

Spawn mobs using configured mob rules:

```text
/clientcore mobspawn <amount>
```

Spawn a MythicMob directly without a ClientCore mob rule:

```text
/clientcore mythicspawn <viewer|self> <mythicMobId> [level] [amount]
```

Examples:

```text
/clientcore mythicspawn self toro_wither_still
/clientcore mythicspawn Steve toro_wither 5 2
```

All arguments have tab completion: online players, `self`, MythicMob IDs, levels, and amounts.

## Fixed Mob Spawn Points

Create a spawn at your location:

```text
/clientcore setspawn mine_zombies
```

List spawn points:

```text
/clientcore spawns
```

Delete a spawn:

```text
/clientcore delspawn mine_zombies
```

Edit attributes:

```text
/clientcore spawnattr mine_zombies rule low-level-zombie
/clientcore spawnattr mine_zombies amount 3
/clientcore spawnattr mine_zombies radius 8
/clientcore spawnattr mine_zombies batch-size 2
/clientcore spawnattr mine_zombies interval-ticks 100
/clientcore spawnattr mine_zombies max-alive 6
/clientcore spawnattr mine_zombies level 1
/clientcore spawnattr mine_zombies activation-range 48
/clientcore spawnattr mine_zombies enabled true
```

## Luck

```text
/clientcore luck profile
/clientcore luck top
/clientcore luck set <player> <amount>
/clientcore luck add <player> <amount>
/clientcore luck remove <player> <amount>
/clientcore luck reset <player>
/clientcore luck top-exclude <player> <true|false>
/clientcore luck giveitem <player> <luck> <amount>
```
