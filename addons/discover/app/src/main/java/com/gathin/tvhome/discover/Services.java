package com.gathin.tvhome.discover;

import java.util.Locale;

/**
 * Streaming services as TMDB names them (from JustWatch, per country) and the Android TV apps that play them. The
 * table holds the names that don't match the app's label plainly (TMDB's "Amazon Prime Video" is the app
 * "Prime Video"); any other service is matched to an installed app by its label (labelMatches), so an app installed
 * later works without a change here. A table name matches when it contains the service's key once spaces, dots and "+" are dropped, so
 * "Amazon Prime Video with Ads" is Prime Video and "Sony Liv" is SonyLIV. Adapted from Free My TV's Recs (MIT).
 */
final class Services {
    private Services() {
    }

    /** {match key, label shown on the TV, package...}: the first installed package plays the service. */
    static final String[][] ALL = {
        {"netflix", "Netflix", "com.netflix.ninja"},
        {"prime", "Prime Video", "com.amazon.amazonvideo.livingroom"},   // TMDB "Amazon Prime Video", Watchmode "Amazon Prime"
        {"hotstar", "JioHotstar", "in.startv.hotstar"},          // TMDB: "JioHotstar" (earlier "Disney Plus Hotstar")
        {"appletv", "Apple TV", "com.apple.atve.sony.appletv", "com.apple.atve.androidtv.appletv"},
        {"sonyliv", "SonyLIV", "com.sonyliv"},
        {"zee5", "ZEE5", "com.graymatrix.did"},
        {"crunchyroll", "Crunchyroll", "com.crunchyroll.crunchyroid"},
        {"mxplayer", "MX Player", "com.mxtech.videoplayer.television"},
        {"disneyplus", "Disney+", "com.disney.disneyplus"},
        {"youtubepremium", "YouTube", "com.google.android.youtube.tv"},
    };

    /**
     * A TMDB service name and an app's label on the TV name the same service: equal once normalized, or one holds
     * the other ("Lionsgate Play" and "Lionsgate Play: Movies"), but only for names of 4 characters or more, so a
     * label like "TV" never matches. Resellers ("Lionsgate Play Amazon Channel") are not the app: they never match.
     */
    static boolean labelMatches(String tmdbName, String appLabel) {
        String n = normalize(tmdbName), l = normalize(appLabel);
        if (n.isEmpty() || l.isEmpty() || isChannel(n)) return false;
        if (n.equals(l)) return true;
        return n.length() >= 4 && l.length() >= 4 && (n.contains(l) || l.contains(n));
    }

    /** "X Amazon Channel", "X Apple TV Channel", "X Roku Premium Channel": sold inside another app. */
    static boolean isChannel(String normalized) {
        return normalized.contains("channel");
    }

    /** TMDB's name with case, spaces, dots, "+" and "-" removed: "Amazon Prime Video" -> "amazonprimevideo". */
    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s.+\\-]", "");
    }

    /** The ALL row for a TMDB provider name, or null for a service no TV app here plays. */
    static String[] find(String tmdbName) {
        String n = normalize(tmdbName);
        if (n.isEmpty() || isChannel(n)) return null;
        for (String[] s : ALL) if (n.contains(s[0])) return s;
        return null;
    }
}
