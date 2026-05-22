---
layout: default
title: Mobs
nav_order: 9
---

# Client-Side Mobs

Client mobs are server entities owned by one viewer. ClientCore hides them from other players, blocks other-player
damage/targeting, and hooks MythicMobs and ModelEngine where available.

Folder:

```text
plugins/ClientCore/mobs/
```

## Minimal Example

```yaml
low-level-zombie:
  enabled: true
  mythicmob-id: ZombieA
  fallback-entity: ZOMBIE
  condition: "%mmocore_level%;<=;5"
  conditions: []
  priority: 100
  health: 20.0
  damage: 2.0
```

If MythicMobs is installed and `ZombieA` exists, ClientCore spawns that MythicMob. If not, it falls back to the vanilla
entity.

## Full Root Example

```yaml
client-mobs:
  enabled: true
  max-distance: 48
  rules:
    wither-boss:
      enabled: true
      mythicmob-id: toro_wither_still
      fallback-entity: ARMOR_STAND
      condition: ""
      conditions:
        - "%mmocore_level%;>=;10"
      priority: 500
      health: 1000.0
      damage: 0.0
```

## Rule Fields

```text
enabled: true/false
mythicmob-id: MythicMobs internal mob ID
fallback-entity: Bukkit EntityType if MythicMobs is missing
condition: optional one-line semicolon condition
conditions: optional list of semicolon conditions
priority: higher wins when auto-picking a rule
health: vanilla stat override
damage: vanilla stat override
variants: optional weighted variants
```

## Variants

```yaml
undead-pack:
  enabled: true
  priority: 50
  fallback-entity: ZOMBIE
  variants:
    - id: zombie
      mythicmob-id: ZombieA
      fallback-entity: ZOMBIE
      weight: 80
      health: 20
      damage: 2
    - id: husk
      mythicmob-id: HuskA
      fallback-entity: HUSK
      weight: 20
      rare: true
      luck-multiplier: 1.0
      health: 30
      damage: 4
```

## Spawn Commands

Auto-pick by configured conditions:

```text
/clientcore mobspawn 1
```

Spawn a MythicMob directly for yourself or another viewer:

```text
/clientcore mythicspawn self toro_wither_still
/clientcore mythicspawn Steve toro_wither_still 1 1
```

## Fixed Spawn Points

Create a spawn at your location:

```text
/clientcore setspawn mine_zombies
```

Configure it:

```text
/clientcore spawnattr mine_zombies rule low-level-zombie
/clientcore spawnattr mine_zombies amount 3
/clientcore spawnattr mine_zombies radius 8
/clientcore spawnattr mine_zombies batch-size 2
/clientcore spawnattr mine_zombies interval-ticks 100
/clientcore spawnattr mine_zombies max-alive 6
/clientcore spawnattr mine_zombies activation-range 48
/clientcore spawnattr mine_zombies enabled true
```

## ModelEngine Notes

ModelEngine render entities are mapped to the mob owner through ModelEngine API events. This is meant to hide model
parts from non-owner players. MythicMobs skills still run server-side, so skills that target all nearby players should
be written carefully if you want purely personal boss mechanics.

## Debug Checklist

- Run `/clientcore status` and check `Mobs:` is above `0`.
- Use `/clientcore mythicspawn self <id>` to bypass ClientCore conditions.
- Check MythicMobs logs if a MythicMob ID does not spawn.
- For ModelEngine mobs, verify the ModelEngine plugin version matches the API dependency.
