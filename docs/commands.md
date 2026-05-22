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

Show loaded rule counts:

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
