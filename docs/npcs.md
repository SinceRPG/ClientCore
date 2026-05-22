---
layout: default
title: NPCs Configuration
nav_order: 8
---

# Client-Side NPCs (`npcs/npcs.yml`)

Similar to client-sided mobs, but designed specifically around NPC providers.

## Providers

You can choose which plugin powers the NPC:

- `FANCYNPCS`
- `CITIZENS`
- `MYTHICMOBS`
- `VANILLA`

## Example Configuration

All `.yml` files inside `npcs/` are loaded and merged. A file can use the full `client-npcs:` root, a `rules:` root,
or direct rule IDs.

```yaml
rules:
  vip_quest_giver:
    enabled: true
    provider-type: CITIZENS
    provider-id: "12"
    entity-type: PLAYER
    name: "<gold>VIP Quests"
    conditions:
      - "%luckperms_has_permission_vip% == true"
    location:
      world: hub
      x: -20.5
      y: 70.0
      z: 15.5
      yaw: 90.0
      pitch: 0.0
```
