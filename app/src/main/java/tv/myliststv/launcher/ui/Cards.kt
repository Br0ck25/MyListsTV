package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

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

enum class CardKind { POSTER, WIDE, APP, EPISODE }

data class CardModel(
    val title: String,
    val image: String?,
    val sub: String? = null,
    val progress: Float = 0f,
    val payload: Any? = null,
    val icon: android.graphics.drawable.Drawable? = null,
    /** For apps: true when [icon] is a wide TV banner that fills the whole tile. */
    val banner: Boolean = false,
    /** A small label in the top corner, e.g. "EPISODE 6". */
    val badge: String? = null,
    /** An episode the viewer has finished: shows a tick in the corner. */
    val done: Boolean = false,
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
    val subLabel = Ui.text(c, "", 11f, Ui.TEXT2, false, 1)
    val bar = View(c).apply { setBackgroundColor(Ui.ACCENT); visibility = GONE }
    val badge = Ui.text(c, "", 11f, Ui.TEXT, true, 1).apply {
        setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2)); background = Ui.rounded(c, Ui.SCRIM_SLATE, Ui.R_PILL)
    }
    val tick = ImageView(c).apply {
        setImageResource(tv.myliststv.launcher.R.drawable.ic_check); setColorFilter(Ui.TEXT)
        val p = Ui.dp(c, 4); setPadding(p, p, p, p); background = Ui.rounded(c, Ui.BRAND_FILL, Ui.R_PILL); visibility = GONE
    }
    val appIcon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private var wPx = 0; private var hPx = 0

    init {
        // App tiles are the same size as wide cards; the size setting scales all of them together.
        val (bw, bh) = when (kind) { CardKind.POSTER -> 118 to 177; CardKind.EPISODE -> 300 to 170; else -> 208 to 117 }
        val w = (Ui.dp(c, bw) * Prefs.cardScale).toInt()
        val h = (Ui.dp(c, bh) * Prefs.cardScale).toInt()
        wPx = w; hPx = h
        layoutParams = RecyclerView.LayoutParams(w, h).also { it.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6)) }
        isFocusable = true; isClickable = true
        background = Ui.rounded(c, Ui.CARD, Ui.R_CARD); clipToOutline = true
        if (kind == CardKind.APP) {
            addView(appIcon, LayoutParams((Ui.dp(c, 48) * Prefs.cardScale).toInt(), (Ui.dp(c, 48) * Prefs.cardScale).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(c, 14) })
            label.gravity = Gravity.CENTER; label.maxLines = 2
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), Ui.dp(c, 8)) })
        } else {
            addView(img, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val ep = kind == CardKind.EPISODE
            val shade = View(c).apply { background = Ui.scrimShade() }
            addView(shade, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, if (ep) 120 else 70), Gravity.BOTTOM))
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), if (ep) Ui.dp(c, 44) else if (kind == CardKind.WIDE) Ui.dp(c, 22) else Ui.dp(c, 8)) })
            if (ep) {
                subLabel.maxLines = 2
                addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0) })
                addView(tick, LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.TOP or Gravity.END).apply { setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0) })
            }
            if (kind == CardKind.WIDE || ep) {
                addView(subLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 8)) })
                addView(bar, LayoutParams(0, Ui.dp(c, 3), Gravity.BOTTOM or Gravity.START))
            }
        }
        foreground = Ui.focusBg(c, Ui.R_CARD, Ui.CLEAR)
    }

    fun bind(m: CardModel) {
        if (kind == CardKind.APP) {
            // A banner is a wide picture that fills the tile; an app without one gets its icon above its name.
            appIcon.setImageDrawable(m.icon)
            appIcon.scaleType = if (m.banner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            appIcon.layoutParams = if (m.banner) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                else LayoutParams((Ui.dp(context, 48) * Prefs.cardScale).toInt(), (Ui.dp(context, 48) * Prefs.cardScale).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(context, 14) }
            label.text = m.title; label.visibility = if (m.banner) GONE else VISIBLE
            return
        }
        // A poster already shows its art with the title baked in for most catalogs; keep the title only when there is no image.
        label.text = if (kind == CardKind.POSTER && !m.image.isNullOrEmpty()) "" else m.title
        subLabel.text = m.sub ?: ""
        ImageLoader.load(img, m.image, wPx, hPx)
        tick.visibility = if (m.done) VISIBLE else GONE
        badge.text = m.badge ?: ""; badge.visibility = if (m.badge == null) GONE else VISIBLE
        if (kind == CardKind.WIDE || kind == CardKind.EPISODE) {
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

    fun payloads(): List<Any?> = items.map { it.payload }

    /** Moves one card to another place (the list is changed in place, so the row's own model stays in step). */
    fun move(from: Int, to: Int) {
        val l = items as? MutableList<CardModel> ?: items.toMutableList().also { items = it }
        l.add(to, l.removeAt(from)); notifyItemMoved(from, to)
    }

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
            if (f) (v.parent as? RecyclerView)?.setTag(tv.myliststv.launcher.R.id.tag_row_pos, h.bindingAdapterPosition)
            if (!lite) v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(110).start()
            if (f) onFocus(m)
        }
    }

    override fun getItemCount() = items.size
}

/** A titled row: heading, optional source label, horizontal strip. */
class RowView(c: Context) : LinearLayout(c) {
    val title = Ui.text(c, "", 16f, Ui.TEXT, true)
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

/** A vertical list of [RowView]s over [rows]. Used by Home, Movies and Series so the row code exists once. */
class RowsList(
    c: Context,
    private val rows: List<RowModel>,
    private val lite: Boolean,
    private val onFocus: (CardModel) -> Unit,
    private val onClick: (CardModel) -> Unit,
    private val onLong: ((CardModel) -> Unit)? = null,
) : RecyclerView(c) {
    init { layoutManager = LinearLayoutManager(c); itemAnimator = null; clipToPadding = false; setPadding(0, 0, 0, Ui.dp(c, 56)); setItemViewCacheSize(3); adapter = A() }

    /** Going to the first row shows it whole (its title included), not cut off at the top. */
    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        // Only when it is not already at the top: scrolling re-requests focus, which would otherwise loop.
        if (child != null && getChildAdapterPosition(child) == 0 && computeVerticalScrollOffset() != 0) post { (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) }
    }

    /** Up and down land on the card you last had focus on in the next row (its first card if you have not been there). */
    override fun focusSearch(focused: View, direction: Int): View? {
        if (direction == FOCUS_UP || direction == FOCUS_DOWN) {
            var row: View = focused
            while (row.parent != null && row.parent !== this) row = row.parent as View
            val next = getChildAt(indexOfChild(row) + if (direction == FOCUS_DOWN) 1 else -1) as? RowView
            if (next != null && next.visibility == VISIBLE) {
                // The card you were last on in that row, or its first card if you have not been there.
                val pos = (next.strip.getTag(tv.myliststv.launcher.R.id.tag_row_pos) as? Int) ?: 0
                val hit = next.strip.layoutManager?.findViewByPosition(pos)?.takeIf { it.isFocusable } ?: (0 until next.strip.childCount).map { next.strip.getChildAt(it) }.firstOrNull { it.isFocusable }
                if (pos > 0 && hit != null) next.strip.scrollToPosition(pos)
                if (hit != null) return hit
            }
        }
        return super.focusSearch(focused, direction)
    }

    fun changed(i: Int) = adapter!!.notifyItemChanged(i)
    fun changedAll() = adapter!!.notifyDataSetChanged()
    fun inserted(i: Int) = adapter!!.notifyItemInserted(i)

    private inner class A : Adapter<A.H>() {
        inner class H(val row: RowView) : ViewHolder(row)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = H(RowView(parent.context))
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(h: H, position: Int) {
            val m = rows[position]
            val show = m.items.isNotEmpty()
            h.row.visibility = if (show) VISIBLE else GONE
            h.row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, if (show) LayoutParams.WRAP_CONTENT else 0)
            h.row.title.text = m.title
            h.row.source.text = m.source ?: ""
            h.row.strip.adapter = CardAdapter(m.kind, m.items, lite, onFocus, onClick, onLong)
        }
    }
}
