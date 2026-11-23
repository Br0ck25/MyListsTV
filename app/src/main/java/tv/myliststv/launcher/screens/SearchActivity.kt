package tv.myliststv.launcher.screens

import tv.myliststv.launcher.services.*

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/** Search across the catalog addons. Type with the system keyboard; results fill a poster grid. */
class SearchActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var wanted = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = EditText(this).apply {
            hint = "Search movies and series"; setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); Ui.size(this, 20f)
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true); background = null
        }
        // The field is a rounded pill with a search icon; it lights up with a white ring while you type.
        val field = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@SearchActivity, 18), Ui.dp(this@SearchActivity, 6), Ui.dp(this@SearchActivity, 18), Ui.dp(this@SearchActivity, 6))
            background = Ui.rounded(this@SearchActivity, Ui.CARD, Ui.R_PILL)
            addView(android.widget.ImageView(this@SearchActivity).apply { setImageResource(tv.myliststv.launcher.R.drawable.ic_search); setColorFilter(Ui.DIM) }, android.widget.LinearLayout.LayoutParams(Ui.dp(this@SearchActivity, 26), Ui.dp(this@SearchActivity, 26)).apply { rightMargin = Ui.dp(this@SearchActivity, 12) })
            addView(box, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.setOnFocusChangeListener { _, f -> field.background = Ui.rounded(this, if (f) Ui.ELEVATED else Ui.CARD, Ui.R_PILL, if (f) Ui.TEXT else Ui.CLEAR) }
        val status = Ui.text(this, "Type at least 2 letters.", 13f, Ui.DIM)
        val grid = RecyclerView(this).apply { layoutManager = GridLayoutManager(this@SearchActivity, if (Prefs.horizontalPosters) 4 else 8); itemAnimator = null }
        val root = Ui.vbox(this).apply { setBackgroundColor(Ui.BG); setPadding(Ui.dp(this@SearchActivity, 48), Ui.dp(this@SearchActivity, 20), Ui.dp(this@SearchActivity, 48), 0) }
        root.addView(field, Ui.matchWrap()); root.addView(status, Ui.wrap().apply { topMargin = Ui.dp(this@SearchActivity, 10); leftMargin = Ui.dp(this@SearchActivity, 18) })
        root.addView(grid, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        box.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim(); wanted = q
                handler.removeCallbacksAndMessages(null)
                if (q.length < 2) return
                handler.postDelayed({
                    status.text = "Searching…"
                    Catalogs.search(q) { list ->
                        if (q != wanted) return@search
                        status.text = if (list.isEmpty()) "No matches for “$q”." else "${list.size} results"
                        grid.adapter = CardAdapter(if (Prefs.horizontalPosters) CardKind.WIDE else CardKind.POSTER, list.filter(Profiles::allows).map { if (Prefs.horizontalPosters) CardModel(it.name, it.background ?: it.poster, it.year, payload = it) else CardModel(it.name, it.poster, payload = it) }, Prefs.lite(), { }, { m ->
                            startActivity(Intent(this@SearchActivity, DetailsActivity::class.java).putExtra("meta", Json.metaToJson(m.payload as Meta)))
                        })
                    }
                }, 450)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }
}
