package tv.myliststv.launcher.data

import tv.myliststv.launcher.*
import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

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
    private val link = "https://mylistsaddon.com/i/SECRETTOKEN/tv/manifest.json"
    private fun e(id: String, type: String, pos: Long, dur: Long = 100_000) = WatchStore.Entry(id, type, "Show", "p.jpg", null, "$id:1:2", 1, 2, "Ep Two", pos, dur, 1L)
    @Test fun partWayGoesToProgressFinishedToHistory() {
        val (url, body) = Progress.request(link, e("tt1", "series", 25_000))!!
        assertTrue(url.endsWith("/api/nuvio-tv/progress")); assertTrue(body.contains("\"targetId\":\"SECRETTOKEN\"")); assertEquals("your account", Pairing.userOf(link)); assertTrue(body.contains("\"episode\":2")); assertTrue(body.contains("\"positionMs\":25000"))
        assertTrue(Progress.request(link, e("tt1", "series", 95_000))!!.first.endsWith("/api/nuvio-tv/history"))
    }
    @Test fun markingWatchedByHandGoesToHistoryAndUnmarkingRemovesIt() {
        val (url, body) = Progress.watchedRequest(link, "tt1", "series", 2, 5, true, 1234L)!!
        assertTrue(url.endsWith("/api/nuvio-tv/history")); assertTrue(body.contains("\"season\":2") && body.contains("\"episode\":5") && body.contains("\"completed\":true") && body.contains("\"watchedAt\":1234"))
        val (rm, rmBody) = Progress.watchedRequest(link, "tt1", "series", 2, 5, false, 1234L)!!
        assertTrue(rm.endsWith("/api/nuvio-tv/history/remove")); assertFalse(rmBody.contains("completed"))
        assertFalse("a movie has no season", Progress.watchedRequest(link, "tt9", "movie", -1, -1, true, 1L)!!.second.contains("season"))
        assertNull(Progress.watchedRequest("", "tt1", "movie", -1, -1, true, 1L)); assertNull(Progress.watchedRequest(link, "live:x", "movie", -1, -1, true, 1L))
    }
    @Test fun nothingIsSentWhenSignedOutOrJustStartedOrNotAnImdbTitle() {
        assertNull(Progress.request("", e("tt1", "movie", 50_000)))
        assertNull(Progress.request(link, e("tt1", "movie", 500)))
        assertNull(Progress.request(link, e("live:abc", "tv", 50_000)))
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class HomeSetupTest {
    @Test fun commandsUseThisBoxAndTheRealServiceName() {
        val c = HomeSetup.commands("192.168.1.9")
        assertTrue(c[0] == "adb connect 192.168.1.9:5555")
        assertTrue(c.any { it.contains("tv.myliststv.launcher/tv.myliststv.launcher.services.HomeButtonService") })
        // The service class named in the commands must exist, or the fallback could never be switched on.
        Class.forName("tv.myliststv.launcher.services.HomeButtonService")
    }
    @Test fun fallbackIsOffUntilEnabled() {
        val c = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(HomeSetup.fallbackOn(c))
        assertFalse(tv.myliststv.launcher.services.Notices.allowed(c))
        assertTrue(tv.myliststv.launcher.services.Notices.list(c).isEmpty())
        Class.forName("tv.myliststv.launcher.services.NotificationsService")
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class ButtonMapTest {
    @Test fun rulesRoundTripAndReplaceTheSameButtonAndPress() {
        Prefs.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Prefs.buttonMap = ""
        ButtonMap.add(ButtonMap.Rule(183, "single", "live")); ButtonMap.add(ButtonMap.Rule(183, "double", "app:com.x")); ButtonMap.add(ButtonMap.Rule(183, "single", "search"))
        assertEquals(listOf("double" to "app:com.x", "single" to "search").toSet(), ButtonMap.rules().map { it.press to it.action }.toSet())
        assertTrue(ButtonMap.allowed(183)); assertFalse(ButtonMap.allowed(android.view.KeyEvent.KEYCODE_BACK)); assertFalse(ButtonMap.allowed(android.view.KeyEvent.KEYCODE_HOME))
        Prefs.buttonMap = ""
    }

    @Test fun aRuleKeepsItsScopeAndAnOlderRuleWorksEverywhere() {
        Prefs.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Prefs.buttonMap = "183|single|search"
        assertEquals("all", ButtonMap.rules().single().scope)
        ButtonMap.add(ButtonMap.Rule(183, "single", "search", "launcher"))
        assertEquals("launcher", ButtonMap.rules().single().scope)
        Prefs.buttonMap = "183|single|search|nonsense"
        assertEquals("a scope nobody knows falls back to everywhere", "all", ButtonMap.rules().single().scope)
        Prefs.buttonMap = ""
    }
}

class AirDateTest {
    @Test fun episodesKeepTheirAirDate() {
        val m = Json.parseMeta("""{"meta":{"id":"tt1","type":"series","name":"S","videos":[{"id":"tt1:1:1","season":1,"episode":1,"title":"A","released":"2026-11-19T00:00:00.000Z"},{"id":"tt1:1:2","season":1,"episode":2,"title":"B"}]}}""")!!
        assertEquals(java.util.Date(1795046400000L), m.videos[0].airDate())
        assertNull(m.videos[1].airDate())
        assertEquals("2026-11-19T00:00:00.000Z", Json.metaFromJson(Json.metaToJson(m))!!.videos[0].released)
    }
}

class StatusTest {
    @Test fun statusSurvivesParsingStoringAndMerging() {
        val m = Json.parseMeta("""{"meta":{"id":"tt1","type":"series","name":"S","status":"Continuing"}}""")!!
        assertEquals("Continuing", m.status)
        assertEquals("Continuing", Json.metaFromJson(Json.metaToJson(m))!!.status)
        assertEquals("Continuing", Catalogs.fillGaps(Meta("tt1", "series", "S"), m).status)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class LibraryTest {
    @Test fun addRemoveAndEachProfileHasItsOwnList() {
        val m = Meta("tt1", "movie", "One", "p.jpg"); val n = Meta("tt2", "series", "Two")
        assertTrue(Library.toggle(m)); assertTrue(Library.toggle(n))
        assertEquals(listOf("tt2", "tt1"), Library.all().map { it.id })       // newest first
        assertFalse(Library.toggle(m)); assertFalse(Library.has("tt1"))
        val before = Profiles.activeId
        Profiles.add("Kid", true); Profiles.activeId = Profiles.all().last().id
        assertTrue("another profile starts empty", Library.all().isEmpty())
        Profiles.activeId = before
        assertTrue(Library.has("tt2"))
        val exported = Library.export(); Library.remove("tt2"); assertTrue(Library.all().isEmpty())
        Library.import(exported); assertEquals(listOf("tt2"), Library.all().map { it.id })
        Library.remove("tt2")
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class AlertsAndWatchedTest {
    private val show = Meta("tt1", "series", "Show")
    private fun v(s: Int, e: Int, date: String?) = Video("tt1:$s:$e", "T", s, e, released = date)

    @Test fun alertsOnlyForAnEpisodeNewerThanTheLastSeen() {
        val vs = listOf(v(1, 1, "2020-01-01T00:00:00.000Z"), v(1, 2, "2020-01-08T00:00:00.000Z"), v(1, 3, "2999-01-01T00:00:00.000Z"))
        assertEquals(10002, Alerts.newestAired(vs))                    // the future episode does not count
        assertNull("first look only notes where the show is", Alerts.compare(show, 0, 10002))
        assertNull("nothing new", Alerts.compare(show, 10002, 10002))
        assertEquals("S1 · E3 is out", Alerts.compare(show, 10002, 10003)?.text)
        assertEquals("S2 · E1 is out", Alerts.compare(show, 10009, 20001)?.text)
    }

    @Test fun watchedEpisodesAreRememberedPerShow() {
        Watched.set("tt1", "tt1:1:1", true); Watched.set("tt1", "tt1:1:2", true); Watched.set("tt2", "tt2:1:1", true)
        assertEquals(setOf("tt1:1:1", "tt1:1:2"), Watched.of("tt1")); assertTrue(Watched.has("tt2", "tt2:1:1"))
        Watched.set("tt1", "tt1:1:1", false); assertFalse(Watched.has("tt1", "tt1:1:1")); assertTrue(Watched.has("tt1", "tt1:1:2"))
        Watched.set("tt1", "tt1:1:2", false); Watched.set("tt2", "tt2:1:1", false); assertTrue(Watched.of("tt1").isEmpty())
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class ProfileAccountsTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun eachProfileKeepsItsOwnAccountAddonsLibraryAndBackupCode() {
        val main = Profiles.activeId
        Prefs.accountLink = "https://mylistsaddon.com/i/MAINTOKEN/tv/manifest.json"; AddonStore.add("https://a.example/manifest.json"); Prefs.backupCode = "AAAA-AAAA-AAAA-AAAA"
        Library.toggle(Meta("tt1", "movie", "Main film"))
        Profiles.add("Sam", false); val sam = Profiles.all().last().id
        Profiles.switchTo(ctx, sam)
        assertEquals("a new profile is signed out", "", Prefs.accountLink)
        assertEquals("and starts with the default addon only", listOf(AddonStore.DEFAULT_URL), AddonStore.saved().map { it.url })
        assertEquals("", Prefs.backupCode); assertTrue(Library.all().isEmpty())
        Prefs.accountLink = "https://mylistsaddon.com/i/SAMTOKEN/tv/manifest.json"; AddonStore.add("https://b.example/manifest.json")
        assertEquals("SAMTOKEN", Pairing.targetOf(Prefs.accountLink))
        Profiles.switchTo(ctx, main)
        assertEquals("MAINTOKEN", Pairing.targetOf(Prefs.accountLink)); assertTrue(AddonStore.saved().any { it.url == "https://a.example/manifest.json" }); assertFalse(AddonStore.saved().any { it.url == "https://b.example/manifest.json" })
        assertEquals("AAAA-AAAA-AAAA-AAAA", Prefs.backupCode); assertTrue(Library.has("tt1"))
        // The main profile's backup carries the profile list and settings; another profile's does not.
        assertTrue(org.json.JSONObject(Backup.snapshot()).has("profiles"))
        Profiles.switchTo(ctx, sam)
        assertFalse(org.json.JSONObject(Backup.snapshot()).has("profiles")); assertTrue(org.json.JSONObject(Backup.snapshot()).getString("account").contains("SAMTOKEN"))
        // Removing a profile removes what it kept.
        Profiles.remove(sam); Profiles.switchTo(ctx, Profiles.activeId)
        assertEquals(main, Profiles.activeId)
        Profiles.switchTo(ctx, main); Prefs.accountLink = ""; AddonStore.reset(); Prefs.backupCode = ""; Library.remove("tt1")
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class SetupSyncTest {
    @Test fun sendsOnlyTheProfilesOwnAddons() {
        Prefs.accountLink = "https://mylistsaddon.com/i/TOKEN/tv/manifest.json"
        AddonStore.reset(); AddonStore.remove(AddonStore.DEFAULT_URL)
        AddonStore.add(Prefs.accountLink); AddonStore.add("https://torrentio.example/key/manifest.json")
        val p = SetupSync.payload()
        val urls = (0 until p.getJSONArray("addons").length()).map { p.getJSONArray("addons").getJSONObject(it).getString("url") }
        assertEquals("not this TV's pairing link, not the default", listOf("https://torrentio.example/key/manifest.json"), urls)
        assertFalse("the pairing secret never goes in the list", p.toString().contains("TOKEN"))
        Prefs.accountLink = ""; AddonStore.reset()
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class AddonSharingTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun aProfileCanUseAnotherProfilesAddonsAndStopWithACopy() {
        val main = Profiles.activeId
        Prefs.accountLink = "https://mylistsaddon.com/i/MAIN/tv/manifest.json"
        AddonStore.add(Prefs.accountLink); AddonStore.add("https://a.example/manifest.json")
        Profiles.add("Kid", true); val kid = Profiles.all().last().id
        Profiles.switchTo(ctx, kid)
        assertEquals("own list first", listOf(AddonStore.DEFAULT_URL), AddonStore.saved().map { it.url })
        Profiles.shareAddons(kid, main)
        assertEquals("the default plus Main's addons, not Main's sign-in link", listOf(AddonStore.DEFAULT_URL, "https://a.example/manifest.json"), AddonStore.saved().map { it.url })
        AddonStore.add("https://kid.example/manifest.json")
        assertFalse("a profile that shares cannot change the shared list", AddonStore.saved().any { it.url.contains("kid.example") })
        Profiles.switchTo(ctx, main); AddonStore.add("https://b.example/manifest.json")
        Profiles.switchTo(ctx, kid)
        assertTrue("a change on Main shows on the kid profile", AddonStore.saved().any { it.url.contains("b.example") })
        AddonStore.stopSharing(kid)
        assertEquals(null, Profiles.addonSource(kid))
        assertTrue("the kid keeps a copy", AddonStore.ownSaved(kid).any { it.url.contains("b.example") })
        Profiles.switchTo(ctx, main); Profiles.remove(kid); Prefs.accountLink = ""; AddonStore.reset()
    }

    @Test fun removingTheSharedFromProfileLeavesTheOtherWithItsOwnList() {
        val main = Profiles.activeId
        Profiles.add("A", false); Profiles.add("B", false)
        val a = Profiles.all()[1].id; val b = Profiles.all()[2].id
        Profiles.shareAddons(b, a)
        assertEquals(a, Profiles.addonSource(b))
        Profiles.remove(a)
        assertNull(Profiles.addonSource(b))
        Profiles.shareAddons(b, main); Profiles.shareAddons(main, b)
        assertNull("no chains: Main shares from B, so B cannot be shared from", Profiles.addonSource(b))
        Profiles.shareAddons(main, null); Profiles.remove(b)
    }
}

class SignInReasonTest {
    @Test fun aRedirectIsNamedAsOneAndNeverFollowed() {
        assertTrue(Pairing.reason(301).contains("redirect")); assertTrue(Pairing.reason(301).contains("301"))
        assertTrue(Pairing.reason(500).contains("error"))
        // Only a real device-code answer is accepted.
        assertNull(Pairing.parseStart("<html>301 Moved Permanently</html>")); assertNull(Pairing.parseStart("""{"ok":false}"""))
    }
}

class AccountContinueTest {
    @Test fun anAccountEntryBecomesALauncherEntry() {
        val ep = Progress.entryFrom(org.json.JSONObject("""{"id":"tt1","type":"series","name":"Show","episodeName":"Pilot","poster":"p","season":2,"episode":3,"positionMs":1000,"durationMs":4000,"watchedAt":9}"""))!!
        assertEquals("tt1:2:3", ep.videoId); assertEquals(2, ep.season); assertEquals(3, ep.episode); assertEquals("Pilot", ep.episodeTitle); assertEquals(0.25f, ep.fraction, 0.001f)
        val nextUp = Progress.entryFrom(org.json.JSONObject("""{"id":"tt1","type":"series","name":"Show","season":2,"episode":4,"positionMs":0,"durationMs":0,"watchedAt":9}"""))!!
        assertEquals("a next-up episode has no length", 0L, nextUp.durationMs)
        assertEquals("movies play from their own id", "tt2", Progress.entryFrom(org.json.JSONObject("""{"id":"tt2","type":"movie","name":"Film"}"""))!!.videoId)
        assertNull("only imdb titles", Progress.entryFrom(org.json.JSONObject("""{"id":"x1","type":"movie","name":"Film"}""")))
        assertEquals("Sam", Pairing.parseUser("""{"ok":true,"status":"authenticated","username":"Sam"}""")); assertEquals("", Pairing.parseUser("not json"))
    }
}

class QrTest {
    @Test fun aQrCodeHasFindersFormatBitsAndTheRightSize() {
        val m = tv.myliststv.launcher.ui.Qr.matrix("https://mylistsaddon.com/link?code=ABCD-1234")
        assertEquals("version 3", 29, m.size)
        for ((x, y) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) assertTrue("finder corner at $x,$y", m[y][x] && m[y][x + 6] && m[y + 6][x] && m[y + 6][x + 6] && !m[y + 1][x + 1])
        assertTrue("timing pattern", m[6][8] && !m[6][9])
        assertTrue("the dark module", m[m.size - 8][8])
        assertEquals(21, tv.myliststv.launcher.ui.Qr.matrix("hi").size)
        assertTrue(try { tv.myliststv.launcher.ui.Qr.matrix("x".repeat(200)); false } catch (_: IllegalArgumentException) { true })
    }
}
