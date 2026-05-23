package net.danh.clientcore.block;

import net.danh.clientcore.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MiningVisualService {
    private final Plugin plugin;
    private final FoliaScheduler scheduler;
    private final Map<UUID, MiningVisuals> displays = new ConcurrentHashMap<>();

    MiningVisualService(Plugin plugin, FoliaScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    void show(Player player, Location location, String blockMaterial, BlockMiningFeedback feedback, boolean blockOverlay) {
        clear(player);
        if (!blockOverlay && !feedback.display()) {
            return;
        }
        if (blockOverlay && "ORIGINAL".equalsIgnoreCase(blockMaterial)) {
            return;
        }
        BlockData data = null;
        if (blockOverlay) {
            Material material = Material.matchMaterial(blockMaterial);
            if (material == null || material.isAir()) {
                return;
            }
            data = Bukkit.createBlockData(material);
        }
        Location spawn = location.toBlockLocation();
        BlockData finalData = data;
        scheduler.region(spawn, () -> {
            if (!player.isOnline()) {
                return;
            }
            BlockDisplay display = null;
            if (blockOverlay) {
                display = (BlockDisplay) spawn.getWorld().spawnEntity(spawn, EntityType.BLOCK_DISPLAY);
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setBlock(finalData);
                display.setViewRange(32.0F);
                display.setShadowRadius(0.0F);
                display.setShadowStrength(0.0F);
                display.setVisibleByDefault(false);
            }

            TextDisplay text = null;
            if (feedback.display()) {
                text = (TextDisplay) spawn.getWorld().spawnEntity(spawn.clone().add(0.5D, 1.18D, 0.5D), EntityType.TEXT_DISPLAY);
                text.setPersistent(false);
                text.setInvulnerable(true);
                text.setGravity(false);
                text.setBillboard(Display.Billboard.CENTER);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setSeeThrough(false);
                text.setShadowed(true);
                text.setDefaultBackground(false);
                text.setBackgroundColor(Color.fromARGB(feedback.backgroundArgb()));
                text.setTextOpacity((byte) 255);
                text.setLineWidth(220);
                text.setViewRange(32.0F);
                text.setVisibleByDefault(false);
                updateText(text, 0, feedback);
            }

            TextDisplay progressText = text;
            BlockDisplay blockDisplay = display;
            scheduler.entity(player, () -> {
                if (!player.isOnline()) {
                    if (blockDisplay != null) {
                        remove(blockDisplay);
                    }
                    if (progressText != null) {
                        remove(progressText);
                    }
                    return;
                }
                if (blockDisplay != null) {
                    player.showEntity(plugin, blockDisplay);
                }
                if (progressText != null) {
                    player.showEntity(plugin, progressText);
                }
            });

            MiningVisuals previous = displays.put(player.getUniqueId(), new MiningVisuals(display, text));
            if (previous != null && previous.isValid()) {
                if (previous.block() != null) {
                    remove(previous.block());
                }
                if (previous.text() != null) {
                    remove(previous.text());
                }
            }
        });
    }

    void updateProgress(Player player, int progress, BlockMiningFeedback feedback) {
        MiningVisuals visuals = displays.get(player.getUniqueId());
        if (visuals == null || !visuals.isValid()) {
            return;
        }
        if (visuals.text() == null || !visuals.text().isValid()) {
            return;
        }
        updateText(visuals.text(), Math.max(0, Math.min(100, progress)), feedback);
    }

    void clear(Player player) {
        MiningVisuals visuals = displays.remove(player.getUniqueId());
        if (visuals == null) {
            return;
        }
        if (visuals.block() != null) {
            remove(visuals.block());
        }
        if (visuals.text() != null) {
            remove(visuals.text());
        }
    }

    void clearAll() {
        for (MiningVisuals visuals : displays.values()) {
            if (visuals.block() != null) {
                remove(visuals.block());
            }
            if (visuals.text() != null) {
                remove(visuals.text());
            }
        }
        displays.clear();
    }

    private void updateText(TextDisplay text, int progress, BlockMiningFeedback feedback) {
        int barLength = Math.max(1, feedback.barLength());
        int filled = Math.max(0, Math.min(barLength, (int) Math.round(progress / 100.0D * barLength)));
        String color = progress >= 85 ? feedback.highColor() : progress >= 45 ? feedback.midColor() : feedback.lowColor();
        String bar = color + "|".repeat(filled) + feedback.emptyColor() + "|".repeat(barLength - filled);
        String textFormat = feedback.displayFormat()
                .replace("{bar}", bar)
                .replace("{progress}", String.valueOf(progress));
        text.text(net.danh.clientcore.util.Text.mm(textFormat));
    }

    private void remove(Entity entity) {
        if (!entity.isValid()) {
            return;
        }
        if (!plugin.isEnabled()) {
            try {
                entity.remove();
            } catch (Exception ignored) {
            }
            return;
        }
        scheduler.entity(entity, entity::remove);
    }

    private record MiningVisuals(BlockDisplay block, TextDisplay text) {
        boolean isValid() {
            return (block != null && block.isValid()) || (text != null && text.isValid());
        }
    }
}
