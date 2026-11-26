package tv.myliststv.launcher

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.myliststv.launcher.data.SystemLinks
import tv.myliststv.launcher.screens.ArrangeActivity
import tv.myliststv.launcher.screens.SettingsActivity

/** Settings is one drawer: every group is on its first page, and the gear's old pages (Inputs, Android settings) live in it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class SettingsDrawerTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v; if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out); return out
    }
    private fun texts(a: SettingsActivity) = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }

    /** Clicks the row that holds the text [label]. */
    private fun open(a: SettingsActivity, label: String) {
        var v: View? = views(a.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label }
        while (v != null && !v.isClickable) v = v.parent as? View
        v!!.performClick(); idle()
    }

    private fun groupNames(a: SettingsActivity): List<String> {
        val f = SettingsActivity::class.java.getDeclaredField("groups").apply { isAccessible = true }
        return (f.get(a) as List<*>).map { g -> g!!.javaClass.getDeclaredField("name").apply { isAccessible = true }.get(g) as String }
    }

    private fun start() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get().also { idle() }

    @Test fun everyGroupIsListedAndThereIsNoAppsPage() {
        val a = start()
        val shown = texts(a)
        assertEquals("a group missing from the first page: add it to `sections`", emptyList<String>(), groupNames(a).filter { it !in shown })
        assertTrue("Apps is a tab, not a settings page", "Apps" !in shown)
        assertTrue("headings", listOf("You", "Home", "Watching", "This TV").all { it in shown })
    }

    @Test fun inputsPageSaysSoWhenTheTvListsNone() {
        val a = start(); open(a, "Inputs")
        assertTrue(texts(a).any { it.startsWith("This TV does not list any inputs") })
    }

    @Test fun androidSettingsPageListsEveryShortcut() {
        val a = start(); open(a, "Android settings")
        assertTrue(texts(a).containsAll(SystemLinks.pages.map { it.first }))
    }

    @Suppress("DEPRECATION")
    @Test fun backGoesFromThePageToTheListThenLeaves() {
        val a = start(); open(a, "Playback")
        assertTrue("Show Skip intro" in texts(a))
        a.onBackPressed(); idle()
        assertTrue("Playback" in texts(a) && "Show Skip intro" !in texts(a))
        assertFalse(a.isFinishing)
        a.onBackPressed()
        assertTrue(a.isFinishing)
    }

    @Test fun arrangeIsTheSameDrawerAndBackLeaves() {
        val a = Robolectric.buildActivity(ArrangeActivity::class.java, android.content.Intent().putExtra("what", "rows")).setup().get(); idle()
        val t = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Reorder Home Catalogs" in t && "Continue Watching" in t)
        @Suppress("DEPRECATION") a.onBackPressed()
        assertTrue(a.isFinishing)
    }
}
