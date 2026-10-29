package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.junit.Test;

public class DashboardCardsTest {

    @Test
    public void knownCardsInTheRowsOrderAndTyposDropped() {
        Set<String> cards = DashboardCards.of(Arrays.asList("screen_time", "wether", "now_playing"));
        assertEquals(Arrays.asList("now_playing", "screen_time"), Arrays.asList(cards.toArray()));
    }

    @Test
    public void anEmptyListIsNoCards() {
        assertTrue(DashboardCards.of(Collections.<String>emptyList()).isEmpty());
        assertTrue(DashboardCards.split("").isEmpty());
    }

    @Test
    public void storedOnTheTvAndReadBack() {
        Set<String> cards = DashboardCards.of(Arrays.asList("system", "weather"));
        assertEquals("weather,system", DashboardCards.join(cards));
        assertEquals(cards, DashboardCards.split(DashboardCards.join(cards)));
    }
}
