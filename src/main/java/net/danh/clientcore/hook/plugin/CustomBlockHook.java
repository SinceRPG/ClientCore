package net.danh.clientcore.hook.plugin;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class CustomBlockHook {
    private CustomBlockHook() {
    }

    public static Optional<BlockData> resolve(String configuredId, boolean oraxen, boolean itemsAdder, boolean nexo, boolean craftEngine) {
        if (configuredId == null || configuredId.isBlank()) {
            return Optional.empty();
        }
        String input = configuredId.trim();
        ProviderId providerId = providerId(input);
        if (providerId.provider() != null) {
            return switch (providerId.provider()) {
                case "oraxen" -> oraxen ? oraxen(providerId.id()) : Optional.empty();
                case "itemsadder", "ia" -> itemsAdder ? itemsAdder(providerId.id()) : Optional.empty();
                case "nexo" -> nexo ? nexo(providerId.id()) : Optional.empty();
                case "craftengine", "ce" -> craftEngine ? craftEngine(providerId.id()) : Optional.empty();
                default -> Optional.empty();
            };
        }

        if (itemsAdder) {
            Optional<BlockData> data = itemsAdder(input);
            if (data.isPresent()) return data;
        }
        if (oraxen) {
            Optional<BlockData> data = oraxen(input);
            if (data.isPresent()) return data;
        }
        if (nexo) {
            Optional<BlockData> data = nexo(input);
            if (data.isPresent()) return data;
        }
        if (craftEngine) {
            return craftEngine(input);
        }
        return Optional.empty();
    }

    public static boolean hasKnownProviderPrefix(String configuredId) {
        if (configuredId == null || configuredId.isBlank()) {
            return false;
        }
        return providerId(configuredId.trim()).provider() != null;
    }

    public static String providerName(String configuredId) {
        if (configuredId == null || configuredId.isBlank()) {
            return "unknown";
        }
        ProviderId providerId = providerId(configuredId.trim());
        return switch (providerId.provider() == null ? "" : providerId.provider()) {
            case "oraxen" -> "Oraxen";
            case "itemsadder", "ia" -> "ItemsAdder";
            case "nexo" -> "Nexo";
            case "craftengine", "ce" -> "CraftEngine";
            default -> "unknown";
        };
    }

    private static Optional<BlockData> itemsAdder(String id) {
        return invokeStaticBlockData("dev.lone.itemsadder.api.CustomBlock", "getBaseBlockData", id);
    }

    private static Optional<BlockData> oraxen(String id) {
        return invokeStaticBlockData("io.th0rgal.oraxen.api.OraxenBlocks", "getOraxenBlockData", stripKnownNamespace(id, "oraxen"));
    }

    private static Optional<BlockData> nexo(String id) {
        return invokeStaticBlockData("com.nexomc.nexo.api.NexoBlocks", "blockData", stripKnownNamespace(id, "nexo"));
    }

    private static Optional<BlockData> craftEngine(String id) {
        try {
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            Object key = craftEngineKey(keyClass, id);
            if (key == null) {
                return Optional.empty();
            }
            Class<?> blocksClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            Object customBlock = invokeStatic(blocksClass, "byId", new Class<?>[]{keyClass}, key);
            if (customBlock == null) {
                return Optional.empty();
            }
            Object state = invokeAny(customBlock, "defaultState", "getDefaultState");
            return coerceBlockData(state);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Object craftEngineKey(Class<?> keyClass, String id) throws ReflectiveOperationException {
        String normalized = stripKnownNamespace(id, "craftengine");
        String[] parts = normalized.split(":", 2);
        if (parts.length == 2) {
            return invokeStatic(keyClass, "of", new Class<?>[]{String.class, String.class}, parts[0], parts[1]);
        }
        Object key = invokeStatic(keyClass, "of", new Class<?>[]{String.class}, normalized);
        if (key != null) {
            return key;
        }
        return invokeStatic(keyClass, "of", new Class<?>[]{String.class, String.class}, "default", normalized);
    }

    private static Optional<BlockData> invokeStaticBlockData(String className, String methodName, String id) {
        try {
            Class<?> clazz = Class.forName(className);
            Object value = invokeStatic(clazz, methodName, new Class<?>[]{String.class}, id);
            return coerceBlockData(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<BlockData> coerceBlockData(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof BlockData data) {
            return Optional.of(data);
        }
        if (value instanceof String text) {
            return createBlockData(text);
        }
        for (String method : new String[]{
                "blockData", "getBlockData",
                "bukkitBlockData", "getBukkitBlockData",
                "vanillaBlockData", "getVanillaBlockData",
                "state", "getState",
                "literal", "asString"
        }) {
            try {
                Optional<BlockData> data = coerceBlockData(invokeAny(value, method));
                if (data.isPresent()) {
                    return data;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        return createBlockData(value.toString());
    }

    private static Optional<BlockData> createBlockData(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String value = input.trim();
        int blockStart = value.indexOf("minecraft:");
        if (blockStart > 0) {
            value = value.substring(blockStart);
        }
        try {
            return Optional.of(Bukkit.createBlockData(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Object invokeStatic(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        try {
            Method method = clazz.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeAny(Object target, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static ProviderId providerId(String input) {
        String[] parts = input.split(":", 2);
        if (parts.length < 2) {
            return new ProviderId(null, input);
        }
        String provider = parts[0].toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "oraxen", "itemsadder", "ia", "nexo", "craftengine", "ce" -> new ProviderId(provider, parts[1]);
            default -> new ProviderId(null, input);
        };
    }

    private static String stripKnownNamespace(String id, String namespace) {
        String prefix = namespace + ":";
        return id.toLowerCase(Locale.ROOT).startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private record ProviderId(String provider, String id) {
    }
}
