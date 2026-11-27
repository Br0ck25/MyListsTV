package tv.myliststv.launcher.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * One folder of a collection: a row for each list inside it (Disney+ Top 10, Movies, Series...). The rows fill in as their catalogs answer,
 * a few at a time, and a list with nothing in it leaves no empty row. OK on a title opens its page.
 */
class CollectionActivity : Activity() {
    private val rows = ArrayList<RowModel>()
    private lateinit var list: RowsList
    private lateinit var note: android.widget.TextView
    private var shown = 0
    private var pending = 0
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val folder = Collections.opened ?: run { finish(); return }
        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(View(c).apply { background = Ui.wash() }, FrameLayout.LayoutParams(-1, -1))
        val page = Ui.vbox(c)
        val head = Ui.vbox(c).apply { setPadding(Ui.dp(c, 48), Ui.dp(c, 28), Ui.dp(c, 48), Ui.dp(c, 8)) }
        head.addView(Ui.text(c, folder.title, 34f, Ui.TEXT, true, 1))
        head.addView(Ui.text(c, "Collection", 14f, Ui.ACCENT, true), Ui.wrap().apply { topMargin = Ui.dp(c, 2) })
        note = Ui.text(c, "Loading…", 14f, Ui.DIM)
        head.addView(note, Ui.wrap().apply { topMargin = Ui.dp(c, 6) })
        page.addView(head, Ui.matchWrap())
        list = RowsList(c, rows, Prefs.lite(), { }, { m -> (m.payload as? Meta)?.let { startActivity(Intent(c, DetailsActivity::class.java).putExtra("meta", Json.metaToJson(it))) } })
        page.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        load(folder)
    }

    /** Asks for the folder's lists three at a time, so a folder with dozens does not send them all at once. */
    private fun load(folder: Collections.Folder) {
        val queue = ArrayDeque(folder.sources)
        pending = queue.size
        val wide = Prefs.horizontalPosters
        fun next() {
            val s = queue.removeFirstOrNull() ?: return
            var counted = false   // an answer may come twice (the saved copy, then a fresh one), but the list is counted once
            Http.get(Urls.catalog(Collections.baseFor(s), s.type, s.catalogId), 30L * 60_000) { body, fromCache, _ ->
                val metas = body?.let { try { Json.parseMetas(it).filter(Profiles::allows) } catch (_: Exception) { null } }.orEmpty()
                val title = Collections.rowName(s, folder.sources)
                val at = rows.indexOfFirst { it.title == title }
                if (metas.isNotEmpty()) {
                    val items = metas.take(30).map { if (wide) CardModel(it.name, it.background ?: it.poster, listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString("  ·  "), payload = it, done = Watched.titleDone(it)) else CardModel(it.name, it.poster, payload = it, done = Watched.titleDone(it)) }
                    if (at >= 0) { rows[at].items = items; list.changed(at) }
                    else { rows += RowModel(title, null, if (wide) CardKind.WIDE else CardKind.POSTER, items); list.inserted(rows.size - 1); shown++ }
                    note.visibility = View.GONE
                } else if (at >= 0 && !fromCache) { rows.removeAt(at); list.removed(at); shown-- }
                if (!counted) { counted = true; pending--; next(); if (pending <= 0) done() }
            }
        }
        repeat(3) { next() }
    }

    private fun done() {
        if (finished) return
        finished = true
        if (shown == 0) { note.text = "Nothing in this collection yet."; note.visibility = View.VISIBLE }
    }
}
