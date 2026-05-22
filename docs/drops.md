---
layout: default
title: Drops Configuration
nav_order: 5
---

# Ground Drops (`drops/drops.yml`)

Spawn client-sided dropped items on the ground. Only the intended player can see or pick up these items.

## Example Configuration

All `.yml` files inside `drops/` are loaded and merged. A file can use the full `client-drops:` root, a `rules:` root,
or direct rule IDs.

```yaml
rules:
  daily_reward_token:
    enabled: true
    conditions: [ ]
    cooldowns:
      - condition: ""
        duration-ticks: 1728000 # 24 hours
    location:
      world: hub
      x: 0.5
      y: 100.0
      z: 0.5
    item:
      material: SUNFLOWER
      amount: 3
      name: "<gold>Daily Token"
      lore:
        - "<yellow>Claim your daily reward!"
```
