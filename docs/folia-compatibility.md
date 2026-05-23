# Folia Compatibility

ClientCore is designed to run on Folia, Paper, and compatible Spigot servers.

## Scheduler model

All plugin-owned scheduling goes through `FoliaScheduler`.

- On Folia, the scheduler is detected with `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`.
- Entity work is routed through the entity scheduler.
- Block and spawn work is routed through the region scheduler for the target location.
- Repeating global scans use the global region scheduler on Folia and the Bukkit scheduler on standard servers.
- Returned tasks use `CompatTask`, so cancellation works without exposing Folia-only `ScheduledTask` classes to standard
  servers.

## Entity rules

Entity and player state changes must be made on the owning entity thread.

Examples in this codebase:

- Virtual chest GUI opening is routed through the player entity scheduler.
- Luck item command rewards modify inventories on the player entity thread.
- Virtual drop pickup adds items to the player's inventory on the player thread.
- Virtual mobs are removed through entity-thread scheduling.
- Mining BlockDisplay/TextDisplay cleanup is routed through the display entity scheduler.
- Player-only display visibility is routed through the viewer's player scheduler.
- Packet filters use immutable mob snapshots instead of reading live entity locations from PacketEvents callbacks.

## Region rules

World and block access must happen on the owning region.

Examples in this codebase:

- Real block restoration packets read `world.getBlockAt(...)` inside `scheduler.region(location, ...)`.
- Client-side build save restores real blocks inside region tasks.
- Entity spawning is called only after scheduling work on the target spawn location.
- Mining visual BlockDisplay/TextDisplay entities are spawned on the target block region.

## Teleport rules

ClientCore does not currently perform plugin-owned teleports. New teleport code must use
`entity.teleportAsync(location)` and must put follow-up logic in the returned `CompletableFuture<Boolean>` callback.

## Packet and NMS notes

ClientCore uses PacketEvents wrappers for block changes, entity destroy packets, spawn packet filtering, sounds, and
particles. It does not use direct NMS or CraftBukkit classes.

PacketEvents callbacks are not treated as safe Bukkit entity access points. Packet filtering must use IDs and immutable
snapshots owned by ClientCore.

Client-side custom mining uses packet block changes for the hitbox block and Bukkit display entities for the visual
overlay. Vanilla crack overlays cannot render on BlockDisplay entities, so ClientCore provides TextDisplay progress,
particles, and sounds for the smooth BARRIER-based mining mode.

## I/O rules

SQL access is asynchronous through `StorageService`. YAML config loading and schema startup run during plugin
load/reload, not inside region tick loops. Event listeners should not add blocking database calls, network calls, or
`Thread.sleep`.
