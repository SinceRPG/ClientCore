package net.danh.clientcore.block;

import java.util.List;

record BlockFarmingConfig(
        boolean enabled,
        List<BlockToolRule> tools,
        List<BlockFarmingStage> stages
) {
    public boolean enabled() {
        return enabled;
    }
}
