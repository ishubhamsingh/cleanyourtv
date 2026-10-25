package com.gathin.tvhome;

/**
 * Where Discover's block sits in the app grid, as plain numbers: right after the dock (position dockSize) with
 * dockSize - 1 empty spacers after it, so the grid restarts at 2 * dockSize and every app keeps its column
 * (position % columns). The block takes a whole row; the spacers fill the next one exactly, the last of them two wide.
 */
final class ShelfSlots {
    private ShelfSlots() {
    }

    /** The first grid position after the block. */
    static int gridStart(int dockSize) {
        return 2 * dockSize;
    }

    /** The spacers between the block and the grid (not the block itself, not the dock): nothing there is an app. */
    static boolean isSpacer(int position, int dockSize) {
        return position > dockSize && position < gridStart(dockSize);
    }

    /** Grid span of a position, with the block at dockSize. */
    static int span(int position, int dockSize) {
        if (position == dockSize) return dockSize;
        if (dockSize > 1 && position == gridStart(dockSize) - 1) return 2;
        return 1;
    }
}
