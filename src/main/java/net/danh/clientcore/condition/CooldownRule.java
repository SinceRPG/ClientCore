package net.danh.clientcore.condition;

import java.util.List;

public record CooldownRule(
        String condition,
        List<String> conditions,
        long durationTicks
) {
}