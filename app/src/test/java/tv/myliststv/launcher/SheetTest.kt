package tv.myliststv.launcher

import android.os.Looper
import android.view.Gravity
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
import org.robolectric.shadows.ShadowDialog
import tv.myliststv.launcher.ui.Sheet
import tv.myliststv.launcher.ui.SidePanel
import tv.myliststv.launcher.ui.Ui

/** Every pop-up is a see-through panel against the right edge, no wider than the other panels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class SheetTest {
    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) out += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out); return out
    }

    @Test fun aSheetSitsAtTheRightEdgeAtThePanelWidth() {
        val a = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        Sheet.confirm(a, "Sign out?", "Home goes back to the default catalogs.", "Sign out", "Keep signed in") { }
        shadowOf(Looper.getMainLooper()).idle()
        val d = ShadowDialog.getLatestDialog()
        assertTrue(d.isShowing)
        val w = d.window!!.attributes
        assertTrue("against the right edge", w.gravity and Gravity.END == Gravity.END)
        assertEquals("the same width as every other panel", Ui.dp(a, SidePanel.WIDTH + SidePanel.MARGIN), w.width)
        assertTrue(texts(d.window!!.decorView).containsAll(listOf("Sign out?", "Keep signed in", "Sign out")))
    }

    @Test fun panelsAreSeeThrough() {
        assertTrue("alpha under 100% and over 70%", (Ui.GLASS ushr 24) in 0xB3..0xFE)
    }
}
