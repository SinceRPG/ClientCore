---
layout: default
title: Troubleshooting
nav_order: 12
---

# Troubleshooting

## `/clientcore status` Shows 0 Rules

`/clientcore status` reports `Block Regen`, `Vanilla Mining`, `Farming`, and `Mobs` separately. Check the counter for
the feature you are testing.

Check folder paths:

```text
plugins/ClientCore/blocks/*.yml
plugins/ClientCore/mobs/*.yml
plugins/ClientCore/npcs/*.yml
plugins/ClientCore/drops/*.yml
plugins/ClientCore/chests/*.yml
```

Files in subfolders are also loaded.

Check your file style. This is valid:

```yaml
my_rule:
  enabled: true
```

This is also valid:

```yaml
rules:
  my_rule:
    enabled: true
```

And full root style is valid:

```yaml
block-regen:
  rules:
    my_rule:
      enabled: true
```

## Conditions Do Not Pass

Temporarily disable conditions:

```yaml
condition: ""
conditions: []
```

If the rule appears, the issue is PlaceholderAPI or the condition expression.

Common checks:

- PlaceholderAPI is installed.
- The placeholder expansion is installed.
- The placeholder returns a number when using numeric comparisons.
- Non-empty conditions use semicolon syntax such as `%placeholder%;>=;value`.

## Block Regen Does Not Appear

- Run `/clientcore status` and confirm `Block Regen:` is above `0`.
- Stand near the configured location.
- Run `/clientcore refresh`.
- Check `block-regen.enabled`.
- Check `refresh-radius`.
- Check the world name.
- Remove WorldGuard flag restrictions while testing.

## Vanilla Mining Does Not Use Custom Drops

- Check `vanilla-mining.enabled`.
- Confirm the block key matches a real Bukkit material such as `STONE` or `DIAMOND_ORE`.
- Put tool-specific rewards under `vanilla-mining.blocks.<BLOCK>.mining.tools[].drops`.
- Confirm the held item matches the configured tool. If no tool `drops` match, ClientCore falls back to block-level
  `drops`, then to natural vanilla drops.
- Turn on `/clientcore debug on` to log the matched tool rule, vanilla/MMOItems match source, enchant requirement
  levels, SinceEnchantments hook state, and drop source.
- Remove WorldGuard flag restrictions while testing, or leave `default-worldguard-flag: ""`.

## MythicMob Spawns But Model Is Visible To Everyone

- Confirm `hooks.modelengine: true`.
- Confirm ModelEngine is installed and enabled.
- Confirm the ModelEngine version matches the compiled API version.
- Test with `/clientcore mythicspawn self <mob>`.

MythicMobs skills are still server-side. If a skill damages `@PlayersNearOrigin`, MythicMobs can still target players
unless the skill config is written to target only the owner or your server logic cancels it.

## Rewards Do Not Give Items

- Check the `drops:` list indentation.
- For MMOItems, confirm `hooks.mmoitems: true`.
- Confirm the MMOItems type and ID exist.
- Add a vanilla `material` fallback.

## SinceEnchantments Tool Requirements Do Not Match

- Run `/clientcore status` and confirm `SinceEnchantments: active`.
- Confirm `hooks.sinceenchantments: true`.
- Use `custom-enchants` for SinceEnchantments IDs; plain `enchants` is for vanilla Bukkit/Paper enchantments by default.
- Turn on `/clientcore debug on` and check the logged `enchant-checks` levels for the held tool.

## Config Changes Do Not Apply

Run:

```text
/clientcore reload
```

Some third-party plugin changes may require reloading that plugin or restarting the server.
