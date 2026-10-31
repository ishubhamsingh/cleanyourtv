package com.gathin.tvhome.backlight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleTest {
    private static final String TVLIN = "com.sony.dtv.tvlin";

    @Test
    public void theChosenInputSyncsAndEverythingElseIsTheScene() {
        Rule r = new Rule(TVLIN, "HDMI 4");
        r.sawInput("HDMI 4");
        assertEquals(Rule.Mode.SYNC, r.decide(TVLIN));
        assertEquals(Rule.Mode.OTHER, r.decide("com.netflix.ninja"));
        assertEquals(Rule.Mode.OTHER, r.decide("com.gathin.tvhome"));
        r.sawInput("HDMI 2");
        assertEquals(Rule.Mode.OTHER, r.decide(TVLIN));
    }

    @Test
    public void backOnTheInputWithoutABannerKeepsItsMode() {
        Rule r = new Rule(TVLIN, "HDMI 4");
        r.sawInput("HDMI 4");
        assertEquals(Rule.Mode.OTHER, r.decide("com.android.tv.settings"));
        r.sawInput(null);
        r.sawInput("  ");
        assertEquals(Rule.Mode.SYNC, r.decide(TVLIN));
    }

    @Test
    public void nothingIsSentBeforeTheInputIsKnown() {
        Rule r = new Rule(TVLIN, "HDMI 4");
        assertNull(r.decide(TVLIN));
        assertNull(r.decide(null));
    }

    @Test
    public void namesMatchAsWholeWordsIgnoringCase() {
        Rule r = new Rule(TVLIN, "HDMI 1, Apple TV");
        assertTrue(r.isSync("HDMI 1"));
        assertTrue(r.isSync("hdmi 1 / ARC"));
        assertTrue(r.isSync("Apple TV"));
        assertFalse(r.isSync("HDMI 10"));
        assertFalse(r.isSync("HDMI 4"));
    }

    @Test
    public void onlyFullScreenWindowsCount() {
        assertTrue(Rule.fullScreen(1920, 1080, 1920, 1080));
        assertFalse(Rule.fullScreen(640, 1080, 1920, 1080));     // a settings side panel
        assertFalse(Rule.fullScreen(1920, 120, 1920, 1080));     // the volume bar
    }

    @Test
    public void albumColoursAreForMusicNotVideo() {
        String spotify = "com.spotify.tv.android", netflix = "com.netflix.ninja", youtube = "com.google.android.youtube.tv",
                home = "com.gathin.tvhome";
        // As on Google TV 14, where every app says "unknown": a music app counts in front or behind.
        assertTrue(Rule.isMusic(0, spotify, spotify));
        assertTrue(Rule.isMusic(0, spotify, home));
        // Video apps play in front: not music.
        assertFalse(Rule.isMusic(0, netflix, netflix));
        assertFalse(Rule.isMusic(0, youtube, youtube));
        // Any other app behind Home: music.
        assertTrue(Rule.isMusic(0, "some.radio.app", home));
        assertFalse(Rule.isMusic(0, null, home));
        // An app that says what it plays is taken at its word.
        assertTrue(Rule.isMusic(Rule.CONTENT_MUSIC, "some.app", "some.app"));
        assertFalse(Rule.isMusic(Rule.CONTENT_MOVIE, netflix, home));
        assertFalse(Rule.isMusic(Rule.CONTENT_SPEECH, "podcasts", home));
    }
}
