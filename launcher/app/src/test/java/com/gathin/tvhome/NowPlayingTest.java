package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NowPlayingTest {
    // PlaybackState constants: NONE 0, STOPPED 1, PAUSED 2, PLAYING 3, BUFFERING 6, ERROR 7.

    @Test
    public void aPlayingSessionBeatsAMoreRecentlyPausedOne() {
        assertEquals(1, NowPlaying.choose(new int[]{2, 3}, new long[]{900, 100}));
    }

    @Test
    public void amongPausedSessionsTheMostRecentWins() {
        assertEquals(0, NowPlaying.choose(new int[]{2, 2}, new long[]{500, 100}));
    }

    @Test
    public void bufferingCountsAsPlaying() {
        assertEquals(1, NowPlaying.choose(new int[]{2, 6}, new long[]{900, 1}));
    }

    @Test
    public void stoppedNoneAndErrorShowNothing() {
        assertEquals(-1, NowPlaying.choose(new int[]{0, 1, 7}, new long[]{1, 2, 3}));
        assertEquals(-1, NowPlaying.choose(new int[0], new long[0]));
    }

    @Test
    public void progressMovesOnWhilePlayingAndStopsWhilePaused() {
        // 30 s into a 120 s track, reported at t=1000; it is now t=31000.
        assertEquals(0.5f, NowPlaying.fraction(30_000, 1_000, 1f, 120_000, true, 31_000), 1e-6);
        assertEquals(0.25f, NowPlaying.fraction(30_000, 1_000, 1f, 120_000, false, 31_000), 1e-6);
    }

    @Test
    public void progressStaysInsideTheTrackAndIsUnknownWithoutALength() {
        assertEquals(1f, NowPlaying.fraction(110_000, 1_000, 1f, 120_000, true, 60_000), 1e-6);
        assertEquals(-1f, NowPlaying.fraction(30_000, 1_000, 1f, 0, true, 31_000), 1e-6);
    }
}
