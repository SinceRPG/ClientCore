---
layout: default
title: Client Builds
nav_order: 6
---

# Client Builds

Client builds let an admin build a structure normally, save the result as a packet-only layout, restore the real world,
and then apply that layout to specific players. The feature is implemented with PacketEvents and
Folia-aware scheduling.

## Workflow

```text
/packetmode on
```

Place and break blocks. Placed blocks are captured as the fake build. Breaking an existing real block records an AIR
fake block and leaves the real block untouched.

```text
/packetsave tutorial_bridge
```

The build is saved as a split YAML file under `plugins/ClientCore/builds/generated/`, capture mode is disabled, and the
real world is restored.

```text
/packetapply tutorial_bridge Steve on
/packetapply tutorial_bridge Steve off
```

The same commands are also available under the main command:

```text
/clientcore build mode on
/clientcore build save tutorial_bridge
/clientcore build apply tutorial_bridge Steve on
```

## Storage

```yaml
client-builds:
  enabled: true
  refresh-period-ticks: 40
  join-delay-ticks: 10
  generated-folder: builds/generated
  players-file: client-build-players.yml
  rules:
    tutorial_bridge:
      enabled: true
      auto-apply: true
      condition: "%mmocore_level%;>=;1"
      conditions:
        - "%luckperms_has_permission_clientcore.tutorial%;==;true"
      blocks:
        "world,100,65,100": "minecraft:oak_planks"
```

The `builds/` folder supports the same split-file styles as blocks, mobs, NPCs, drops, and chests. `players-file` stores
manual build assignments by player UUID, so assigned visuals are restored after a player rejoins.

## Visibility Conditions

Each build can define `auto-apply`, `condition`, and `conditions`.

When `auto-apply: true`, every online player who passes the conditions can see the build, including builds saved under
`builds/generated/`. No `/packetapply` assignment is required.

Generated builds are written with `auto-apply: false` so a freshly saved admin build does not appear to everyone by
accident. To make a generated build visible by conditions, edit its generated YAML file:

```yaml
client-builds:
  rules:
    tutorial_bridge:
      enabled: true
      auto-apply: true
      condition: ""
      conditions:
        - "%luckperms_has_permission_clientcore.tutorial%;==;true"
```

When `auto-apply: false`, use `/packetapply <build> <player> on` to assign the build. Conditions still apply after
assignment. If the player stops passing the conditions, ClientCore sends the original block states back to that player.

## Folia Safety

- Block restoration runs on the owning region scheduler for each changed block location.
- Packet sends are routed through the target player's scheduler when needed.
- Runtime state uses UUID keys and concurrent collections.
- The refresh task uses the global region scheduler only to dispatch player-owned work.

## Notes

Client builds are visual. They do not create real collision or server-side blocks after saving. Use normal block regen
rules when you need rewards, cooldowns, drops, or WorldGuard checks tied to individual block nodes.
