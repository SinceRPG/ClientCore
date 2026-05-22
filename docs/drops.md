---
layout: default
title: Drops Configuration
---

# Ground Drops (`drops.yml`)

Spawn client-sided dropped items on the ground. Only the intended player can see or pick up these items.

## Example Configuration

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
