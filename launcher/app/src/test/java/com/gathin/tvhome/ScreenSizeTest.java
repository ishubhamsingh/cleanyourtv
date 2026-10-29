package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScreenSizeTest {

    @Test
    public void aFiftyFiveInch4kPanelAt80Dpi() {
        // The BRAVIA AE2 this was tried on: 3840 x 2160 at 80 dpi.
        float inches = ScreenSize.inches(3840, 2160, 80f, 80f);
        assertEquals(55.1f, inches, 0.2f);
        assertEquals(6, ScreenSize.columns(inches));
    }

    @Test
    public void sizesGiveColumns() {
        assertEquals(4, ScreenSize.columns(32));
        assertEquals(5, ScreenSize.columns(43));
        assertEquals(6, ScreenSize.columns(50));
        assertEquals(6, ScreenSize.columns(65));
        assertEquals(7, ScreenSize.columns(75));
    }

    @Test
    public void aDensityBucketIsNotAMeasurement() {
        assertEquals(0f, ScreenSize.inches(1920, 1080, 160f, 160f), 0f);
        assertEquals(0f, ScreenSize.inches(3840, 2160, 320f, 320f), 0f);
        assertEquals(ScreenSize.FALLBACK_COLUMNS, ScreenSize.columns(0));
    }

    @Test
    public void disagreeingOrImpossibleDensitiesAreUnknown() {
        assertEquals(0f, ScreenSize.inches(1920, 1080, 40f, 90f), 0f);       // x and y disagree
        assertEquals(0f, ScreenSize.inches(1920, 1080, 400f, 400f), 0f);     // a 5-inch "TV"
        assertEquals(0f, ScreenSize.inches(1920, 1080, 0f, 0f), 0f);
    }
}
