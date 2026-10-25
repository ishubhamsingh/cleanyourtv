package com.gathin.tvhome.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class TitleTest {

    @Test
    public void aTrendingMovieIsReadFromTmdb() throws Exception {
        JSONObject o = new JSONObject("{\"id\":550,\"title\":\"Fight Club\",\"release_date\":\"1999-10-15\","
                + "\"backdrop_path\":\"/b.jpg\",\"poster_path\":null,\"vote_average\":8.4,\"genre_ids\":[18,53]}");
        Title t = Title.from(o, "movie");
        assertEquals("movie:550", t.key());
        assertEquals("Fight Club", t.title);
        assertEquals("1999", t.year);
        assertEquals("/b.jpg", t.backdrop);
        assertEquals("", t.poster);
        assertEquals(Arrays.asList(18, 53), t.genreIds);
    }

    @Test
    public void aSearchResultTakesItsKindFromMediaTypeAndPeopleAreSkipped() throws Exception {
        Title show = Title.from(new JSONObject("{\"media_type\":\"tv\",\"id\":1399,\"name\":\"Game of Thrones\","
                + "\"first_air_date\":\"2011-04-17\"}"), null);
        assertEquals("tv:1399", show.key());
        assertEquals("2011", show.year);
        assertNull(Title.from(new JSONObject("{\"media_type\":\"person\",\"id\":1,\"name\":\"Someone\"}"), null));
        assertNull(Title.from(new JSONObject("{\"media_type\":\"movie\",\"id\":0,\"title\":\"\"}"), null));
    }

    @Test
    public void whatIsSavedComesBackTheSame() throws Exception {
        Title t = Title.from(new JSONObject("{\"id\":7,\"name\":\"Show\",\"backdrop_path\":\"/x.jpg\","
                + "\"genre_ids\":[80]}"), "tv");
        t.pkg = "com.netflix.ninja";
        t.app = "Netflix";
        t.elsewhere.add("Sun NXT");
        t.meta = "Series · Crime";
        Title back = Title.fromJson(t.toJson());
        assertEquals(t.key(), back.key());
        assertEquals("com.netflix.ninja", back.pkg);
        assertEquals("Netflix", back.app);
        assertEquals(Arrays.asList("Sun NXT"), back.elsewhere);
        assertEquals(Arrays.asList(80), back.genreIds);
        assertEquals("Series · Crime", back.meta);
    }

    @Test
    public void pairedTvGenresReadAsOneWord() {
        assertEquals("Action", Catalog.shortGenre("Action & Adventure"));
        assertEquals("Sci-Fi", Catalog.shortGenre("Science Fiction"));
        assertEquals("Drama", Catalog.shortGenre("Drama"));
    }

    @Test
    public void datesAreTmdbsFormat() {
        String today = Catalog.day(0);
        assertEquals(10, today.length());
        assertEquals('-', today.charAt(4));
        assertEquals('-', today.charAt(7));
    }
}
