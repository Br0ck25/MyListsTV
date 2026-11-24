package tv.myliststv.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.data.SystemLinks
import tv.myliststv.launcher.screens.SettingsActivity
import tv.myliststv.launcher.ui.SidePanel
import tv.myliststv.launcher.ui.SystemPanel

/** The gear's Inputs and Android settings pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class SystemPanelTest {
    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) out += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out); return out
    }

    @Test fun inputsPageSaysSoWhenTheTvListsNone() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val t = texts(SystemPanel.inputs(a, SidePanel(a)))
        assertTrue(t.first() == "Inputs" && t.any { it.startsWith("This TV does not list any inputs") })
    }

    @Test fun androidSettingsPageListsEveryShortcut() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val t = texts(SystemPanel.androidSettings(a, SidePanel(a)))
        assertEquals(SystemLinks.pages.map { it.first }, t.drop(1))
    }
}
