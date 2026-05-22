---
layout: default
title: Mobs Configuration
nav_order: 7
---

# Client-Side Mobs (`mobs.yml`)

Spawn entities that are fully instanced to the viewing player. Great for tutorial mobs, personal boss fights, or
immersive client-sided pets.

## Example Configuration

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
