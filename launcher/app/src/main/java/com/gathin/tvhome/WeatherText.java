package com.gathin.tvhome;

import java.util.Locale;

/** The dashboard's Weather card in words: a WMO weather code's name and symbol, and an hour as the TV writes it. */
final class WeatherText {
    private WeatherText() {
    }

    /** The condition's English name (the add-on's own page names them the same way). */
    static String word(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    /** A symbol for the condition; the moon for a clear night. */
    static String symbol(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 95) return "⛈️";
        return "";
    }

    /** "21:00" as the TV writes hours: "21" with a 24-hour clock, else "9 PM" (and "12 AM" for midnight). */
    static String hour(String hhmm, boolean twentyFour) {
        int h;
        try {
            h = Integer.parseInt(hhmm.substring(0, hhmm.indexOf(':')));
        } catch (RuntimeException e) {
            return hhmm;
        }
        if (twentyFour) return String.format(Locale.ROOT, "%02d", h);
        return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM");
    }

    /**
     * Labels for consecutive hours, short enough for five across a card: the first with AM or PM, the rest the bare
     * hour until the half of the day changes ("9 PM", "10", "11", "12 AM", "1"); 24-hour clocks are short already.
     */
    static String[] hourLabels(String[] hhmm, boolean twentyFour) {
        String[] out = new String[hhmm.length];
        String lastHalf = null;
        for (int i = 0; i < hhmm.length; i++) {
            String full = hour(hhmm[i], twentyFour);
            if (twentyFour) {
                out[i] = full;
                continue;
            }
            int space = full.indexOf(' ');
            String half = space < 0 ? "" : full.substring(space + 1);
            out[i] = half.equals(lastHalf) ? full.substring(0, space) : full;
            lastHalf = half;
        }
        return out;
    }

    /** Whole degrees: "18°". */
    static String degrees(double c) {
        return Math.round(c) + "°";
    }
}
