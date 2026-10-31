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
        String spotify = "com.spotify.tv.android", netflix = "com.netflix.ninja", home = "com.gathin.tvhome";
        // What the app says wins: Spotify's music in front or behind, Netflix's movie never.
        assertTrue(Rule.isMusic(Rule.CONTENT_MUSIC, spotify, spotify));
        assertFalse(Rule.isMusic(Rule.CONTENT_MOVIE, netflix, home));
        assertFalse(Rule.isMusic(Rule.CONTENT_SPEECH, "podcasts", home));
        // An app that doesn't say: music only while it plays behind another app.
        assertFalse(Rule.isMusic(0, netflix, netflix));
        assertTrue(Rule.isMusic(0, spotify, home));
        assertFalse(Rule.isMusic(0, null, home));
    }
}
