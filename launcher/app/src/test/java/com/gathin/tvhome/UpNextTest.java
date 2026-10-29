package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpNextTest {
    private static final long[] IDS = {10, 11, 12};
    private static final String[] TITLES = {"One", "Two", "Three"};
    private static final String[] ARTISTS = {"A", "B", ""};

    @Test
    public void theItemAfterThePlayingOne() {
        assertEquals("Two  ·  B", UpNext.after(IDS, TITLES, ARTISTS, 10));
        assertEquals("Three", UpNext.after(IDS, TITLES, ARTISTS, 11));     // no artist: the title alone
    }

    @Test
    public void nothingAfterTheLastOrWithoutAQueue() {
        assertEquals("", UpNext.after(IDS, TITLES, ARTISTS, 12));
        assertEquals("", UpNext.after(null, null, null, 10));
        assertEquals("", UpNext.after(new long[0], new String[0], new String[0], 10));
    }

    @Test
    public void anAppThatDoesNotSayWhatPlaysGetsNoGuess() {
        assertEquals("", UpNext.after(IDS, TITLES, ARTISTS, UpNext.UNKNOWN));
        assertEquals("", UpNext.after(IDS, TITLES, ARTISTS, 99));
    }
}
