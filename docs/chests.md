---
layout: default
title: Chests Configuration
nav_order: 4
---

# Loot Chests (`chests/chests.yml`)

Create client-sided chests that players can interact with to receive items.

## How it works

A fake chest block is rendered for eligible players. Right-clicking it opens a custom inventory GUI containing loot.
After looting, a cooldown is applied to that specific player.

## Example Configuration

All `.yml` files inside `chests/` are loaded and merged. A file can use the full `client-loot-chests:` root, a `rules:`
root, or direct rule IDs.

```yaml
rules:
  hidden_reward_chest:
    enabled: true
    display-block: CHEST
    gui-title: "<gold>Reward Chest"
    conditions:
      - "%mmocore_level% >= 2"
    cooldowns:
      - condition: "%luckperms_has_permission_vip% == true"
        duration-ticks: 3000
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
```

## Available Settings

- **display-block:** CHEST, BARREL, ENDER_CHEST, etc.
- **cooldowns:** A priority list of cooldowns. Top-to-bottom evaluation. The first matching condition determines the
  duration.
