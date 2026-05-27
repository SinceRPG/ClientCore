# Changelog

## Unreleased

### Added

- Added client-side farming nodes under block regen through `farming`, including right-click harvest, optional tool requirements, and tool-specific drops.
- Added farming growth stages with block age/data support such as `WHEAT[age=3]`, stage timing, and stage-specific early harvest drops.
- Added custom client-side block mining rules under `mining`.
- Added per-tool mining requirements for vanilla tools and MMOItems tools.
- Added per-tool mining durations through `time-ticks`.
- Added per-tool rewards, including MMOItems reward support.
- Added PacketEvents-backed custom mining for real vanilla blocks through `vanilla-mining`.
- Added tool-specific drop tables for vanilla mining rules; matching tool drops override block-level drops.
- Added natural-break fallback for vanilla mining when neither the matching tool nor the block defines custom drops.
- Added `active-block` for custom mining. `BARRIER` is recommended so the client does not break the visible block early.
- Added player-only `BlockDisplay` overlays while custom mining is active, so players can still see the configured ready block.
- Added `mining.visual-mode: active-block` for resource-pack/custom-block visuals that need vanilla crack overlays.
- Added custom block ID resolution for active mining visuals through Oraxen, ItemsAdder, Nexo, and CraftEngine hooks.
- Added player-only `TextDisplay` mining progress bars above active mining nodes.
- Added configurable mining feedback:
  - `display`
  - `actionbar`
  - `particles`
  - `sounds`
  - `interval-ticks`
  - `message`
  - `display-format`
  - `bar-length`
  - `low-color`
  - `mid-color`
  - `high-color`
  - `empty-color`
  - `background-argb`
- Added block break acknowledgement handling for early client-side digging predictions.
- Added `ClientPacketService#sendBlockBreakAnimation`.
- Added `ClientPacketService#acknowledgeDig`.
- Added MMOItems held-item matching for mining tool checks.
- Added `MiningVisualService` to own and clean up mining `BlockDisplay` and `TextDisplay` entities.
- Added GitHub Actions wiki documentation.
- Added a Folia-specific GitHub issue template.

### Changed

- Custom mining now uses server-side progress as the authority instead of trusting the client's vanilla mining speed.
- Block config loading now supports both `block-regen` and `vanilla-mining` roots in the blocks folder.
- Block mining rewards are inserted directly into the player inventory. Leftovers drop at the player's location only if the inventory is full.
- Luck item command rewards now modify player inventories on the player entity thread.
- Virtual chest GUI opening now runs on the player entity thread.
- Mining display styling is now configured in YAML instead of hardcoded in Java.
- Client mob random selection now uses `ThreadLocalRandom` for safer cross-region execution.
- `FoliaScheduler` now attempts cancellable Folia entity delayed tasks through `EntityScheduler#runDelayed` before falling back to non-cancellable `execute`.
- Player-only mining display visibility now runs on the viewer's entity thread.
- Mining display cleanup now runs through the display entity scheduler.

### Fixed

- Fixed fake block nodes turning into client-side air when the vanilla client finished breaking the visual block before custom `time-ticks`.
- Fixed one-click mining sessions that continued after the player stopped digging.
- Fixed mining cooldown visuals not being resent reliably after completion.
- Fixed virtual mining drops requiring player movement to pick up in some cases.
- Fixed potential mining hologram/display leaks on cancel, completion, quit, reload, and shutdown.
- Fixed Folia risks around player inventory mutation and inventory GUI opening.

### Notes

- Vanilla crack overlays cannot render on `BlockDisplay` entities. ClientCore uses TextDisplay, particle, and sound feedback for the smooth `BARRIER + BlockDisplay` mining mode.
- For true vanilla crack visuals, use a real hard `active-block` such as `OBSIDIAN`; this means players will see that active material unless a resource pack remaps it.
