package com.gathin.tvhome.discover;

import java.util.Locale;

/**
 * Streaming services as TMDB names them (from JustWatch, per country) and the Android TV apps that play them.
 * A TMDB name matches when it contains the service's key once spaces, dots and "+" are dropped, so
 * "Amazon Prime Video with Ads" is Prime Video and "Sony Liv" is SonyLIV. Adapted from Free My TV's Recs (MIT).
 */
final class Services {
    private Services() {
    }

    /** {match key, label shown on the TV, package...}: the first installed package plays the service. */
    static final String[][] ALL = {
        {"netflix", "Netflix", "com.netflix.ninja"},
        {"primevideo", "Prime Video", "com.amazon.amazonvideo.livingroom"},
        {"hotstar", "JioHotstar", "in.startv.hotstar"},          // TMDB: "JioHotstar" (earlier "Disney Plus Hotstar")
        {"appletv", "Apple TV", "com.apple.atve.sony.appletv", "com.apple.atve.androidtv.appletv"},
        {"sonyliv", "SonyLIV", "com.sonyliv"},
        {"zee5", "ZEE5", "com.graymatrix.did"},
        {"crunchyroll", "Crunchyroll", "com.crunchyroll.crunchyroid"},
        {"mxplayer", "MX Player", "com.mxtech.videoplayer.television"},
        {"disneyplus", "Disney+", "com.disney.disneyplus"},
        {"youtubepremium", "YouTube", "com.google.android.youtube.tv"},
        {"lionsgateplay", "Lionsgate Play", "com.lionsgateplay.videoapp"},
        {"sunnxt", "Sun NXT", "com.suntv.sunnxt"},
        {"aha", "aha", "ahaflix.tv"},
        {"hoichoi", "hoichoi", "com.viewlift.hoichoi"},
    };

    /** TMDB's name with case, spaces, dots, "+" and "-" removed: "Amazon Prime Video" -> "amazonprimevideo". */
    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s.+\\-]", "");
    }

    /** The ALL row for a TMDB provider name, or null for a service no TV app here plays. */
    static String[] find(String tmdbName) {
        String n = normalize(tmdbName);
        if (n.isEmpty()) return null;
        for (String[] s : ALL) {
            // "aha" is a whole name, not a part of one ("Shahid" is not aha).
            if (s[0].equals("aha") ? n.equals("aha") || n.startsWith("ahavideo") : n.contains(s[0])) return s;
        }
        return null;
    }
}
