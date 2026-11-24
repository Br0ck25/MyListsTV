package tv.myliststv.launcher.screens

import tv.myliststv.launcher.*

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Orders and hides the Home rows or the apps row. Each item has Up, Down and Hide buttons; a press moves the row in place,
 * saves at once and leaves focus on the same button, so a run of moves needs no reloading and never jumps back to the top.
 * Back leaves; nothing else to confirm.
 */
class ArrangeActivity : Activity() {
    private class Item(val key: String, val name: String, var hidden: Boolean)

    private lateinit var list: LinearLayout
    private val items = ArrayList<Item>()
    private var save: (List<String>, List<String>) -> Unit = { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = intent.getStringExtra("what") == "apps"
        list = Ui.vbox(this)
        // A modal over Settings, not a page: a rounded card in a window that only dims what is behind it.
        val root = Ui.vbox(this).apply { background = Ui.rounded(this@ArrangeActivity, Ui.CARD, Ui.R_DIALOG); setPadding(Ui.dp(this@ArrangeActivity, 24), Ui.dp(this@ArrangeActivity, 20), Ui.dp(this@ArrangeActivity, 24), Ui.dp(this@ArrangeActivity, 16)) }
        root.addView(Ui.text(this, if (apps) "Arrange apps row" else "Arrange Home rows", 24f, Ui.TEXT, true))
        root.addView(Ui.text(this, "Up and Down move it. Hide takes it off Home. Back when you are done.", 13f, Ui.DIM).apply { setPadding(0, Ui.dp(this@ArrangeActivity, 6), 0, Ui.dp(this@ArrangeActivity, 14)) })
        root.addView(ScrollView(this).apply { addView(list); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.setGravity(android.view.Gravity.CENTER)
        window.setLayout(Ui.dp(this, 560), (resources.displayMetrics.heightPixels * 0.86f).toInt())   // after the content: a dialog-themed window resets its size when content is attached
        if (apps) Http.background {
            val order = Prefs.appOrder
            val all = AppRepository.load(this).sortedBy { order.indexOf(it.component.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            Http.post { fill(all.map { it.component.packageName to it.label }, Prefs.appHidden) { o, h -> Prefs.appOrder = o; Prefs.appHidden = h } }
        } else {
            val titles = listOf("Continue watching", "Airing next") + Catalogs.browseRows(null, 8).map { (_, c) -> Catalogs.rowTitle(null, c) } + "Your apps"
            fill(titles.distinct().sortedBy { Prefs.homeRank(it) }.map { it to it }, Prefs.homeHidden) { o, h -> Prefs.homeOrder = o; Prefs.homeHidden = h }
        }
    }

    private fun fill(keys: List<Pair<String, String>>, hidden: List<String>, onSave: (List<String>, List<String>) -> Unit) {
        save = onSave
        keys.forEach { (k, n) -> items += Item(k, n, k in hidden) }
        items.forEach { list.addView(rowFor(it), Ui.matchWrap().apply { topMargin = Ui.dp(this@ArrangeActivity, 8) }) }
        list.getChildAt(0)?.let { ((it as LinearLayout).getChildAt(2)).requestFocus() }   // first Down button: Up would do nothing there
    }

    private fun rowFor(item: Item): View {
        val c = this
        val name = Ui.text(c, item.name, 16f, if (item.hidden) Ui.DIM else Ui.TEXT, true, 1)
        val row = Ui.hbox(c, 8).apply { setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8)); Ui.rowStyle(c, this); tag = item }
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val hide = Ui.button(c, if (item.hidden) "Show" else "Hide") {}
        fun refreshLook() { name.setTextColor(if (item.hidden) Ui.DIM else Ui.TEXT); hide.text = if (item.hidden) "Show" else "Hide" }
        val up = Ui.button(c, "▲") { move(row, -1) }
        val down = Ui.button(c, "▼") { move(row, 1) }
        hide.setOnClickListener { item.hidden = !item.hidden; refreshLook(); persist() }
        row.addView(up); row.addView(down); row.addView(hide)
        return row
    }

    /** Swaps the row with its neighbour and puts focus back on the button that was pressed. */
    private fun move(row: View, d: Int) {
        val i = list.indexOfChild(row); val j = i + d
        if (j < 0 || j >= list.childCount) return
        val pressed = row.findFocus()
        list.removeViewAt(i); list.addView(row, j)
        items.add(j, items.removeAt(i))
        pressed?.requestFocus()
        persist()
    }

    private fun persist() = save(items.map { it.key }, items.filter { it.hidden }.map { it.key })
}
