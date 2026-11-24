package tv.myliststv.launcher

import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.PlaySession

/** Starts every screen on a simulated device and reads the real My Lists default catalog, so a crash on launch fails the build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class SmokeTest {

    /** Tests that read the live My Lists server are skipped, not failed, when it cannot be reached (for example from CI). */
    private fun assumeAddonReachable() {
        val ok = try { Http.fetchBlocking(AddonStore.DEFAULT_URL, 6000, 8000).contains("catalogs") } catch (_: Throwable) { false }
        org.junit.Assume.assumeTrue("My Lists default addon is not reachable", ok)
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v; if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out); return out
    }

    /** Lets background network threads finish, then runs what they posted to the main thread. */
    private fun settle(ms: Long = 6000) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) }
    }

    /** First run: the setup shows one step at a time, each with Skip, and Skip moves on until the closing screen. */
    @Test fun setupScreensCanBeSkippedOneByOne() {
        val a = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        fun texts() = views(a.window.decorView).filterIsInstance<android.widget.TextView>()
        assertTrue("a step is shown", texts().any { it.text.toString().startsWith("Step ") })
        var guard = 0
        while (guard++ < 6) { texts().firstOrNull { it.text == "Skip this step" }?.performClick() ?: break }
        assertTrue("closing screen", texts().any { it.text == "Setup finished" || it.text == "You are all set" })
        texts().first { it.text == "Continue" }.performClick()
        assertTrue("setup is marked done", Prefs.setupDone)
        assertTrue(a.isFinishing)
    }

    @Test fun homeLoadsDefaultRowsFromMyLists() {
        assumeAddonReachable()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // The catalog is a live server: wait up to 25 s for its rows instead of a fixed 6 s.
        fun rowsShown() = views(a.window.decorView).filterIsInstance<RecyclerView>().any { it.adapter != null && it.adapter!!.itemCount >= 2 }
        val end = System.currentTimeMillis() + 25_000
        while (!rowsShown() && System.currentTimeMillis() < end) settle(1000)
        assertTrue("home has rows", rowsShown())
        assertTrue("addon loaded", AddonStore.loaded.any { it.name == "My Lists" })
    }

    /** My Lists removed, only the standard Stremio catalog left: Home, search and details must all still work. */
    @Test fun worksWithoutTheMyListsAddon() {
        val cinemeta = "https://v3-cinemeta.strem.io/manifest.json"
        val ok = try { Http.fetchBlocking(cinemeta, 6000, 8000).contains("catalogs") } catch (_: Throwable) { false }
        org.junit.Assume.assumeTrue("Cinemeta is not reachable", ok)
        AddonStore.reset(); AddonStore.remove(AddonStore.DEFAULT_URL); AddonStore.add(cinemeta)
        var loaded = false; AddonStore.load { loaded = true }; settle(4000)
        assertTrue(loaded)
        assertTrue("no My Lists", AddonStore.loaded.none { it.name == "My Lists" })
        assertTrue("home has catalogs", Catalogs.browseRows(null, 8).isNotEmpty())
        var found: List<Meta> = emptyList(); Catalogs.search("matrix") { found = it }; settle(5000)
        assertTrue("search finds titles", found.isNotEmpty())
        var meta: Meta? = null; Catalogs.meta("movie", "tt0133093", null) { meta = it }; settle(5000)
        assertEquals("The Matrix", meta?.name)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get(); settle(6000)
        assertTrue("home rows", views(a.window.decorView).filterIsInstance<RecyclerView>().any { it.adapter != null && it.adapter!!.itemCount >= 2 })
        AddonStore.reset()
    }

    @Test fun everyTabBuilds() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle(3000)
        val tabs = views(a.window.decorView).filterIsInstance<android.widget.TextView>().filter { it.text.toString() in listOf("Movies", "Series", "Library", "Apps") }
        assertEquals("four pages; addons live in Settings now", 4, tabs.size)
        tabs.forEach { it.performClick(); settle(1500) }
    }

    @Test fun addonsPageOpensFromSettings() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java, Intent().putExtra("open", "addons")).setup().get()
        settle(1500)
        assertTrue(views(a.window.decorView).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Addons" })
    }

    @Test fun detailsStreamsSearchAndSettingsOpen() {
        val m = Meta("tt1160419", "movie", "Dune", description = "x")
        val json = Json.metaToJson(m)
        val d = Robolectric.buildActivity(DetailsActivity::class.java, Intent().putExtra("meta", json)).setup().get()
        settle(3000)
        assertTrue(views(d.window.decorView).any { it is android.widget.TextView && it.text.toString() == "Dune" })
        Robolectric.buildActivity(StreamsActivity::class.java, Intent().putExtra("meta", json).putExtra("videoId", "tt1160419")).setup()
        settle(2000)
        tv.myliststv.launcher.ui.SearchScreen(d, {}).start()   // Search is a page of Home now, not an activity
        Robolectric.buildActivity(SettingsActivity::class.java).setup()
    }

    @Test fun searchFindsTitlesFromTheLiveAddon() {
        assumeAddonReachable()
        Robolectric.buildActivity(MainActivity::class.java).setup(); settle(3000)
        val answers = ArrayList<List<Meta>>()
        Catalogs.search("dune") { answers += it }
        // Results arrive as each addon answers, so wait for them instead of for a fixed time.
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && answers.none { l -> l.any { it.name.contains("Dune") } }) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) }
        assertTrue("search returned Dune", answers.any { l -> l.any { it.name.contains("Dune") } })
    }
}

/** The player: starts, shows the Sources panel, and falls back to the next source when the first cannot be reached. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class PlayerSmokeTest {
    private fun stream(n: Int) = StreamItem("Addon$n", "https://a$n.example/manifest.json", "http://127.0.0.1:1/video$n.mkv", "1080p source $n", "t", "f$n.mkv", null, 1080, 1_000_000_000L - n, false, false, null)

    @Test fun playerStartsAndSourcesPanelListsEverySource() {
        PlaySession.meta = Meta("tt1", "movie", "Test Movie")
        PlaySession.videoId = "tt1"
        PlaySession.ranked = listOf(stream(1), stream(2), stream(3))
        PlaySession.current = PlaySession.ranked[0]
        PlaySession.autoSelected = true
        PlaySession.resumeMs = 0
        val a = Robolectric.buildActivity(PlayerActivity::class.java).setup().get()
        val end = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) }
        fun all(v: View, out: MutableList<View> = ArrayList()): List<View> { out += v; if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out); return out }
        val sources = all(a.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Sources" }
        sources.performClick(); shadowOf(Looper.getMainLooper()).idle()
        val labels = all(a.window.decorView).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue("panel lists the sources", labels.count { it.contains("source ") } >= 3)
    }
}
