package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ServicesTest {

    private static String label(String tmdbName) {
        String[] s = Services.find(tmdbName);
        return s == null ? null : s[1];
    }

    @Test
    public void tmdbNamesInIndiaMatchTheirApps() {
        assertEquals("Netflix", label("Netflix"));
        assertEquals("Netflix", label("Netflix Standard with Ads"));
        assertEquals("Prime Video", label("Amazon Prime Video"));
        assertEquals("Prime Video", label("Amazon Prime Video with Ads"));
        assertEquals("JioHotstar", label("JioHotstar"));
        assertEquals("JioHotstar", label("Disney Plus Hotstar"));
        assertEquals("Apple TV", label("Apple TV+"));
        assertEquals("Apple TV", label("Apple TV"));
        assertEquals("SonyLIV", label("Sony Liv"));
        assertEquals("SonyLIV", label("SonyLIV"));
        assertEquals("ZEE5", label("Zee5"));
        assertEquals("MX Player", label("Amazon MX Player"));
        assertEquals("aha", label("aha"));
    }

    @Test
    public void unknownOrPartialNamesDoNotMatch() {
        assertNull(label("Shahid VIP"));
        assertNull(label("Mubi"));
        assertNull(label(""));
        assertNull(label(null));
    }

    @Test
    public void normalizeDropsSpacesDotsAndPlus() {
        assertEquals("appletv", Services.normalize("Apple TV+"));
        assertEquals("disneyplushotstar", Services.normalize("Disney Plus Hotstar"));
    }
}
