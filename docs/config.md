---
layout: default
title: Main Configuration
nav_order: 3
---

# Main Configuration

`config.yml` contains global settings, SQL storage, the Luck system, and hook toggles. Unlike feature configs, it stays
at the root of `plugins/ClientCore/`.

## Storage

Default SQLite setup:

```yaml
storage:
  type: sqlite
  sqlite:
    file: clientcore.db
```

MySQL setup:

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: clientcore
    username: root
    password: "password"
```

ClientCore stores player Luck data and mob spawn point data in SQL. Fixed mob spawn points are cached in memory while
the server is running, so the spawn loop does not query SQL every tick.

## Luck

Luck increases rare variant weights for block and mob variants marked with `rare: true`.

```yaml
luck:
  max-rare-weight-bonus-percent: 300.0
```

Formula:

```text
finalWeight = baseWeight * (1 + min(maxBonus, luck * luckMultiplier / 100))
```

Luck item settings live under `luck.item`. The item supports the same vanilla item metadata style used by rewards.

## Hooks

```yaml
hooks:
  placeholderapi: true
  worldguard: true
  mmoitems: true
  mythicmobs: true
  modelengine: true
  citizens: true
  fancynpcs: true
```

Set unused hooks to `false` if the plugin is not installed. ClientCore checks whether the plugin is actually enabled
before using a hook, so leaving optional hooks enabled is usually safe.

## Folia and Packet Safety Settings

```yaml
settings:
  client-block-support:
    enabled: true
    monitor-period-ticks: 5
    player-half-width: 0.3001
    foot-y-offset: 0.03
  packet-filter:
    visual-entity-radius-squared: 9.0
    effect-hide-radius-squared: 2.25
```

`client-block-support` prevents survival and adventure players from being kicked by vanilla floating checks when they
stand on solid client-side packet blocks. The plugin temporarily manages `allowFlight` only while the player is
supported by a visual block and cancels player flight toggles when the player did not originally have flight permission.

`packet-filter` controls how aggressively non-owner players are prevented from seeing effects around client-side mobs.

## Auto Update

On load, ClientCore adds missing keys to `config.yml` and `messages.yml` only. Existing values are not reset.

Feature folders are not auto-merged into one file. They are loaded as split YAML files and merged in memory.
