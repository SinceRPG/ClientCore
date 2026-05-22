---
layout: default
title: Ground Drops
nav_order: 8
---

# Ground Drops

Ground drops are player-specific physical item entities. Other players should not see or pick them up.

Folder:

```text
plugins/ClientCore/drops/
```

## Minimal Example

```yaml
rare_tutorial_stone:
  enabled: true
  condition: "%mmocore_level%;>=;1"
  conditions: []
  cooldowns:
    - condition: ""
      duration-ticks: 6000
  location:
    world: world
    x: 15.5
    y: 65.0
    z: 15.5
  item:
    material: DIAMOND
    amount: 1
    name: "<aqua>Rare Tutorial Diamond"
    lore:
      - "<gray>Only visible to you."
```

## Full Root Example

```yaml
client-drops:
  enabled: true
  refresh-period-ticks: 100
  rules:
    daily_reward_token:
      enabled: true
      cooldowns:
        - condition: ""
          duration-ticks: 1728000
      location:
        world: hub
        x: 0.5
        y: 100.0
        z: 0.5
      item:
        material: SUNFLOWER
        amount: 3
        name: "<gold>Daily Token"
```

## Rule Fields

```text
enabled: true/false
condition: optional one-line semicolon condition
conditions: optional list of semicolon conditions
cooldowns: ordered list; first matching entry is used
location: world, x, y, z
item: item shown and awarded
```

## Cooldowns

Cooldown entries are checked top-to-bottom.

```yaml
cooldowns:
  - condition: "%luckperms_has_permission_vip%;==;true"
    duration-ticks: 1200
  - condition: ""
    duration-ticks: 6000
```

The blank condition is the fallback for everyone.

## Item Fields

Common fields:

```yaml
item:
  material: DIAMOND
  amount: 1
  name: "<aqua>Gem"
  lore:
    - "<gray>Only visible to you."
```

## Debug Checklist

- Make sure the player is near the configured location.
- Temporarily set cooldown duration low while testing.
- Remove conditions to isolate PlaceholderAPI issues.
- Check whether the player already has an active cooldown record.
