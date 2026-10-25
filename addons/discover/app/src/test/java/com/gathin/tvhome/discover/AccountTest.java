package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountTest {

    @Test
    public void ratingsTeachLikesOpensNothingOrDislikes() {
        assertEquals(Taste.LIKE, Account.weightForRating(9), 0);
        assertEquals(Taste.LIKE, Account.weightForRating(8), 0);
        assertEquals(Taste.OPEN, Account.weightForRating(7), 0);
        assertEquals(0, Account.weightForRating(5), 0);
        assertEquals(Taste.DISLIKE, Account.weightForRating(Account.LOW_RATING), 0);
    }

    @Test
    public void notInterestedsOwnRatingHidesTheTitleAgainAfterAReinstall() {
        assertTrue(Account.hiddenForRating(Account.LOW_RATING));
        assertTrue(Account.hiddenForRating(4));
        assertFalse(Account.hiddenForRating(5));
    }

    @Test
    public void anAccountNeedsBothTheSessionAndTheId() {
        assertFalse(new Account("", "123").configured());
        assertFalse(new Account("abc", null).configured());
        assertTrue(new Account("abc", "123").configured());
    }
}
