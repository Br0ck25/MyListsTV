package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.view.View
import android.widget.ScrollView
import tv.myliststv.launcher.data.SystemLinks

/** The gear's TV pages: Inputs (HDMI and the like) and the shortcuts into Android's own settings. */
object SystemPanel {
    fun inputs(c: Activity, panel: SidePanel): View {
        val page = SidePanel.page(c)
        page.addView(SidePanel.header(c, "Inputs"))
        val list = Ui.vbox(c)
        val found = SystemLinks.inputs(c)
        if (found.isEmpty()) list.addView(Ui.text(c, "This TV does not list any inputs to apps. Use its own Input or Source button.", 15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap())
        found.forEach { i -> list.addView(SidePanel.item(c, R.drawable.ic_tv, i.label) { panel.dismiss(); SystemLinks.openInput(c, i.id) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) }) }
        page.addView(ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false }, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 4) })
        return page
    }

    fun androidSettings(c: Activity, panel: SidePanel): View {
        val page = SidePanel.page(c)
        page.addView(SidePanel.header(c, "Android settings"))
        val list = Ui.vbox(c)
        SystemLinks.pages.forEach { (name, action) -> list.addView(SidePanel.item(c, R.drawable.ic_settings, name) { panel.dismiss(); SystemLinks.open(c, action) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) }) }
        page.addView(ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false }, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 4) })
        return page
    }
}
