---
layout: default
title: Blocks Regeneration
nav_order: 6
---

# Blocks

Block regen creates fake, player-specific resource nodes. The real world block does not need to change. ClientCore sends
block packets to eligible players, handles the break packet, gives rewards, then sends a cooldown block and later the
ready block again.

Vanilla mining rules are different: they apply to real vanilla blocks already placed in the world. ClientCore cancels the
normal break, runs a server-side mining timer, sends PacketEvents crack animation packets, and breaks the real block when
the timer completes.

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

## Vanilla Mining

Use `vanilla-mining` when you want custom mining speed and rewards for real vanilla blocks such as `STONE`,
`DIAMOND_ORE`, or `DEEPSLATE`. It does not change the client's built-in mining speed table; it replaces the break flow
with server-authoritative timing.

```yaml
vanilla-mining:
  enabled: true
  # Leave blank to allow configured vanilla mining anywhere.
  # Set a WorldGuard flag name if the rule should only work in allowed regions.
  default-worldguard-flag: ""
  blocks:
    STONE:
      enabled: true
      condition: ""
      conditions: []
      mining:
        default-time-ticks: 60
        tools:
          - item:
              type: vanilla
              material: WOODEN_PICKAXE
            time-ticks: 80
            drops:
              - type: vanilla
                material: COBBLESTONE
                amount: 1
          - item:
              type: mmoitems
              mmo-type: TOOL
              mmo-id: STARTER_PICKAXE
            time-ticks: 40
            drops:
              - type: mmoitems
                mmo-type: MATERIAL
                mmo-id: STARTER_STONE
                material: COBBLESTONE
```

Vanilla mining supports the same `mining.feedback`, `tools`, vanilla item matching, MMOItems tool matching, conditions,
and MMOItems rewards as client-side block regen mining.

Tool-specific drops are preferred:

- If the matching tool has `drops`, those drops are given.
- If the matching tool has no `drops`, ClientCore uses the block-level `drops`.
- If neither the tool nor the block has `drops`, ClientCore breaks the block naturally with the player's held item.

This lets each tool have a different reward table without defining a shared block drop:

```yaml
vanilla-mining:
  enabled: true
  blocks:
    DIAMOND_ORE:
      mining:
        default-time-ticks: 120
        tools:
          - item:
              type: vanilla
              material: IRON_PICKAXE
            time-ticks: 140
            drops:
              - type: vanilla
                material: DIAMOND
                amount: 1
          - item:
              type: vanilla
              material: DIAMOND_PICKAXE
            time-ticks: 90
            drops:
              - type: vanilla
                material: DIAMOND
                amount: 2
```

Vanilla mining is intentionally disabled by default because it affects every matching real block in the world.

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
mining: optional custom break time, tool requirements, and tool-specific drops
farming: optional right-click harvest support, tool requirements, and tool-specific drops
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

## Custom Mining

Add `mining` to a rule or variant when ClientCore should control the break time instead of using the vanilla client
material timing. If `tools` is set, only matching tools can mine the node.

```yaml
mining:
  active-block: BARRIER
  visual-mode: block-display
  feedback:
    display: true
    actionbar: false
    particles: true
    sounds: true
    interval-ticks: 4
    message: "<gray>Mining <white>{progress}%</white>"
    display-format: "<bold>{bar}</bold> <white>{progress}%</white>"
    bar-length: 12
    low-color: "<gold>"
    mid-color: "<yellow>"
    high-color: "<green>"
    empty-color: "<dark_gray>"
    background-argb: "8C0C1016"
  default-time-ticks: 60
  tools:
    - item:
        type: vanilla
        material: IRON_PICKAXE
      time-ticks: 80
      drops:
        - type: vanilla
          material: RAW_IRON
          amount: 1
    - item:
        type: mmoitems
        mmo-type: TOOL
        mmo-id: MINER_PICKAXE
      time-ticks: 30
      drops:
        - type: mmoitems
          mmo-type: MATERIAL
          mmo-id: RICH_IRON
          material: RAW_IRON
```

Tool drops override the variant/rule `drops`. If a matching tool has no `drops`, ClientCore uses the normal
variant/rule drops.

`active-block` is the hitbox block sent while the player is actively mining.

`visual-mode` controls what the player sees while the custom mining timer is active:

- `block-display` is the default and preserves the old behavior. `BARRIER` is recommended because it is hard to break
  and normally invisible; ClientCore renders a player-only `BlockDisplay` with the ready block on top.
- `active-block` uses the sent `active-block` itself as the visual block and does not spawn the ready-block
  `BlockDisplay`. Use this with a resource pack that remaps a hard block or block state to your stone/ore/custom-block
  texture. Because the client is mining a real block state, vanilla crack overlays render on that texture.

Aliases accepted for `active-block` mode: `resource-pack`, `resource-pack-block`, and `vanilla-crack`.

ClientCore can also resolve custom block IDs directly when the matching plugin is installed and its hook is enabled:

```yaml
mining:
  active-block: oraxen:amethyst_ore
  visual-mode: active-block
```

Supported prefixes:

- `oraxen:<id>`
- `itemsadder:<namespace:id>`
- `nexo:<id>`
- `craftengine:<namespace:id>`

The resolver asks the custom block plugin for the block state it uses in-game, then sends that state as the active
mining block. This works for normal block-state based custom blocks. Furniture/entity-based custom blocks cannot use
vanilla crack overlays because the client is not mining a real block state.

`feedback` adds non-vanilla progress feedback for the `BARRIER + BlockDisplay` mode. `display` creates a player-only
`TextDisplay` progress bar above the block, and particles/sounds provide extra local feedback while the server-side
mining timer is running.

## Farming

Add `farming` to a rule or variant when the node should be harvested by right-click instead of being mined. Farming uses
the same visibility, conditions, variants, cooldown, regen, WorldGuard flag, direct inventory rewards, MMOItems rewards,
and tool matching behavior as mining. Optional `stages` let crops grow through visible block ages over time and provide
stage-specific early harvest rewards.

```yaml
wheat-farm:
  enabled: true
  location:
    world: world
    x: 20
    y: 65
    z: 20
  ready-block: WHEAT
  cooldown-block: FARMLAND
  regen-ticks: 200
  farming:
    enabled: true
    stages:
      - block: WHEAT[age=0]
        after-ticks: 1
        drops:
          - type: vanilla
            material: WHEAT_SEEDS
            amount: 1
      - block: WHEAT[age=3]
        after-ticks: 80
        drops:
          - type: vanilla
            material: WHEAT_SEEDS
            amount: 2
      - block: WHEAT[age=7]
        after-ticks: 120
        drops:
          - type: vanilla
            material: WHEAT
            amount: 2
    tools:
      - item:
          type: vanilla
          material: WOODEN_HOE
  drops:
    - type: vanilla
      material: WHEAT
      amount: 1
```

If `tools` is set, only matching tools can harvest the node. Tool drops override the variant/rule `drops`; if the
matching tool has no `drops`, ClientCore uses the normal variant/rule drops.

If `stages` is set, stage drops are used before the normal variant/rule `drops`. Players can harvest at any stage:
stage `WHEAT[age=3]` can give one reward, while the final mature stage `WHEAT[age=7]` can give wheat. `after-ticks`
is the delay before that stage appears after the previous stage. The first stage is sent immediately after harvest.

Supported stage block formats:

```yaml
block: WHEAT[age=3]
```

or:

```yaml
material: WHEAT
age: 3
```

### Common Farm Ages

Use the crop's max age as the final mature stage. These are common vanilla block data ages:

```text
WHEAT: 0-7
CARROTS: 0-7
POTATOES: 0-7
BEETROOTS: 0-3
NETHER_WART: 0-3
COCOA: 0-2
SWEET_BERRY_BUSH: 0-3
CAVE_VINES: 0-25
CAVE_VINES_PLANT: 0-25
KELP: 0-25
KELP_PLANT: 0-25
CACTUS: 0-15
SUGAR_CANE: 0-15
BAMBOO: 0-1
TORCHFLOWER_CROP: 0-1
PITCHER_CROP: 0-4
CHORUS_FLOWER: 0-5
```

Examples:

```yaml
# Wheat/carrot/potato, max age 7
stages:
  - block: WHEAT[age=0]
    after-ticks: 1
  - block: WHEAT[age=3]
    after-ticks: 80
  - block: WHEAT[age=7]
    after-ticks: 120

# Beetroot/nether wart, max age 3
stages:
  - block: BEETROOTS[age=0]
    after-ticks: 1
  - block: BEETROOTS[age=1]
    after-ticks: 80
  - block: BEETROOTS[age=3]
    after-ticks: 120

# Cocoa, max age 2. Cocoa also needs a facing direction.
stages:
  - block: COCOA[age=0,facing=north]
    after-ticks: 1
  - block: COCOA[age=1,facing=north]
    after-ticks: 80
  - block: COCOA[age=2,facing=north]
    after-ticks: 120
```

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
