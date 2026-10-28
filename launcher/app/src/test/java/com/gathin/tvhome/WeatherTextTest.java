package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class WeatherTextTest {
    @Test
    public void codesHaveNames() {
        assertEquals("Clear", WeatherText.word(0));
        assertEquals("Partly cloudy", WeatherText.word(2));
        assertEquals("Rain", WeatherText.word(63));
        assertEquals("Snow", WeatherText.word(73));
        assertEquals("Thunderstorm", WeatherText.word(95));
    }

    @Test
    public void aClearNightIsTheMoon() {
        assertNotEquals(WeatherText.symbol(0, true), WeatherText.symbol(0, false));
    }

    @Test
    public void hoursFollowTheClock() {
        assertEquals("9 PM", WeatherText.hour("21:00", false));
        assertEquals("12 AM", WeatherText.hour("00:00", false));
        assertEquals("12 PM", WeatherText.hour("12:00", false));
        assertEquals("21", WeatherText.hour("21:00", true));
        assertEquals("18°", WeatherText.degrees(17.6));
    }

    @Test
    public void hourLabelsNameTheHalfOfTheDayOnlyWhenItChanges() {
        org.junit.Assert.assertArrayEquals(new String[]{"9 PM", "10", "11", "12 AM", "1"},
                WeatherText.hourLabels(new String[]{"21:00", "22:00", "23:00", "00:00", "01:00"}, false));
        org.junit.Assert.assertArrayEquals(new String[]{"21", "22"},
                WeatherText.hourLabels(new String[]{"21:00", "22:00"}, true));
    }
}
