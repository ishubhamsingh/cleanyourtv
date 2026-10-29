package com.gathin.tvhome;

/** Now playing's "Up next": the queue item after the one playing, from a media session's queue. */
final class UpNext {
    private UpNext() {
    }

    /** The playing item's id is unknown (PlaybackState.UNKNOWN_ID). */
    static final long UNKNOWN = -1;

    /**
     * "Title · Artist" of the item after {@code activeId} in the queue ({@code ids}, {@code titles}, {@code subtitles}
     * in queue order), or "" when there is none: no queue, the last item playing, or an app that does not say which
     * item plays (guessing the first would be wrong as often as right).
     */
    static String after(long[] ids, String[] titles, String[] subtitles, long activeId) {
        if (ids == null || activeId == UNKNOWN) return "";
        for (int i = 0; i + 1 < ids.length; i++) {
            if (ids[i] != activeId) continue;
            String title = titles[i + 1] == null ? "" : titles[i + 1].trim();
            String sub = subtitles[i + 1] == null ? "" : subtitles[i + 1].trim();
            if (title.isEmpty()) return "";
            return sub.isEmpty() ? title : title + "  ·  " + sub;
        }
        return "";
    }
}
