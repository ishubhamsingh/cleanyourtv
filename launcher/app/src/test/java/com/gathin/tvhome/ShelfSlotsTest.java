package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShelfSlotsTest {

    @Test
    public void theBlockAndItsSpacersTakeExactlyTwoRows() {
        for (int columns = 2; columns <= 8; columns++) {
            int used = 0;
            for (int p = columns; p < ShelfSlots.gridStart(columns); p++) used += ShelfSlots.span(p, columns);
            assertEquals("columns " + columns, 2 * columns, used);
        }
    }

    @Test
    public void appsAfterTheBlockKeepTheirColumns() {
        int columns = 4;
        int start = ShelfSlots.gridStart(columns);
        assertEquals(8, start);
        assertEquals(0, start % columns);
        assertEquals(1, ShelfSlots.span(start, columns));
    }

    @Test
    public void theBlockSpansTheRowAndTheLastSpacerIsTwoWide() {
        assertEquals(4, ShelfSlots.span(4, 4));
        assertEquals(1, ShelfSlots.span(5, 4));
        assertEquals(1, ShelfSlots.span(6, 4));
        assertEquals(2, ShelfSlots.span(7, 4));
    }

    @Test
    public void spacersAreOnlyBetweenTheBlockAndTheGrid() {
        assertFalse(ShelfSlots.isSpacer(3, 4));
        assertFalse(ShelfSlots.isSpacer(4, 4));
        assertTrue(ShelfSlots.isSpacer(5, 4));
        assertTrue(ShelfSlots.isSpacer(7, 4));
        assertFalse(ShelfSlots.isSpacer(8, 4));
    }

    @Test
    public void aRowThatFitsParksWhereTheDockSits() {
        // row 1000..1300 parks its top at 600: bottom 900 fits a 1000-high grid with 30 kept free.
        assertEquals(400, ShelfSlots.parkOffset(1000, 1300, 600, 1000, 30));
    }

    @Test
    public void aTallerRowParksHigherSoItsBottomStaysOnScreen() {
        // parked at 600 the bottom would be 1000, past 970: 30 more.
        assertEquals(430, ShelfSlots.parkOffset(1000, 1400, 600, 1000, 30));
    }
}
