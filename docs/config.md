---
layout: default
title: Main Configuration
nav_order: 2
---

# Main Configuration (`config.yml`)

The main configuration file controls database settings, plugin hooks, and the Luck system.

## Storage Settings

You can choose between `sqlite` (for single servers) and `mysql` (for proxy networks).

```yaml
storage:
  type: sqlite
  sqlite:
    file: clientcore.db
```

## Luck System

Luck increases a player's chance of rolling a rare variant of a block drop or mob spawn.
Administrators can give out Luck Tokens via `/clientcore luck giveitem`.
The base formula is:
`finalWeight = baseWeight * (1 + min(maxBonus, luck * luckMultiplier / 100))`

## Third-Party Hooks

ClientCore seamlessly integrates with several other plugins:

- **PlaceholderAPI:** For conditions in rules.
- **WorldGuard:** For region-based logic.
- **MMOItems / MythicMobs:** For custom items and custom entities.
- **Citizens / FancyNpcs:** For NPC generation.

If you don't use a plugin, simply set its hook to `false`.
