---
layout: default
title: NPCs
nav_order: 10
---

# Client-Side NPCs

NPC rules spawn or toggle NPC providers for players who pass conditions.

Folder:

```text
plugins/ClientCore/npcs/
```

## Minimal Example

```yaml
guide_npc:
  enabled: true
  provider-type: VANILLA
  provider-id: ""
  entity-type: VILLAGER
  name: "<yellow>Server Guide"
  condition: "%mmocore_level%;<=;5"
  conditions: []
  location:
    world: world
    x: 10.5
    y: 65.0
    z: 10.5
    yaw: 180.0
    pitch: 0.0
```

## Providers

```text
VANILLA: ClientCore spawns a Bukkit entity and hides it per player.
MYTHICMOBS: uses a MythicMobs mob ID.
CITIZENS: toggles a Citizens NPC by provider-id.
FANCYNPCS: toggles a FancyNpcs NPC by provider-id.
```

## Full Root Example

```yaml
client-npcs:
  enabled: true
  refresh-period-ticks: 100
  rules:
    vip_quest_giver:
      enabled: true
      provider-type: CITIZENS
      provider-id: "12"
      entity-type: PLAYER
      name: "<gold>VIP Quests"
      conditions:
        - "%luckperms_has_permission_vip%;==;true"
      location:
        world: hub
        x: -20.5
        y: 70.0
        z: 15.5
        yaw: 90.0
        pitch: 0.0
```

## Rule Fields

```text
enabled: true/false
provider-type: VANILLA, MYTHICMOBS, CITIZENS, FANCYNPCS
provider-id: ID used by the provider
entity-type: Bukkit EntityType for vanilla visual fallback
name: display name
condition: optional one-line semicolon condition
conditions: optional list of semicolon conditions
location: world, x, y, z, yaw, pitch
```

## Debug Checklist

- Temporarily remove conditions.
- Confirm the provider plugin is installed and enabled.
- For Citizens/FancyNpcs, confirm `provider-id` exactly matches the existing NPC.
- For vanilla NPCs, confirm the target world is loaded.
