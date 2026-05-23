package net.danh.clientcore.block;

record BlockMiningFeedback(
        boolean display,
        boolean actionBar,
        boolean particles,
        boolean sounds,
        int intervalTicks,
        String message,
        String displayFormat,
        int barLength,
        String lowColor,
        String midColor,
        String highColor,
        String emptyColor,
        int backgroundArgb
) {
}
