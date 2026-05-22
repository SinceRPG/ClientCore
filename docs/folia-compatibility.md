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

- Packet sends to players are routed through `ClientPacketService`, which schedules onto the player thread.
- Virtual drop pickup adds items to the player's inventory on the player thread.
- Virtual mobs are removed through entity-thread scheduling.
- Packet filters use immutable mob snapshots instead of reading live entity locations from PacketEvents callbacks.

## Region rules

World and block access must happen on the owning region.

Examples in this codebase:

- Real block restoration packets read `world.getBlockAt(...)` inside `scheduler.region(location, ...)`.
- Client-side build save restores real blocks inside region tasks.
- Entity spawning is called only after scheduling work on the target spawn location.

## Teleport rules

ClientCore does not currently perform plugin-owned teleports. New teleport code must use
`entity.teleportAsync(location)` and must put follow-up logic in the returned `CompletableFuture<Boolean>` callback.

## Packet and NMS notes

ClientCore uses PacketEvents wrappers for block changes, entity destroy packets, spawn packet filtering, sounds, and
particles. It does not use direct NMS or CraftBukkit classes.

PacketEvents callbacks are not treated as safe Bukkit entity access points. Packet filtering must use IDs and immutable
snapshots owned by ClientCore.

## I/O rules

SQL access is asynchronous through `StorageService`. YAML config loading and schema startup run during plugin
load/reload, not inside region tick loops. Event listeners should not add blocking database calls, network calls, or
`Thread.sleep`.
