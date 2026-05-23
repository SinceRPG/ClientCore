package net.danh.clientcore.block;

import java.util.List;

record BlockMiningConfig(
        String activeBlock,
        String visualMode,
        int defaultTimeTicks,
        BlockMiningFeedback feedback,
        List<BlockToolRule> tools
) {
    boolean enabled() {
        return defaultTimeTicks > 0 || !tools.isEmpty();
    }

    boolean blockDisplayOverlay() {
        return !"ACTIVE_BLOCK".equalsIgnoreCase(visualMode);
    }
}
