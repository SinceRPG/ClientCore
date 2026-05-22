---
layout: default
title: Conditions
nav_order: 4
---

# Conditions

Conditions decide whether a player can see or use a rule. They are used by blocks, mobs, NPCs, drops, chests, cooldowns,
and variants.

All non-empty conditions must use semicolon syntax:

```text
placeholder;operator;value
```

Do not use expression syntax such as `%mmocore_level% >= 2`. That format is intentionally invalid.

## Single Condition

```yaml
condition: "%mmocore_level%;>=;2"
```

If no single condition is needed, leave it blank:

```yaml
condition: ""
```

## Conditions List

Use `conditions:` for multiple required checks. All required lines must pass.

```yaml
conditions:
  - "%mmocore_level%;>=;2"
  - "%mmocore_level%;<=;5"
```

## Operators

Supported operators:

```text
==, =, !=, <, >, <=, >=, contains, starts_with, ends_with
```

Aliases:

```text
eq, ne, lt, gt, lte, gte, starts, ends
```

## Optional Condition IDs

Optional lines do not block the rule. They only record an ID that variants can require.

```yaml
conditions:
  - "vip;%luckperms_has_permission_clientcore.vip%;==;yes;optional"
```

Then a variant can require that optional ID:

```yaml
variants:
  - id: normal
    weight: 90
    ready-block: COAL_ORE
  - id: vip
    weight: 10
    rare: true
    required-condition-ids: [ vip ]
    ready-block: DIAMOND_ORE
```

You can also use optional without an ID:

```yaml
conditions:
  - "%some_placeholder%;==;yes;optional"
```

That line will not block the rule, but no variant ID is recorded.

## PlaceholderAPI Notes

If PlaceholderAPI or the required expansion is missing, placeholders may remain unresolved. For example,
`%mmocore_level%;>=;2` will fail if `%mmocore_level%` is returned as literal text instead of a number.

When debugging, temporarily use:

```yaml
condition: ""
conditions: []
```

If the rule appears after removing conditions, the issue is the placeholder, expansion, or condition value.
