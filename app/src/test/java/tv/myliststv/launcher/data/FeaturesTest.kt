package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class FeaturesTest {
    @Test fun safeModeNeedsThreeCrashesInTwoMinutes() {
        val now = 1_000_000L
        assertFalse(CrashGuard.isSafe(listOf(now - 10_000, now - 20_000), now))
        assertTrue(CrashGuard.isSafe(listOf(now - 10_000, now - 20_000, now - 30_000), now))
        assertFalse("old crashes do not count", CrashGuard.isSafe(listOf(now - 300_000, now - 310_000, now - 320_000), now))
    }

    @Test fun kidsProfilesHideAdultTitles() {
        assertFalse(Profiles.safeForKids(Meta("a", "movie", "Scary", genres = listOf("Horror", "Mystery"))))
        assertFalse(Profiles.safeForKids(Meta("b", "series", "Gritty", certification = "TV-MA")))
        assertTrue(Profiles.safeForKids(Meta("c", "movie", "Cartoon", genres = listOf("Animation", "Family"), certification = "PG")))
        assertTrue(Profiles.safeForKids(Meta("d", "movie", "No data")))
    }

    @Test fun picksSubtitlesInTheWantedLanguage() {
        val text = """{"subtitles":[{"url":"https://s/1.srt","lang":"eng"},{"url":"https://s/2.srt","lang":"spa"},{"url":"https://s/3.vtt","lang":"en"},{"url":"ftp://bad","lang":"eng"}]}"""
        val all = Subs.parse(text)
        assertEquals(3, all.size)
        assertEquals(listOf("eng", "en"), Subs.pick(all, "en").map { it.lang })
        assertEquals(listOf("spa"), Subs.pick(all, "es").map { it.lang })
        assertTrue(Subs.pick(all, "off").isEmpty())
    }

    @Test fun buildsSubtitleUrl() {
        assertEquals("https://h/x/subtitles/series/tt1:1:2.json", Urls.subtitles("https://h/x", "series", "tt1:1:2"))
    }
}
