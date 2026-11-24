package tv.myliststv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A My Lists channel (a mixed list such as the Arrowverse timeline) is a series whose videos are real titles. */
class StreamTypeTest {
    @Test fun anEpisodeOfAMixedListIsAskedForAsASeries() = assertEquals("series", StreamResolver.typeFor("series", "channel_arrowverse", "tt3107288:1:1"))
    @Test fun aMovieInAMixedListIsAskedForAsAMovie() = assertEquals("movie", StreamResolver.typeFor("series", "channel_arrowverse", "tt4532038"))
    @Test fun aPlainSeriesAndAPlainMovieKeepTheirType() {
        assertEquals("series", StreamResolver.typeFor("series", "tt0944947", "tt0944947:1:1"))
        assertEquals("series", StreamResolver.typeFor("series", "tt0944947", "tt0944947"))
        assertEquals("movie", StreamResolver.typeFor("movie", "tt0111161", "tt0111161"))
    }
}
