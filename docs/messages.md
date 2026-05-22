---
layout: default
title: Messages Configuration
---

# Messages & Language (`messages.yml`)

Fully customize the plugin's output messages. ClientCore uses the **MiniMessage** format.

## Examples of MiniMessage Tags

- `<red>`, `<green>`, `<gold>` - Colors
- `<bold>`, `<italic>` - Decorations
- `<gradient:red:blue>Text</gradient>` - Gradients

## Example Settings

```yaml
prefix: "<dark_gray>[<aqua>ClientCore</aqua>]<reset> "
commands:
  blocks-refreshed: "<green>Refreshed client-side blocks around you."
```
