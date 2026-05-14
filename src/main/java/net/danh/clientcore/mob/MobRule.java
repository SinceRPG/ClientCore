package net.danh.clientcore.mob;

import java.util.List;

record MobRule(
        String id,
        int priority,
        String condition,
        List<String> conditions,
        List<MobVariant> variants
) {
}
