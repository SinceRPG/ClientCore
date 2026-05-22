---
layout: default
title: Messages
nav_order: 11
---

# Messages

`messages.yml` controls command output and console messages.

It stays at:

```text
plugins/ClientCore/messages.yml
```

## MiniMessage

Messages use MiniMessage formatting:

```yaml
prefix: "<dark_gray>[<aqua>ClientCore</aqua>]<reset> "
commands:
  reloaded: "<green>ClientCore configurations reloaded successfully."
```

## Placeholders

Command messages use simple replacement placeholders such as:

```text
{amount}
{player}
{mob}
{level}
{id}
{attribute}
{value}
```

Example:

```yaml
commands:
  mythic-mob-spawned: "<green>Spawned {amount} MythicMob(s) <white>{mob}</white> level {level} for {player}."
```

## Auto Update

When ClientCore loads, missing keys in `messages.yml` are added from the jar defaults. Existing customized values are
not reset.
