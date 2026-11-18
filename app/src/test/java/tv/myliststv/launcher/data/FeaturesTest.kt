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

class DetailsDataTest {
    @Test fun readsCastCrewTrailersAndFactsFromBothAddonShapes() {
        // My Lists: names as plain strings, trailers as ytId streams. Cinemeta: trailers as {source}.
        val a = Json.parseMeta("""{"meta":{"id":"tt1","type":"movie","name":"Dune","cast":["Timothée Chalamet","Zendaya"],"director":["Denis Villeneuve"],"trailerStreams":[{"title":"Trailer","ytId":"w0HgHet0sxg"}],"trailers":[{"source":"w0HgHet0sxg","type":"Trailer"}]}}""")!!
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), a.cast); assertEquals(listOf("Denis Villeneuve"), a.directors)
        assertEquals("the same trailer is listed once", listOf("w0HgHet0sxg"), a.trailers)
        val b = Json.parseMeta("""{"meta":{"id":"tt1","type":"movie","name":"Dune","country":"Canada, United States","awards":"Won 6 Oscars","writer":["Jon Spaihts"],"logo":"https://l/logo.png","cast":[{"name":"Object Form"}]}}""")!!
        assertEquals("Canada, United States", b.country); assertEquals(listOf("Jon Spaihts"), b.writers); assertEquals(listOf("Object Form"), b.cast)
    }

    @Test fun extraDetailsOnlyFillWhatTheCatalogAddonLacks() {
        val mine = Meta("tt1", "movie", "Dune", description = "My Lists text", cast = listOf("A"), rating = "7.8")
        val cine = Meta("tt1", "movie", "Dune", description = "Cinemeta text", cast = listOf("B", "C"), logo = "https://l/logo.png", country = "Canada", awards = "Won 6 Oscars", rating = "8.0")
        val m = Catalogs.fillGaps(mine, cine)
        assertEquals("My Lists text", m.description); assertEquals(listOf("A"), m.cast); assertEquals("7.8", m.rating)
        assertEquals("https://l/logo.png", m.logo); assertEquals("Canada", m.country); assertEquals("Won 6 Oscars", m.awards)
    }

    @Test fun detailsSurviveTheTripBetweenScreens() {
        val m = Meta("tt1", "movie", "Dune", cast = listOf("A", "B"), directors = listOf("D"), trailers = listOf("w0HgHet0sxg"), country = "Canada", awards = "x")
        assertEquals(m, Json.metaFromJson(Json.metaToJson(m)))
    }

    @Test fun readsACastPhotoFromAPersonSearch() {
        assertEquals("https://image.tmdb.org/t/p/w200/x.jpg", People.parsePhoto("""{"ok":true,"results":[{"name":"Zendaya","poster":"https://image.tmdb.org/t/p/w200/x.jpg"},{"name":"Other","poster":null}]}"""))
        assertNull(People.parsePhoto("""{"ok":true,"results":[]}""")); assertNull(People.parsePhoto("not json"))
    }
}

class RatingTest {
    @Test fun aZeroRatingIsLeftOut() {
        assertNull(Json.parseMeta("""{"meta":{"id":"tt1","name":"New","imdbRating":"0"}}""")!!.rating)
        assertNull(Json.parseMeta("""{"meta":{"id":"tt1","name":"New","rating":0.0}}""")!!.rating)
        assertEquals("7.8", Json.parseMeta("""{"meta":{"id":"tt1","name":"Old","imdbRating":"7.8"}}""")!!.rating)
        assertEquals("one decimal", "9.1", Json.parseMeta("""{"meta":{"id":"tt1","name":"Long","rating":9.077}}""")!!.rating)
    }
}

class DebridAddonTest {
    @Test fun aBadKeyNoticeIsExplainedAndIsNotASource() {
        // What Torrentio answers when the TorBox key is wrong: one fake "stream" whose video says so.
        val bad = """{"streams":[{"name":"Torrentio\nTB error","title":"Invalid TorBox ApiKey/Token!","url":"https://torrentio.strem.fun/videos/failed_access_v3.mp4"}]}"""
        assertTrue(Json.parseStreams(bad, "Torrentio", "https://t/manifest.json").isEmpty())
        assertEquals(listOf("Invalid TorBox ApiKey/Token!"), Json.notices(bad))
    }

    @Test fun torrentOnlyLinksAreCountedSoTheViewerIsTold() {
        val noKey = """{"streams":[{"name":"Torrentio\n4k","title":"x","infoHash":"2f1b54bf","fileIdx":0},{"name":"Torrentio\n1080p","title":"y","infoHash":"aa11"}]}"""
        assertTrue(Json.parseStreams(noKey, "Torrentio", "u").isEmpty()); assertEquals(2, Json.torrentOnlyCount(noKey))
    }

    @Test fun debridLinksArePlayableSources() {
        val ok = """{"streams":[{"name":"Torrentio\n[TB+] 1080p","title":"Dune 2021 1080p\n👤 30 💾 9.5 GB","url":"https://torrentio.strem.fun/resolve/torbox/KEY/hash/null/0/Dune.mkv","behaviorHints":{"bingeGroup":"torrentio|1080p"}}]}"""
        val l = Json.parseStreams(ok, "Torrentio", "u")
        assertEquals(1, l.size); assertEquals(1080, l[0].resolution); assertTrue(l[0].cached); assertEquals(0, Json.torrentOnlyCount(ok))
    }

    @Test fun debridAddonsGetTimeToAnswer() = assertTrue(StreamResolver.DEFAULT_TIMEOUT_MS >= 20_000)
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class PosterShapeTest {
    @Test fun wideAndTallPostersAreASettingThatMakesHomeRebuild() {
        Prefs.horizontalPosters = false
        assertFalse(Prefs.horizontalPosters)
        val before = Prefs.epoch
        Prefs.horizontalPosters = true
        assertTrue(Prefs.horizontalPosters); assertTrue("Home is told to rebuild", Prefs.epoch > before)
        Prefs.horizontalPosters = false
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class HomeLayoutTest {
    @Test fun ownerOrderWinsAndDefaultsKeepAppsLast() {
        val c = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs.init(c)
        assertTrue(Prefs.homeRank("Continue watching") < Prefs.homeRank("Popular") && Prefs.homeRank("Popular") < Prefs.homeRank("Your apps"))
        Prefs.homeOrder = listOf("Your apps", "Popular")
        assertTrue(Prefs.homeRank("Your apps") < Prefs.homeRank("Popular"))
        Prefs.homeOrder = emptyList()
    }
}

class ProgressTest {
    private val link = "https://mylistsaddon.com/jim/tv/manifest.json"
    private fun e(id: String, type: String, pos: Long, dur: Long = 100_000) = WatchStore.Entry(id, type, "Show", "p.jpg", null, "$id:1:2", 1, 2, "Ep Two", pos, dur, 1L)
    @Test fun partWayGoesToProgressFinishedToHistory() {
        val (url, body) = Progress.request(link, e("tt1", "series", 25_000))!!
        assertTrue(url.endsWith("/api/nuvio-tv/progress")); assertTrue(body.contains("\"targetId\":\"jim\"")); assertTrue(body.contains("\"episode\":2")); assertTrue(body.contains("\"positionMs\":25000"))
        assertTrue(Progress.request(link, e("tt1", "series", 95_000))!!.first.endsWith("/api/nuvio-tv/history"))
    }
    @Test fun nothingIsSentWhenSignedOutOrJustStartedOrNotAnImdbTitle() {
        assertNull(Progress.request("", e("tt1", "movie", 50_000)))
        assertNull(Progress.request(link, e("tt1", "movie", 500)))
        assertNull(Progress.request(link, e("live:abc", "tv", 50_000)))
    }
}
