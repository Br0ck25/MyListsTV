package tv.myliststv.launcher.screens

import tv.myliststv.launcher.*

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Reorder Home Catalogs (or the apps row): a page of cards, each with its name, where it comes from, an on/off toggle and a
 * handle. OK on the toggle shows or hides the row on Home. OK on the handle picks the card up; Up and Down then move it and OK
 * (or Back) puts it down. Everything saves at once, in place, so a run of moves never reloads or jumps back to the top.
 */
class ArrangeActivity : Activity() {
    private class Item(val key: String, val name: String, val sub: String, var hidden: Boolean)

    private lateinit var list: LinearLayout
    private val items = ArrayList<Item>()
    private var save: (List<String>, List<String>) -> Unit = { _, _ -> }
    private var carrying: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = intent.getStringExtra("what") == "apps"
        val c = this
        list = Ui.vbox(c).apply { setPadding(Ui.dp(c, 14), Ui.dp(c, 3), Ui.dp(c, 14), Ui.dp(c, 16)) }
        // The same drawer as Settings: a back arrow with the title, the line under it, then the cards.
        val page = Ui.vbox(c)
        page.addView(SidePanel.backRow(c, if (apps) "Reorder Apps Row" else "Reorder Home Catalogs") { onBackPressed() }, Ui.matchWrap().apply { setMargins(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), 0) })
        page.addView(Ui.text(c, if (apps) "This controls the order of the apps on Home. The Apps page still shows all of them." else "This controls row order on Home. OK on a handle picks a row up; Up and Down move it.", 14f, Ui.DIM).apply { setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { setMargins(Ui.dp(c, 28), Ui.dp(c, 6), Ui.dp(c, 28), Ui.dp(c, 6)) })
        page.addView(ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false; clipToPadding = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(SidePanel.drawer(c, page))
        if (apps) Http.background {
            val order = Prefs.appOrder
            val all = AppRepository.load(this).sortedBy { order.indexOf(it.component.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            Http.post { fill(all.map { Triple(it.component.packageName, it.label, "Installed app") }, Prefs.appHidden) { o, h -> Prefs.appOrder = o; Prefs.appHidden = h } }
        } else {
            val fixed = mapOf("Continue Watching" to "Your progress", "Airing Next" to "Next episodes of the shows you follow", "Your Apps" to "Installed apps")
            val source = Catalogs.browseRows(null, 8).associate { (a, cat) -> Catalogs.rowTitle(null, cat) to (if (a.name.startsWith("My Lists")) "My Lists" else a.name) }
            val titles = listOf("Continue Watching", "Airing Next") + Catalogs.browseRows(null, 8).map { (_, cat) -> Catalogs.rowTitle(null, cat) } + "Your Apps"
            fill(titles.distinct().sortedBy { Prefs.homeRank(it) }.map { Triple(it, it, fixed[it] ?: source[it] ?: "Catalog") }, Prefs.homeHidden) { o, h -> Prefs.homeOrder = o; Prefs.homeHidden = h }
        }
    }

    private fun fill(keys: List<Triple<String, String, String>>, hidden: List<String>, onSave: (List<String>, List<String>) -> Unit) {
        save = onSave
        keys.forEach { (k, n, s) -> items += Item(k, n, s, k in hidden) }
        items.forEach { list.addView(rowFor(it), Ui.matchWrap().apply { topMargin = Ui.dp(this@ArrangeActivity, 10) }) }
        list.getChildAt(0)?.let { ((it as LinearLayout).getChildAt(1)).requestFocus() }
    }

    private fun rowFor(item: Item): View {
        val c = this
        val row = Ui.hbox(c, 12).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12)); background = Ui.rounded(c, Ui.FILL, Ui.R_PANEL); tag = item }
        val words = Ui.vbox(c)
        val name = Ui.text(c, item.name, 16f, Ui.TEXT, false, 1)
        words.addView(name)
        words.addView(Ui.text(c, item.sub, 13f, Ui.DIM, false, 1), Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Toggle(c, !item.hidden)
        val tHolder = Ui.hbox(c).apply {
            gravity = Gravity.CENTER; setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8))
            background = Ui.pillBg(c, Ui.CLEAR, Ui.FOCUS_FILL); isFocusable = true; isClickable = true
            addView(toggle)
            setOnClickListener { item.hidden = !item.hidden; toggle.on = !item.hidden; toggle.invalidate(); name.alpha = if (item.hidden) 0.5f else 1f; persist() }
        }
        name.alpha = if (item.hidden) 0.5f else 1f
        val handle = ImageView(c).apply {
            setImageResource(R.drawable.ic_menu); setColorFilter(Ui.DIM)
            val p = Ui.dp(c, 10); setPadding(p, p, p, p)
            background = Ui.pillBg(c, Ui.CLEAR, Ui.FOCUS_FILL); isFocusable = true; isClickable = true
            setOnClickListener { carrying = if (carrying === row) null else row; refreshCarry() }
            setOnKeyListener { _, code, ev ->
                if (carrying !== row || ev.action != KeyEvent.ACTION_DOWN) false
                else when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> { move(row, -1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { move(row, 1); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
        }
        row.addView(tHolder); row.addView(handle, LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        return row
    }

    /** The card being carried is outlined in the brand colour. */
    private fun refreshCarry() {
        for (i in 0 until list.childCount) {
            val r = list.getChildAt(i)
            r.background = if (r === carrying) Ui.rounded(this, Ui.FILL, Ui.R_PANEL, Ui.BRAND) else Ui.rounded(this, Ui.FILL, Ui.R_PANEL)
        }
    }

    /** Swaps the card with its neighbour and keeps focus on its handle. */
    private fun move(row: View, d: Int) {
        val i = list.indexOfChild(row); val j = i + d
        if (j < 0 || j >= list.childCount) return
        val handle = (row as LinearLayout).getChildAt(2)
        list.removeViewAt(i); list.addView(row, j, Ui.matchWrap().apply { topMargin = Ui.dp(this@ArrangeActivity, 10) })
        items.add(j, items.removeAt(i))
        handle.requestFocus()
        persist()
    }

    private fun persist() = save(items.map { it.key }, items.filter { it.hidden }.map { it.key })

    @Deprecated("Back puts a carried card down first, then leaves.")
    override fun onBackPressed() { if (carrying != null) { carrying = null; refreshCarry() } else super.onBackPressed() }
}
