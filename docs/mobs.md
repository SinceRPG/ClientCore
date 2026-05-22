---
layout: default
title: Mobs Configuration
nav_order: 7
---

# Client-Side Mobs (`mobs/mobs.yml`)

Spawn entities that are fully instanced to the viewing player. Great for tutorial mobs, personal boss fights, or
immersive client-sided pets.

## Example Configuration

All `.yml` files inside `mobs/` are loaded and merged. A file can use the full `client-mobs:` root, a `rules:` root,
or direct rule IDs such as `boss_a:` and `boss_b:`.

```yaml
rules:
  endgame-boss:
    enabled: true
    mythicmob-id: WitherKing # Requires MythicMobs hook
    fallback-entity: WITHER
    conditions:
      - "%mmocore_level% >= 50"
    priority: 200
    health: 1000.0
    damage: 50.0
```

## Priority

If multiple mobs could potentially spawn at the same internal placeholder or spawnpoint, the one with the highest
`priority` is chosen.
