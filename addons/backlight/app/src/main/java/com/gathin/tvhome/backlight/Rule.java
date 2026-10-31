package com.gathin.tvhome.backlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which mode the light should be in for what is on screen. The TV's input app (Sony: com.sony.dtv.tvlin) names the
 * input in a banner for a few seconds after each switch; the last name it showed is kept, so returning to the input
 * app without a banner (after a settings panel, say) keeps its mode. Any other full-screen app is the TV's own OS.
 */
final class Rule {
    enum Mode { SYNC, OTHER }

    private final String inputApp;
    private final List<Pattern> syncInputs = new ArrayList<>();
    private String lastInput;

    Rule(String inputApp, String syncInputsCsv) {
        this.inputApp = inputApp;
        for (String s : syncInputsCsv.split(",")) {
            String t = s.trim();
            // "HDMI 1" must not match "HDMI 10": the name only as a whole word.
            if (!t.isEmpty()) syncInputs.add(Pattern.compile("(^|\\W)" + Pattern.quote(t.toLowerCase(Locale.ROOT)) + "($|\\W)"));
        }
    }

    String inputApp() {
        return inputApp;
    }

    String lastInput() {
        return lastInput;
    }

    /** The input app's banner was read. */
    void sawInput(String name) {
        if (name != null && !name.trim().isEmpty()) lastInput = name.trim();
    }

    boolean isSync(String inputName) {
        String n = inputName.toLowerCase(Locale.ROOT);
        for (Pattern p : syncInputs) if (p.matcher(n).find()) return true;
        return false;
    }

    /** The mode for the full-screen app in front; null to leave the light as it is (nothing known yet). */
    Mode decide(String frontPackage) {
        if (frontPackage == null) return null;
        if (!frontPackage.equals(inputApp)) return Mode.OTHER;
        if (lastInput == null) return null;
        return isSync(lastInput) ? Mode.SYNC : Mode.OTHER;
    }

    /**
     * Whether a playing media session is music, for the album colours. A music app always is. Otherwise what the app
     * says it plays (AudioAttributes' content type), though on Google TV 14 every app says "unknown"; and failing
     * that, only while it plays behind another app: video apps play only in front (Netflix and YouTube send a poster
     * or thumbnail as artwork too).
     */
    static boolean isMusic(int contentType, String sessionPackage, String frontPackage) {
        if (sessionPackage != null && MUSIC_APPS.contains(sessionPackage)) return true;
        if (contentType == CONTENT_MUSIC) return true;
        if (contentType == CONTENT_MOVIE || contentType == CONTENT_SPEECH) return false;
        return sessionPackage != null && !sessionPackage.equals(frontPackage);
    }

    /** Music apps on Android TV (and their phone builds, for TVs that run those). */
    static final java.util.Set<String> MUSIC_APPS = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.spotify.tv.android", "com.spotify.music", "com.google.android.youtube.tvmusic",
            "com.google.android.apps.youtube.music", "com.apple.android.music", "com.amazon.mp3",
            "com.jio.media.jiobeats", "com.bsbportal.music", "com.gaana", "com.aspiro.tidal", "deezer.android.tv",
            "deezer.android.app", "com.soundcloud.android"));

    /** AudioAttributes.CONTENT_TYPE_SPEECH, _MUSIC and _MOVIE, here so the tests run on a plain JVM. */
    static final int CONTENT_SPEECH = 1, CONTENT_MUSIC = 2, CONTENT_MOVIE = 3;

    /** A window that covers (nearly) the whole screen: an app, not a side panel or the volume bar. */
    static boolean fullScreen(int width, int height, int screenWidth, int screenHeight) {
        return width >= screenWidth * 0.9 && height >= screenHeight * 0.9;
    }
}
