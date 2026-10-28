package com.gathin.tvhome;

import java.util.Locale;

/** The dashboard cards' numbers as text: track times, network speed, the Wi-Fi band, uptime and sizes. */
final class DashboardText {
    private DashboardText() {
    }

    private static final long KB = 1024, MB = KB * 1024, GB = MB * 1024;

    /** A track position or length: "1:32", "12:05", "1:02:05"; empty when unknown (negative). */
    static String clock(long ms) {
        if (ms < 0) return "";
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Bytes a second: "820 KB/s", "12.4 MB/s", "120 MB/s". */
    static String rate(double bytesPerSecond) {
        double b = Math.max(0, bytesPerSecond);
        if (b < MB) return String.format(Locale.ROOT, "%d KB/s", Math.round(b / KB));
        double mb = b / MB;
        return mb >= 100 ? String.format(Locale.ROOT, "%d MB/s", Math.round(mb))
                : String.format(Locale.ROOT, "%.1f MB/s", mb);
    }

    /** The Wi-Fi band for a channel frequency in MHz: "2.4 GHz", "5 GHz", "6 GHz"; empty for anything else. */
    static String band(int mhz) {
        if (mhz >= 2400 && mhz < 2500) return "2.4 GHz";
        if (mhz >= 4900 && mhz < 5925) return "5 GHz";
        if (mhz >= 5925 && mhz <= 7125) return "6 GHz";
        return "";
    }

    /** Time since boot, the two largest units: "3d 4h", "4h 12m", "12m". */
    static String uptime(long ms) {
        long m = Math.max(0, ms) / 60_000, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return m + "m";
    }

    /** Used of total in GB: "2.2 / 4.5 GB", "18.4 / 32 GB" (a whole total drops its ".0"). */
    static String usedOfTotal(long usedBytes, long totalBytes) {
        double used = Math.max(0, usedBytes) / (double) GB, total = Math.max(0, totalBytes) / (double) GB;
        String t = String.format(Locale.ROOT, "%.1f", total);
        if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
        return String.format(Locale.ROOT, "%.1f / %s GB", used, t);
    }

    /** used / total as 0..1000 for a progress bar; 0 when the total is unknown. */
    static int permille(long used, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(1000, Math.round(used * 1000.0 / total)));
    }
}
