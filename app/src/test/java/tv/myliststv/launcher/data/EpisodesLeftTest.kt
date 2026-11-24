package tv.myliststv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodesLeftTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L   // 2027-01-15 08:00 UTC
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    private val midnight = Math.floorDiv(now, day) * day
    private fun iso(ms: Long) = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))
    private fun ep(s: Int, e: Int, at: Long) = Video("tt1:$s:$e", "t", s, e, released = iso(at))
    private fun entry(s: Int, e: Int, dur: Long = 0, pos: Long = 0) = WatchStore.Entry("tt1", "series", "Show", null, null, "tt1:$s:$e", s, e, null, pos, dur, 0)

    @Test fun countsAiredEpisodesFromTheOneYouAreOnAndLeavesOutSpecialsAndFutureOnes() {
        val v = listOf(ep(0, 1, now - 9 * day), ep(1, 1, now - 8 * day), ep(1, 2, now - 7 * day), ep(1, 3, now - day), ep(1, 4, now + 5 * day))
        assertEquals(EpisodesLeft.Info(2, null), EpisodesLeft.compute(v, entry(1, 2), now, utc))
        assertEquals(EpisodesLeft.Info(3, null), EpisodesLeft.compute(v, entry(1, 1), now, utc))
    }

    @Test fun whenTheNextOneIsNotOutYetNothingIsLeftAndItSaysWhenItAirs() {
        val v = listOf(ep(1, 1, now - 8 * day), ep(1, 2, now + 3 * day - 1000))
        assertEquals(EpisodesLeft.Info(0, "Airs in 3 days"), EpisodesLeft.compute(v, entry(1, 2), now, utc))
    }

    @Test fun airsTextCountsCalendarDays() {
        assertEquals("Airs today", EpisodesLeft.airsText(midnight, now, utc))
        assertEquals("Airs tomorrow", EpisodesLeft.airsText(midnight + day, now, utc))
        assertEquals("Airs in 2 days", EpisodesLeft.airsText(midnight + 2 * day, now, utc))
    }

    @Test fun unknownEpisodeGivesNoAnswer() = assertNull(EpisodesLeft.compute(listOf(ep(1, 1, now - day)), entry(2, 9), now, utc))

    @Test fun timeLeftIsRoundedUpAndNeedsALength() {
        assertEquals("45 min left", EpisodesLeft.timeLeft(entry(1, 1, dur = 50 * 60_000L, pos = 5 * 60_000L)))
        assertEquals("1h 5m left", EpisodesLeft.timeLeft(entry(1, 1, dur = 70 * 60_000L, pos = 5 * 60_000L)))
        assertNull(EpisodesLeft.timeLeft(entry(1, 1)))
        assertEquals("1 episode left", EpisodesLeft.leftText(1))
    }
}
