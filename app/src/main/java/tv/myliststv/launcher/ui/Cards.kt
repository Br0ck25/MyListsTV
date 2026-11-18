package tv.myliststv.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.ImageLoader
import tv.myliststv.launcher.data.Prefs

enum class CardKind { POSTER, WIDE, APP }

data class CardModel(
    val title: String,
    val image: String?,
    val sub: String? = null,
    val progress: Float = 0f,
    val payload: Any? = null,
    val icon: android.graphics.drawable.Drawable? = null,
)

data class RowModel(
    val title: String,
    val source: String?,
    val kind: CardKind,
    var items: List<CardModel>,
    var loaded: Boolean = true,
)

/** One tile. Posters and wide cards load their image at tile size only. */
class Card(c: Context, private val kind: CardKind) : FrameLayout(c) {
    val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val label = Ui.text(c, "", 13f, Ui.TEXT, true, 3)
    val subLabel = Ui.text(c, "", 11f, 0xFFD6DAE9.toInt(), false, 1)
    val bar = View(c).apply { setBackgroundColor(Ui.ACCENT); visibility = GONE }
    val appIcon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private var wPx = 0; private var hPx = 0

    init {
        val w: Int; val h: Int
        when (kind) {
            CardKind.POSTER -> { w = Ui.dp(c, 118); h = Ui.dp(c, 177) }
            CardKind.WIDE -> { w = Ui.dp(c, 208); h = Ui.dp(c, 117) }
            CardKind.APP -> { w = Ui.dp(c, 104); h = Ui.dp(c, 104) }
        }
        wPx = w; hPx = h
        layoutParams = RecyclerView.LayoutParams(w, h).also { it.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6)) }
        isFocusable = true; isClickable = true
        setBackgroundColor(Ui.CARD)
        if (kind == CardKind.APP) {
            addView(appIcon, LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(c, 12) })
            label.gravity = Gravity.CENTER; label.maxLines = 2
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), Ui.dp(c, 8)) })
        } else {
            addView(img, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val shade = View(c).apply { background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), Color.TRANSPARENT)) }
            addView(shade, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 70), Gravity.BOTTOM))
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), if (kind == CardKind.WIDE) Ui.dp(c, 22) else Ui.dp(c, 8)) })
            if (kind == CardKind.WIDE) {
                addView(subLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 8)) })
                addView(bar, LayoutParams(0, Ui.dp(c, 3), Gravity.BOTTOM or Gravity.START))
            }
        }
        foreground = Ui.focusBg(c, 6, 0x00000000)
    }

    fun bind(m: CardModel) {
        if (kind == CardKind.APP) {
            label.text = m.title; appIcon.setImageDrawable(m.icon); return
        }
        // A poster already shows its art with the title baked in for most catalogs; keep the title only when there is no image.
        label.text = if (kind == CardKind.POSTER && !m.image.isNullOrEmpty()) "" else m.title
        subLabel.text = m.sub ?: ""
        ImageLoader.load(img, m.image, wPx, hPx)
        if (kind == CardKind.WIDE) {
            bar.visibility = if (m.progress > 0f) VISIBLE else GONE
            (bar.layoutParams as LayoutParams).width = (wPx * m.progress).toInt()
            bar.requestLayout()
        }
    }
}

/** A horizontal strip of cards. */
class CardAdapter(
    private val kind: CardKind,
    private var items: List<CardModel>,
    private val lite: Boolean,
    private val onFocus: (CardModel) -> Unit,
    private val onClick: (CardModel) -> Unit,
    private val onLong: ((CardModel) -> Unit)? = null,
) : RecyclerView.Adapter<CardAdapter.H>() {

    class H(val card: Card) : RecyclerView.ViewHolder(card)

    fun set(newItems: List<CardModel>) { items = newItems; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
        val card = Card(parent.context, kind)
        Ui.focusScale(card, lite)
        return H(card)
    }

    override fun onBindViewHolder(h: H, position: Int) {
        val m = items[position]
        h.card.bind(m)
        h.card.setOnClickListener { onClick(m) }
        h.card.setOnLongClickListener { onLong?.invoke(m); onLong != null }
        h.card.setOnFocusChangeListener { v, f ->
            if (!lite) v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(110).start()
            if (f) onFocus(m)
        }
    }

    override fun getItemCount() = items.size
}

/** A titled row: heading, optional source label, horizontal strip. */
class RowView(c: Context) : LinearLayout(c) {
    val title = Ui.text(c, "", 17f, Ui.TEXT, true)
    val source = Ui.text(c, "", 12f, Ui.DIM)
    val strip = RecyclerView(c).apply {
        layoutManager = LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false)
        clipToPadding = false
        setPadding(Ui.dp(c, 42), Ui.dp(c, 4), Ui.dp(c, 42), Ui.dp(c, 4))
        itemAnimator = null
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        setItemViewCacheSize(4)
    }
    var adapter: CardAdapter? = null

    init {
        orientation = VERTICAL
        val head = Ui.hbox(c, 10)
        head.setPadding(Ui.dp(c, 48), Ui.dp(c, 6), Ui.dp(c, 48), 0)
        head.addView(title); head.addView(source)
        addView(head, Ui.matchWrap())
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
    }
}
