---
layout: default
title: Configuration Layout
nav_order: 2
---

# Configuration Layout

ClientCore uses root files for global settings and folders for feature rules.

```text
plugins/ClientCore/
  config.yml
  messages.yml
  blocks/
  mobs/
  npcs/
  drops/
  chests/
  builds/
```

## Feature Folder Loading

Each feature folder loads every `.yml` and `.yaml` file inside it, including subfolders.

```text
blocks/*.yml
blocks/*.yaml
blocks/**/*.yml
blocks/**/*.yaml
```

The same applies to `mobs`, `npcs`, `drops`, `chests`, and `builds`.

Files are sorted alphabetically by path before merging. If two files define the same rule ID, the later file overrides
the same keys.

## Supported File Styles

Every feature folder accepts three styles.

Full root style:

```yaml
client-mobs:
  enabled: true
  rules:
    zombie_a:
      enabled: true
      fallback-entity: ZOMBIE
```

Rules root style:

```yaml
rules:
  zombie_a:
    enabled: true
    fallback-entity: ZOMBIE
```

Direct rule style:

```yaml
zombie_a:
  enabled: true
  fallback-entity: ZOMBIE

zombie_b:
  enabled: true
  fallback-entity: HUSK
```

## Feature Roots

These are the full root names if you use full root style:

```text
blocks: block-regen
mobs: client-mobs
npcs: client-npcs
drops: client-drops
chests: client-loot-chests
builds: client-builds
```

## Legacy Migration

If a legacy root file exists, ClientCore still loads it:

```text
blocks.yml
mobs.yml
npcs.yml
drops.yml
chests.yml
client-builds.yml
```

If the new folder file does not exist yet, ClientCore moves the legacy file into the matching folder. If both old and
new files exist, the legacy file is loaded first and folder files override duplicate keys.
