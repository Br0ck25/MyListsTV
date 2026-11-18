package tv.myliststv.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.*

/** "Library" as a page: every saved title in a grid, with All, Movies and Series filters. OK opens it, hold OK removes it. */
class LibraryScreen(private val act: Activity, private val onMeta: (Meta) -> Unit) {
    private val title = Ui.text(act, "", 24f, Ui.TEXT, true)
    private val empty = Ui.text(act, "Nothing saved yet. Press + on a title's page to keep it here.", 14f, Ui.DIM, false, 3)
    private val pills = Ui.hbox(act, 8)
    private val grid = RecyclerView(act)
    private var filter: String? = null
    private val wide = Prefs.horizontalPosters

    val view: View = Ui.vbox(act).also {
        title.setPadding(Ui.dp(act, 48), Ui.dp(act, 10), Ui.dp(act, 48), Ui.dp(act, 6))
        it.addView(title, Ui.matchWrap())
        pills.setPadding(Ui.dp(act, 42), 0, Ui.dp(act, 42), Ui.dp(act, 6))
        it.addView(pills, Ui.matchWrap())
        empty.setPadding(Ui.dp(act, 48), Ui.dp(act, 12), Ui.dp(act, 48), 0)
        it.addView(empty, Ui.matchWrap())
        // As many columns as fit: the card width (scaled by the card size setting) plus its margins.
        val cell = Ui.dp(act, if (wide) 220 else 130) * Prefs.cardScale
        grid.layoutManager = GridLayoutManager(act, ((act.resources.displayMetrics.widthPixels - Ui.dp(act, 84)) / cell).toInt().coerceIn(2, 10)); grid.itemAnimator = null; grid.clipToPadding = false
        grid.setPadding(Ui.dp(act, 42), Ui.dp(act, 4), Ui.dp(act, 42), Ui.dp(act, 56))
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
        title.text = "Library" + if (all.isNotEmpty()) "  ·  ${all.size}" else ""
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        pills.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        val models = items.map { if (wide) CardModel(it.name, it.background ?: it.poster, null, payload = it) else CardModel(it.name, it.poster, payload = it) }
        grid.adapter = CardAdapter(if (wide) CardKind.WIDE else CardKind.POSTER, models, Prefs.lite(), { }, { m -> (m.payload as? Meta)?.let(onMeta) }, { m ->
            (m.payload as? Meta)?.let { meta -> AlertDialog.Builder(act).setTitle(meta.name).setItems(arrayOf("Remove from Library")) { _, _ -> Library.remove(meta.id); show() }.show() }
        })
    }
}
