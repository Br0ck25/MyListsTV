package tv.myliststv.launcher

import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Developer tool, not a check: draws screens to PNG files so the look can be reviewed. Runs only when SHOTS_DIR is set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = App::class, qualifiers = "w960dp-h540dp-xhdpi")
class ShotTest {
    private val dir = System.getenv("SHOTS_DIR")

    private fun settle(ms: Long) { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); Thread.sleep(100) } }

    private fun lay(a: Activity) {
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
    }

    private fun shot(a: Activity, name: String) {
        lay(a)
        val v = a.window.decorView
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screens() {
        Assume.assumeTrue(dir != null)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); settle(500); shot(s, "settings")
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get(); settle(7000)
        // Focus the second poster so the backdrop and hero show, as they do when the viewer scrolls.
        fun all(v: View, out: MutableList<View> = ArrayList()): List<View> { out += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out); return out }
        lay(m); settle(300); lay(m)
        // Robolectric's window has no focus, so call the card's focus listener the way a D-pad move would.
        all(m.window.decorView).filterIsInstance<tv.myliststv.launcher.ui.Card>().getOrNull(1)?.let { it.onFocusChangeListener?.onFocusChange(it, true) }; settle(4500); shot(m, "home")
        tv.myliststv.launcher.data.Prefs.horizontalPosters = true
        val w = Robolectric.buildActivity(MainActivity::class.java).setup().get(); settle(7000); lay(w); settle(300); lay(w)
        all(w.window.decorView).filterIsInstance<tv.myliststv.launcher.ui.Card>().getOrNull(1)?.let { it.onFocusChangeListener?.onFocusChange(it, true) }; settle(4500); shot(w, "home_wide")
        tv.myliststv.launcher.data.Prefs.horizontalPosters = false
        val tab = all(m.window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Apps" }; tab.performClick(); settle(1500); shot(m, "apps")
        val d = Robolectric.buildActivity(DetailsActivity::class.java, android.content.Intent().putExtra("meta", tv.myliststv.launcher.data.Json.metaToJson(tv.myliststv.launcher.data.Meta("tt1160419", "movie", "Dune", "https://image.tmdb.org/t/p/w500/v1tRXZ4JtD2Iv6fjkPvT4GiwslV.jpg", "https://image.tmdb.org/t/p/w1280/qVgZu5BTx6pu4owCvVOm4zjTfOi.jpg")))).setup().get(); settle(9000); shot(d, "details")
    }

    @Test fun liveGuide() {
        Assume.assumeTrue(dir != null)
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        a.setTheme(R.style.Theme_MyListsTV)
        val screen = tv.myliststv.launcher.ui.LiveScreen(a)
        a.setContentView(android.widget.FrameLayout(a).apply { setBackgroundColor(tv.myliststv.launcher.ui.Ui.BG); addView(screen.view) })
        val chs = listOf("News One" to "News", "Sports HD" to "Sports", "Movie Max" to "Movies", "Kids Zone" to "Kids", "Music TV" to "Music", "No Guide" to "News")
            .mapIndexed { i, (n, g) -> tv.myliststv.launcher.data.Channel(n, null, g, "http://x/$i", if (i == 5) null else "c$i") }
        val t = System.currentTimeMillis(); val m = 60_000L
        tv.myliststv.launcher.data.Epg.setGuide(chs.filter { it.tvgId != null }.associate { c -> c.tvgId!! to listOf(
            tv.myliststv.launcher.data.Prog(t - 20 * m, t + 25 * m, "Morning Show"), tv.myliststv.launcher.data.Prog(t + 25 * m, t + 85 * m, "The Big Match"), tv.myliststv.launcher.data.Prog(t + 85 * m, t + 140 * m, "Evening Report")) })
        screen.show(chs); settle(500); shot(a, "live")
    }

    @Test fun sideBar() {
        Assume.assumeTrue(dir != null)
        tv.myliststv.launcher.data.Prefs.navStyle = 1
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get(); settle(7000); lay(m); settle(300); lay(m); shot(m, "side")
        tv.myliststv.launcher.data.Prefs.navStyle = 0
    }

    @Test fun library() {
        Assume.assumeTrue(dir != null)
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        a.setTheme(R.style.Theme_MyListsTV)
        listOf("tt1160419" to "Dune", "tt0133093" to "The Matrix", "tt0816692" to "Interstellar").forEach { (id, n) -> tv.myliststv.launcher.data.Library.toggle(tv.myliststv.launcher.data.Meta(id, "movie", n, "https://image.tmdb.org/t/p/w500/v1tRXZ4JtD2Iv6fjkPvT4GiwslV.jpg")) }
        val screen = tv.myliststv.launcher.ui.LibraryScreen(a) { }
        a.setContentView(android.widget.FrameLayout(a).apply { setBackgroundColor(tv.myliststv.launcher.ui.Ui.BG); addView(screen.view) })
        screen.start(); settle(3000); shot(a, "library")
        tv.myliststv.launcher.data.Library.all().forEach { tv.myliststv.launcher.data.Library.remove(it.id) }
    }
}
