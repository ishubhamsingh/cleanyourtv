package com.gathin.tvhome.discover;

/**
 * The owner's TMDB account (optional, one-time approval: tmdb_session and tmdb_account in discover.properties), so
 * what is taught on the TV is kept in the cloud: More like this adds the title to the account's Favourites, Not
 * interested rates it LOW_RATING. Every rebuild reads the Favourites and ratings back (Catalog.syncAccount), so a
 * reinstall, another TV, or a rating made on themoviedb.org teaches Discover too.
 */
final class Account {
    /** The rating Not interested gives a title on TMDB (out of 10). */
    static final double LOW_RATING = 2.0;

    final String session, id;

    Account(String session, String id) {
        this.session = session == null ? "" : session.trim();
        this.id = id == null ? "" : id.trim();
    }

    boolean configured() {
        return !session.isEmpty() && !id.isEmpty();
    }

    /** What a rating on TMDB teaches: 8 and up a like, 6 to 7.5 an open, 4.5 to 5.5 nothing, 4 and under a dislike. */
    static double weightForRating(double rating) {
        if (rating >= 8) return Taste.LIKE;
        if (rating >= 6) return Taste.OPEN;
        if (rating > 4) return 0;
        return Taste.DISLIKE;
    }

    /** A rating of 4 or under also hides the title, as Not interested does. */
    static boolean hiddenForRating(double rating) {
        return rating <= 4;
    }
}
