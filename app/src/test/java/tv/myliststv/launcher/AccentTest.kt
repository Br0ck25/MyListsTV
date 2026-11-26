package tv.myliststv.launcher

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.myliststv.launcher.data.Prefs
import tv.myliststv.launcher.screens.SettingsActivity
import tv.myliststv.launcher.ui.Ui

/** The highlight colour is one setting that every ring, selection, toggle and primary button follows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class AccentTest {
    @After fun reset() { Prefs.accent = 0 }

    private fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
    }

    @Test fun everyFillCarriesWhiteTextClearly() {
        Ui.ACCENTS.forEach { a -> assertTrue("${a.name} fill needs 4.5:1 against white", 1.05 / (luminance(a.fill) + 0.05) >= 4.5) }
    }

    @Test fun theHighlightFollowsTheSetting() {
        assertEquals(Ui.ACCENTS[0].main, Ui.BRAND)
        Prefs.accent = 3
        assertEquals(Ui.ACCENTS[3].main, Ui.BRAND); assertEquals(Ui.ACCENTS[3].fill, Ui.BRAND_FILL)
        assertEquals("a soft fill of the same colour", Ui.ACCENTS[3].main and 0x00FFFFFF, Ui.BRAND_SOFT and 0x00FFFFFF)
        Prefs.accent = 99
        assertEquals("an unknown choice falls back to a real colour", Ui.ACCENTS.last().main, Ui.BRAND)
    }

    @Test fun settingsOffersItUnderLayoutAndChangingItSticks() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); shadowOf(Looper.getMainLooper()).idle()
        fun views(v: View, out: MutableList<View> = ArrayList()): List<View> { out += v; if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out); return out }
        fun click(label: String) { var v: View? = views(a.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label }; while (v != null && !v.isClickable) v = v.parent as? View; v!!.performClick(); shadowOf(Looper.getMainLooper()).idle() }
        click("Layout")
        click("Highlight colour")
        assertEquals(1, Prefs.accent)
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("Green") })
    }
}
