package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.content.Context
import android.os.Build
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
import tv.myliststv.launcher.data.Meta
import tv.myliststv.launcher.data.Prefs
import tv.myliststv.launcher.data.Watched

/** How much a card grows when it has focus: it rises above its neighbours. */
private const val LIFT = 1.15f

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
    /** A small label in the top-left corner, e.g. "EPISODE 6" or "3 episodes left". May be filled in after the card is shown. */
    var badge: String? = null,
    /** A small label in the top-right corner of a wide card, e.g. "45 min left" or "Airs in 3 days". */
    var badgeEnd: String? = null,
    /** An episode the viewer has finished: shows a tick in the corner. */
    var done: Boolean = false,
)

data class RowModel(
    val title: String,
    val source: String?,
    val kind: CardKind,
    var items: List<CardModel>,
    var loaded: Boolean = true,
) {
    /** The strip showing this row, so a card can be redrawn on its own without rebuilding the row (and losing focus). */
    var adapter: CardAdapter? = null

    /** The card focus was last on in this row, so Up and Down come back to it (0 when focus has never been in the row). */
    var lastPos = 0
}

/** One tile. Posters and wide cards load their image at tile size only. */
class Card(c: Context, private val kind: CardKind) : FrameLayout(c) {
    val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val label = Ui.text(c, "", 13f, Ui.TEXT, true, 3)
    val subLabel = Ui.text(c, "", 11f, Ui.TEXT2, false, 1)
    val bar = View(c).apply { setBackgroundColor(Ui.ACCENT); visibility = GONE }
    val badge = Ui.text(c, "", 11f, Ui.TEXT, true, 1).apply {
        setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2)); background = Ui.rounded(c, Ui.SCRIM_SLATE, Ui.R_PILL)
    }
    val badgeEnd = Ui.text(c, "", 11f, Ui.TEXT, true, 1).apply {
        setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2)); background = Ui.rounded(c, Ui.SCRIM_SLATE, Ui.R_PILL)
    }
    val tick = ImageView(c).apply {
        setImageResource(tv.myliststv.launcher.R.drawable.ic_check); setColorFilter(Ui.TEXT)
        val p = Ui.dp(c, 4); setPadding(p, p, p, p); background = Ui.rounded(c, Ui.BRAND_FILL, Ui.R_PILL); visibility = GONE
    }
    val appIcon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private var wPx = 0; private var hPx = 0

    /** Both corner badges on a wide card must fit its top edge. When they do not (a big card size), the right one drops its leading word ("Airs in 3 Days" becomes "in 3 Days"), and only then is it cut. */
    private fun fitBadges(m: CardModel) {
        val left = m.badge; val right = m.badgeEnd
        if (left == null || right == null) { badge.maxWidth = wPx; badgeEnd.maxWidth = wPx; return }
        val room = wPx - Ui.dp(context, 8) * 3   // the margin at each end and a gap between
        fun width(t: TextView, s: String) = t.paint.measureText(s) + t.paddingLeft + t.paddingRight
        badge.maxWidth = width(badge, left).toInt() + 2
        var text = right
        if (width(badge, left) + width(badgeEnd, text) > room && text.startsWith("Airs ")) text = text.removePrefix("Airs ")
        badgeEnd.text = text
        badgeEnd.maxWidth = (room - width(badge, left)).toInt().coerceAtLeast(Ui.dp(context, 40))
    }

    init {
        // App tiles are the same size as wide cards; the size setting scales all of them together.
        val (bw, bh) = when (kind) { CardKind.POSTER -> 118 to 177; CardKind.EPISODE -> 300 to 170; else -> 208 to 117 }
        val w = (Ui.dp(c, bw) * Prefs.cardScale).toInt()
        val h = (Ui.dp(c, bh) * Prefs.cardScale).toInt()
        wPx = w; hPx = h
        // The two corner badges share the top edge; bind() fits them side by side whatever the card size.
        badge.maxWidth = w; badgeEnd.maxWidth = w
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
            }
            addView(tick, LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.TOP or Gravity.END).apply { setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0) })
            if (kind == CardKind.WIDE) {
                addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(Ui.dp(c, 8), Ui.dp(c, 8), 0, 0) })
                addView(badgeEnd, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0) })
            }
            if (kind == CardKind.WIDE || ep) {
                addView(subLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 8)) })
                addView(bar, LayoutParams(0, Ui.dp(c, 5), Gravity.BOTTOM or Gravity.START))
            }
        }
        foreground = Ui.focusBg(c, Ui.R_CARD, Ui.CLEAR)
        if (Build.VERSION.SDK_INT >= 28) outlineSpotShadowColor = Ui.BRAND   // the shadow a raised card casts glows in the highlight colour
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
        tick.visibility = if (m.done || (m.payload as? Meta)?.let { Watched.titleDone(it) } == true) VISIBLE else GONE
        badge.text = m.badge ?: ""; badge.visibility = if (m.badge == null) GONE else VISIBLE
        badgeEnd.text = m.badgeEnd ?: ""; badgeEnd.visibility = if (m.badgeEnd == null) GONE else VISIBLE
        fitBadges(m)
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
    private val onPos: ((Int) -> Unit)? = null,
    /** A title was added to or taken off the Library from its hold-OK menu: a screen that lists the Library redraws itself. */
    private val onTitleChanged: (() -> Unit)? = null,
) : RecyclerView.Adapter<CardAdapter.H>() {

    class H(val card: Card) : RecyclerView.ViewHolder(card)

    fun set(newItems: List<CardModel>) { items = newItems; notifyDataSetChanged() }

    fun payloads(): List<Any?> = items.map { it.payload }

    /** Changes one card's model and redraws just it (the card keeps the focus). */
    fun refresh(i: Int, change: (CardModel) -> Unit) { items.getOrNull(i)?.let { change(it); notifyItemChanged(i) } }

    /** Takes one card out of the strip where it is, so the rest slide along and nothing else reloads. */
    fun removeAt(i: Int) { if (i !in items.indices) return; items = items.toMutableList().also { it.removeAt(i) }; notifyItemRemoved(i) }

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
        h.card.setOnLongClickListener {
            val meta = m.payload as? Meta
            if (meta != null) { TitleMenu.show(h.card.context as android.app.Activity, meta, { onClick(m) }) { m.done = Watched.titleDone(meta); onTitleChanged?.invoke() ?: notifyItemChanged(h.bindingAdapterPosition) }; true }
            else { onLong?.invoke(m); onLong != null }
        }
        h.card.setOnFocusChangeListener { v, f ->
            if (f) { (v.parent as? RecyclerView)?.setTag(tv.myliststv.launcher.R.id.tag_row_pos, h.bindingAdapterPosition); onPos?.invoke(h.bindingAdapterPosition) }
            if (!lite) v.animate().scaleX(if (f) LIFT else 1f).scaleY(if (f) LIFT else 1f).translationZ(if (f) Ui.dpf(v.context, 12f) else 0f).setDuration(110).start()
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
        setPadding(Ui.dp(c, 42), Ui.dp(c, 12), Ui.dp(c, 42), Ui.dp(c, 12))   // room for the focused card to rise
        clipChildren = false
        itemAnimator = null
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        setItemViewCacheSize(4)
    }
    var adapter: CardAdapter? = null

    init {
        orientation = VERTICAL; clipChildren = false
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
    init { layoutManager = LinearLayoutManager(c); itemAnimator = null; clipToPadding = false; clipChildren = false; setPadding(0, 0, 0, Ui.dp(c, 56)); setItemViewCacheSize(3); adapter = A() }

    /** Landing on a row shows it whole, title included: the first row goes to the very top, any other row is scrolled until its heading is in view. */
    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child == null) return
        getChildAdapterPosition(child).takeIf { it >= 0 }?.let { lastRow = it }
        // Only when it is not already in place: scrolling re-requests focus, which would otherwise loop.
        if (getChildAdapterPosition(child) == 0 && computeVerticalScrollOffset() != 0) post { smoothScrollBy(0, -computeVerticalScrollOffset()) }   // the same easing as scrolling down, not a jump
        else if (child.top < 0) post { if (child.isAttachedToWindow && child.top < 0) smoothScrollBy(0, child.top) }
    }

    /** The row (a direct child of this list) that holds [v]. */
    private fun rowOf(v: View): View? { var r: View = v; while (r.parent != null && r.parent !== this) r = r.parent as? View ?: return null; return if (r.parent === this) r else null }

    /** The card to land on in [next]: the one you were last on in that row, or its first card if you have not been there. */
    private fun cardIn(next: RowView): View? {
        val strip = next.strip
        val pos = (strip.getTag(tv.myliststv.launcher.R.id.tag_row_pos) as? Int) ?: 0
        fun at() = strip.layoutManager?.findViewByPosition(pos)?.takeIf { it.isFocusable }
        var hit = at()
        // That card is scrolled out of the strip: bring it in now, or the first card still on screen (say the fifth) would get the focus instead.
        if (hit == null && strip.width > 0) {
            strip.scrollToPosition(pos)
            strip.measure(MeasureSpec.makeMeasureSpec(strip.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(strip.height, MeasureSpec.EXACTLY))
            strip.layout(strip.left, strip.top, strip.right, strip.bottom)
            hit = at()
        }
        return hit ?: (0 until strip.childCount).map { strip.getChildAt(it) }.firstOrNull { it.isFocusable }
    }

    /** A row that is not on screen yet (it was scrolled away): bring it in, then put the focus on its card. */
    private fun focusRowAt(target: Int, tries: Int = 0) {
        val row = findViewHolderForAdapterPosition(target)?.itemView as? RowView
        val card = row?.let { cardIn(it) }
        if (card != null) { card.requestFocus(); return }
        if (tries == 0) smoothScrollToPosition(target)   // the same easing as going down to a row that is off screen
        if (tries < 30) postDelayed({ focusRowAt(target, tries + 1) }, 40)
    }

    /** Up and down land on the card you last had focus on in the next row (its first card if you have not been there), also when that row is not on screen. */
    override fun focusSearch(focused: View, direction: Int): View? {
        // Right at the end of a row (or Left at its start) stays put; it must not jump to a card in some other row.
        if (direction == FOCUS_LEFT || direction == FOCUS_RIGHT) {
            val found = super.focusSearch(focused, direction)
            val mine = rowOf(focused)
            return if (found != null && mine != null && rowOf(found) === mine) found else if (mine != null) focused else found
        }
        if (direction == FOCUS_UP || direction == FOCUS_DOWN) {
            val here = rowOf(focused)
            if (here != null) {
                val step = if (direction == FOCUS_DOWN) 1 else -1
                var t = getChildAdapterPosition(here) + step
                while (t in rows.indices && rows[t].items.isEmpty()) t += step   // rows with nothing in them are skipped
                if (t in rows.indices) {
                    val shown = findViewHolderForAdapterPosition(t)?.itemView as? RowView
                    val card = shown?.let { cardIn(it) }
                    if (card != null) return card
                    focusRowAt(t)   // it is off screen: the same-column card of some other row must not get the focus meanwhile
                    return focused
                }
            }
        }
        return super.focusSearch(focused, direction)
    }

    /** Moving down from the tabs: the first row's remembered card (its first card if none), not whichever card happens to sit under the tab. */
    fun enter(): Boolean { val first = findViewHolderForAdapterPosition(0)?.itemView as? RowView ?: return false; return cardIn(first)?.requestFocus() == true }

    fun removed(i: Int) = adapter!!.notifyItemRemoved(i)

    private var lastRow = 0
    /** Nothing has the focus (coming back from another page): put it on the card you were last on. False when something already has it. */
    fun restoreFocus(): Boolean { if (findFocus() != null || rows.isEmpty()) return false; focusRowAt(lastRow.coerceIn(0, rows.size - 1)); return true }

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
            // The remembered card belongs to the row, not to the (recycled) strip view: a strip reused for another row must not carry its position over.
            h.row.strip.setTag(tv.myliststv.launcher.R.id.tag_row_pos, m.lastPos)
            h.row.strip.adapter = CardAdapter(m.kind, m.items, lite, onFocus, onClick, onLong, { m.lastPos = it }).also { m.adapter = it }
        }
    }
}
