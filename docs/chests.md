---
layout: default
title: Loot Chests
nav_order: 7
---

# Loot Chests

Loot chests are fake chest-like blocks sent to eligible players. When the player interacts with the fake block,
ClientCore opens a virtual inventory and gives the configured rewards.

Folder:

```text
plugins/ClientCore/chests/
```

## Minimal Example

```yaml
hidden_reward_chest:
  enabled: true
  display-block: CHEST
  gui-title: "<gold>Reward Chest"
  condition: "%mmocore_level%;>=;2"
  conditions: []
  cooldowns:
    - condition: ""
      duration-ticks: 12000
  location:
    world: world
    x: 20
    y: 65
    z: 20
  drops:
    - type: vanilla
      material: GOLD_INGOT
      amount: 5
      name: "<yellow>Reward Gold"
```

## Full Root Example

```yaml
client-loot-chests:
  enabled: true
  refresh-radius: 15
  refresh-period-ticks: 40
  rules:
    rare_ender_chest:
      enabled: true
      display-block: ENDER_CHEST
      gui-title: "<dark_purple>Mysterious Chest"
      conditions:
        - "%statistic_time_played%;>=;3600"
      cooldowns:
        - condition: ""
          duration-ticks: 86400
      location:
        world: world
        x: 50
        y: 70
        z: -10
      drops:
        - type: vanilla
          material: DIAMOND
          amount: 1
```

## Global Settings

```yaml
client-loot-chests:
  enabled: true
  refresh-radius: 15
  refresh-period-ticks: 40
```

## Rule Fields

```text
enabled: true/false
display-block: CHEST, ENDER_CHEST, BARREL, etc.
gui-title: inventory title
condition: optional one-line semicolon condition
conditions: optional list of semicolon conditions
cooldowns: ordered list; first matching entry is used
location: world, x, y, z
drops: rewards
```

## Drops

Vanilla:

```yaml
drops:
  - type: vanilla
    material: GOLD_INGOT
    amount: 5
```

MMOItems:

```yaml
drops:
  - type: mmoitems
    mmo-type: MATERIAL
    mmo-id: RARE_STONE
    material: STONE
```

## Debug Checklist

- Stand within `refresh-radius`.
- Run `/clientcore reload`.
- Remove conditions while testing.
- Make sure the location world is loaded.
- Make sure the player is not on cooldown.
