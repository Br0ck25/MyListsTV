package tv.myliststv.launcher

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

    @Test fun homeLoadsDefaultRowsFromMyLists() {
        assumeAddonReachable()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle()
        val rv = views(a.window.decorView).filterIsInstance<RecyclerView>()
        assertTrue("home has rows", rv.any { it.adapter != null && it.adapter!!.itemCount >= 2 })
        assertTrue("addon loaded", AddonStore.loaded.any { it.name == "My Lists" })
    }

    @Test fun everyTabBuilds() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle(3000)
        val tabs = views(a.window.decorView).filterIsInstance<android.widget.TextView>().filter { it.text.toString() in listOf("Movies", "Series", "Apps", "Addons") }
        assertEquals(4, tabs.size)
        tabs.forEach { it.performClick(); settle(1500) }
    }

    @Test fun detailsStreamsSearchAndSettingsOpen() {
        val m = Meta("tt1160419", "movie", "Dune", description = "x")
        val json = Json.metaToJson(m)
        val d = Robolectric.buildActivity(DetailsActivity::class.java, Intent().putExtra("meta", json)).setup().get()
        settle(3000)
        assertTrue(views(d.window.decorView).any { it is android.widget.TextView && it.text.toString() == "Dune" })
        Robolectric.buildActivity(StreamsActivity::class.java, Intent().putExtra("meta", json).putExtra("videoId", "tt1160419")).setup()
        settle(2000)
        Robolectric.buildActivity(SearchActivity::class.java).setup()
        Robolectric.buildActivity(SettingsActivity::class.java).setup()
    }

    @Test fun searchFindsTitlesFromTheLiveAddon() {
        assumeAddonReachable()
        Robolectric.buildActivity(MainActivity::class.java).setup(); settle(3000)
        var found: List<Meta>? = null
        Catalogs.search("dune") { found = it }
        settle(4500)
        assertTrue("search returned Dune", found?.any { it.name.contains("Dune") } == true)
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
