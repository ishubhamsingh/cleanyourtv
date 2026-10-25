package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.junit.Test;

public class WatchmodeTest {

    private static final String SOURCES = "["
            + "{\"name\":\"Netflix\",\"type\":\"sub\",\"region\":\"US\",\"web_url\":\"https://www.netflix.com/title/1\"},"
            + "{\"name\":\"Amazon\",\"type\":\"rent\",\"region\":\"IN\",\"web_url\":\"https://www.primevideo.com/detail/2\"},"
            + "{\"name\":\"Netflix\",\"type\":\"sub\",\"region\":\"IN\",\"web_url\":\"https://www.netflix.com/title/80994082\"},"
            + "{\"name\":\"JioHotstar\",\"type\":\"free\",\"region\":\"IN\",\"web_url\":\"http://insecure.example/3\"}"
            + "]";

    @Test
    public void theWantedServicesOfferInTheCountryWins() throws Exception {
        assertEquals("https://www.netflix.com/title/80994082",
                Watchmode.pick(new JSONArray(SOURCES), "IN", name -> name.equals("Netflix")));
    }

    @Test
    public void rentalsOtherServicesAndPlainHttpAreLeftOut() throws Exception {
        assertNull(Watchmode.pick(new JSONArray(SOURCES), "IN", name -> name.equals("Amazon")));
        assertNull(Watchmode.pick(new JSONArray(SOURCES), "IN", name -> name.equals("JioHotstar")));
        assertNull(Watchmode.pick(new JSONArray(SOURCES), "GB", name -> true));
        assertNull(Watchmode.pick(null, "IN", name -> true));
    }
}
