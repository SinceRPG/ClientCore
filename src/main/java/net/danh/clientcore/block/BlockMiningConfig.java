package net.danh.clientcore.block;

import java.util.List;

record BlockMiningConfig(
        String activeBlock,
        int defaultTimeTicks,
        BlockMiningFeedback feedback,
        List<BlockToolRule> tools
) {
    boolean enabled() {
        return defaultTimeTicks > 0 || !tools.isEmpty();
    }
}
