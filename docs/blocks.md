---
layout: default
title: Blocks Configuration
nav_order: 3
---

# Blocks Regeneration (`blocks/blocks.yml`)

This module allows you to send fake block updates to clients, effectively creating client-sided ores or resource nodes.

## How it works

1. The real block on the server remains `AIR`.
2. The client is sent a `ready-block` (e.g., `STONE`).
3. When the player breaks the block, they receive items and the block turns into a `cooldown-block` (e.g., `BEDROCK`).
4. After `regen-ticks`, it turns back into the `ready-block`.

## Example Configuration

All `.yml` files inside `blocks/` are loaded and merged. A file can use the full `block-regen:` root, a `rules:` root,
or direct rule IDs such as `ore_a:` and `ore_b:`.

```yaml
rules:
  vip-diamond-ore:
    enabled: true
    location:
      world: world
      x: 15
      y: 60
      z: 20
    ready-block: DIAMOND_ORE
    cooldown-block: DEEPSLATE
    conditions:
      - "%luckperms_has_permission_vip% == true"
    regen-ticks: 1200
    drops:
      - type: vanilla
        material: DIAMOND
        amount: 2
```

## Available Settings

- **ready-block:** The visual block shown when ready to mine.
- **cooldown-block:** The visual block shown while regenerating.
- **conditions:** A list of PlaceholderAPI conditions that must all be true.
- **regen-ticks:** Time until the block regenerates (20 ticks = 1s).
