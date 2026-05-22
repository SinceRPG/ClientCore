---
layout: default
title: Blocks Regeneration
nav_order: 6
---

# Blocks Regeneration

Block regen creates fake, player-specific resource nodes. The real world block does not need to change. ClientCore sends
block packets to eligible players, handles the break packet, gives rewards, then sends a cooldown block and later the
ready block again.

Folder:

```text
plugins/ClientCore/blocks/
```

## Minimal Example

`plugins/ClientCore/blocks/coal.yml`

```yaml
level-2-coal:
  enabled: true
  location:
    world: world
    x: 233.423
    y: -34
    z: 257.638
  ready-block: COAL_ORE
  cooldown-block: BEDROCK
  condition: ""
  conditions:
    - "%mmocore_level%;>=;2"
    - "%mmocore_level%;<=;5"
  regen-ticks: 80
  drops:
    - type: vanilla
      material: COAL
      amount: 1
```

Coordinates can be decimal. Minecraft block packets target the block position containing that coordinate.

## Full Root Example

```yaml
block-regen:
  enabled: true
  refresh-radius: 16
  refresh-period-ticks: 40
  join-delay-ticks: 5
  default-worldguard-flag: clientcore-regen
  rules:
    tutorial-stone:
      enabled: true
      location:
        world: world
        x: 10
        y: 65
        z: 10
      ready-block: STONE
      cooldown-block: BEDROCK
      regen-ticks: 100
      drops:
        - type: vanilla
          material: COBBLESTONE
          amount: 1
```

## Global Settings

These are read from the merged `block-regen` root:

```yaml
block-regen:
  enabled: true
  refresh-radius: 10
  refresh-period-ticks: 40
  join-delay-ticks: 5
  default-worldguard-flag: clientcore-regen
```

If you use direct rule style only, these settings fall back to defaults.

## Rule Fields

```text
enabled: true/false
location.world: Bukkit world name
location.x/y/z: block location
ready-block: material sent when the node is available
cooldown-block: material sent after mining
condition: optional one-line semicolon condition
conditions: optional list of semicolon conditions
worldguard-flag: overrides the default flag for this rule
regen-ticks: cooldown duration
drops: rewards given directly to the player
variants: optional weighted variants
```

Use `ORIGINAL` for `ready-block` or `cooldown-block` when you want to send the real server block state.

## MMOItems Reward

```yaml
drops:
  - type: mmoitems
    mmo-type: MATERIAL
    mmo-id: RARE_STONE
    material: STONE
```

If MMOItems cannot return the item, ClientCore falls back to the vanilla `material`.

## Variants

```yaml
rich-coal:
  enabled: true
  location:
    world: world
    x: 30
    y: 60
    z: 30
  ready-block: COAL_ORE
  cooldown-block: BEDROCK
  regen-ticks: 100
  drops:
    - type: vanilla
      material: COAL
      amount: 1
  variants:
    - id: normal
      weight: 90
      ready-block: COAL_ORE
    - id: rare
      weight: 10
      rare: true
      luck-multiplier: 1.0
      ready-block: DIAMOND_ORE
      drops:
        - type: vanilla
          material: DIAMOND
          amount: 1
```

## Debug Checklist

- Run `/clientcore status` and check `Blocks:` is above `0`.
- Run `/clientcore refresh` near the configured location.
- Temporarily set `condition: ""` and `conditions: []`.
- Confirm the world name exactly matches `/mv info` or Bukkit world folder name.
- Confirm PlaceholderAPI expansion returns a number for placeholders such as `%mmocore_level%`.
- If WorldGuard is installed, allow the configured flag or leave the rule flag unset while testing.
