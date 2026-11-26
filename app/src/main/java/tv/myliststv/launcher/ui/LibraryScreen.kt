package tv.myliststv.launcher.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.*

/** "Library" as a page: every saved title in a grid, with All, Movies and Series filters. OK opens it, hold OK removes it. */
class LibraryScreen(private val act: Activity, private val onMeta: (Meta) -> Unit, private val onBackdrop: (String?) -> Unit = {}) {
    private val empty = Ui.text(act, "Nothing saved yet. Press + on a title's page to keep it here.", 14f, Ui.DIM, false, 3)
    private val pills = Ui.hbox(act, 8)
    private val grid = RecyclerView(act)
    private var filter: String? = null
    private val wide = Prefs.horizontalPosters

    val view: View = Ui.vbox(act).also {
        // No page title: the Library tab already says where you are, as it does on Movies and Series.
        pills.setPadding(Ui.dp(act, 42), Ui.dp(act, 14), Ui.dp(act, 42), Ui.dp(act, 6))
        it.addView(pills, Ui.matchWrap())
        empty.setPadding(Ui.dp(act, 48), Ui.dp(act, 12), Ui.dp(act, 48), 0)
        it.addView(empty, Ui.matchWrap())
        // The same card size, spacing and edges as the rows on Home. The top space leaves room for the selected card, which grows, to show whole under the filter buttons.
        CardGrid.apply(grid, act, if (wide) 208 else 118, 24, 56)
        grid.clipChildren = false; it.clipChildren = false
        it.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun start() {
        pills.removeAllViews()
        listOf<Pair<String?, String>>(null to "All", "movie" to "Movies", "series" to "Series").forEach { (f, label) ->
            lateinit var b: View
            b = Ui.button(act, label) { filter = f; show(); for (k in 0 until pills.childCount) pills.getChildAt(k).isSelected = pills.getChildAt(k) === b }.also { it.isSelected = f == filter }
            pills.addView(b)
        }
        show()
    }

    private fun show() {
        val all = Library.all().filter { Profiles.allows(it) }
        val items = all.filter { filter == null || it.type == filter }
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        pills.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        val models = items.map { if (wide) CardModel(it.name, it.background ?: it.poster, null, payload = it, done = Watched.titleDone(it)) else CardModel(it.name, it.poster, payload = it, done = Watched.titleDone(it)) }
        grid.adapter = CardAdapter(if (wide) CardKind.WIDE else CardKind.POSTER, models, Prefs.lite(), { m -> (m.payload as? Meta)?.let { onBackdrop(it.background ?: it.poster) } }, { m -> (m.payload as? Meta)?.let(onMeta) }, null, null) { show() }
    }
}
