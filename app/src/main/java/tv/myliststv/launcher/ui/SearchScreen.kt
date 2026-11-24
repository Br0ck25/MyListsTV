package tv.myliststv.launcher.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.R
import tv.myliststv.launcher.data.*

/**
 * "Search" as a page like the others: a field to type in with the system keyboard. Until something is typed, trending movies and
 * series sit below it; then the matches from the catalog addons replace them in a poster grid.
 */
class SearchScreen(private val act: Activity, private val onMeta: (Meta) -> Unit, private val onBackdrop: (String?) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private var wanted = ""
    private val box = EditText(act).apply {
        hint = "Search movies and series"; setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); Ui.size(this, 20f)
        inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true); background = null
    }
    private val status = Ui.text(act, "Trending now", 13f, Ui.DIM)
    private val wide = Prefs.horizontalPosters
    private val grid = RecyclerView(act).apply {
        layoutManager = GridLayoutManager(act, Ui.columns(act, if (wide) 208 else 118)); itemAnimator = null; clipToPadding = false
        setPadding(Ui.dp(act, 42), Ui.dp(act, 6), Ui.dp(act, 42), Ui.dp(act, 24)); visibility = View.GONE
    }
    private val holder = FrameLayout(act)
    private var trending: RowsList? = null

    val view: View = Ui.vbox(act).also { root ->
        // The field is a rounded pill with a search icon; it lights up with a white ring while you type.
        val field = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(act, 18), Ui.dp(act, 6), Ui.dp(act, 18), Ui.dp(act, 6))
            background = Ui.rounded(act, Ui.CARD, Ui.R_PILL)
            addView(ImageView(act).apply { setImageResource(R.drawable.ic_search); setColorFilter(Ui.DIM) }, LinearLayout.LayoutParams(Ui.dp(act, 26), Ui.dp(act, 26)).apply { rightMargin = Ui.dp(act, 12) })
            addView(box, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.setOnFocusChangeListener { _, f -> field.background = Ui.rounded(act, if (f) Ui.ELEVATED else Ui.CARD, Ui.R_PILL, if (f) Ui.TEXT else Ui.CLEAR) }
        root.addView(field, Ui.matchWrap().apply { leftMargin = Ui.dp(act, 48); rightMargin = Ui.dp(act, 48); topMargin = Ui.dp(act, 10) })
        root.addView(status, Ui.wrap().apply { topMargin = Ui.dp(act, 10); leftMargin = Ui.dp(act, 66) })
        holder.addView(grid, FrameLayout.LayoutParams(-1, -1))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun card(m: Meta) = if (wide) CardModel(m.name, m.background ?: m.poster, m.year, payload = m, done = Watched.titleDone(m)) else CardModel(m.name, m.poster, payload = m, done = Watched.titleDone(m))

    /** Trending movies and series from the first catalog of each kind, as rows under the field. */
    private fun loadTrending() {
        val defs = listOf("movie" to "Trending · Movies", "series" to "Trending · Series")
            .mapNotNull { (type, title) -> Catalogs.browseRows(type, 1).firstOrNull()?.let { (a, c) -> Triple(title, a, c) } }
        if (defs.isEmpty()) { status.text = "Type at least 2 letters."; return }
        val rows = defs.map { (t, a, _) -> RowModel(t, "via " + if (a.name.startsWith("My Lists")) "My Lists" else a.name, if (wide) CardKind.WIDE else CardKind.POSTER, emptyList(), loaded = false) }
        val list = RowsList(act, rows, Prefs.lite(), { m -> (m.payload as? Meta)?.let { onBackdrop(it.background ?: it.poster) } }, { m -> (m.payload as? Meta)?.let(onMeta) })
        trending = list
        holder.addView(list, 0, FrameLayout.LayoutParams(-1, -1))
        list.visibility = if (grid.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        defs.forEachIndexed { i, (_, a, c) ->
            Catalogs.load(a, c) { metas, _ ->
                if (metas != null) { rows[i].items = metas.filter(Profiles::allows).take(30).map(::card); rows[i].loaded = true; list.changed(i) }
            }
        }
    }

    fun start() {
        loadTrending()
        box.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim(); wanted = q
                handler.removeCallbacksAndMessages(null)
                if (q.length < 2) {
                    status.text = if (trending != null) "Trending now" else "Type at least 2 letters."
                    grid.visibility = View.GONE; trending?.visibility = View.VISIBLE
                    return
                }
                handler.postDelayed({
                    status.text = "Searching…"
                    Catalogs.search(q) { list ->
                        if (q != wanted) return@search
                        status.text = if (list.isEmpty()) "No matches for “$q”." else "${list.size} results"
                        grid.visibility = View.VISIBLE; trending?.visibility = View.GONE
                        grid.adapter = CardAdapter(if (wide) CardKind.WIDE else CardKind.POSTER, list.filter(Profiles::allows).map(::card), Prefs.lite(),
                            { m -> (m.payload as? Meta)?.let { onBackdrop(it.background ?: it.poster) } }, { m -> (m.payload as? Meta)?.let(onMeta) })
                    }
                }, 450)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    fun stop() { handler.removeCallbacksAndMessages(null) }
}
