package tv.myliststv.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.Prefs

/**
 * The one way a page lays cards out in a grid (Search, Library, Apps), so every page has the same card size, spacing and edges as the
 * rows on Home. The columns are as many whole cards as fit; the cards are spread evenly over the whole width, the first at the same left edge
 * as a row on Home and the last at the matching edge on the right.
 */
object CardGrid {
    private const val MARGIN = 12   // the space around each card, in the same units as Cards.kt (6 on every side)
    private const val EDGE = 42     // the space at each side of the screen, the same as the rows on Home, so the first card lines up with them

    private fun cell(c: Context, bw: Int) = Ui.dp(c, bw) * Prefs.cardScale + Ui.dp(c, MARGIN)

    /** How many cards of base width [bw] (Cards.kt: 118 poster, 208 wide or app) fit across, at the chosen card size. */
    fun columns(c: Context, bw: Int): Int =
        ((c.resources.displayMetrics.widthPixels - Ui.dp(c, EDGE * 2)) / cell(c, bw) + 0.02f).toInt().coerceAtLeast(1)

    /** Spaces the cards evenly across the whole width: the first card at the left edge, the last at the right, the same gap between each. */
    private class Spread : RecyclerView.ItemDecoration() {
        var gap = 0f
        var cols = 1
        override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val col = (view.layoutParams as? GridLayoutManager.LayoutParams)?.spanIndex ?: 0
            out.left = if (cols > 1) (col * gap / cols).toInt() else 0
        }
    }

    private val spreads = java.util.WeakHashMap<RecyclerView, Spread>()

    /**
     * Puts [rv] in a grid of [bw]-wide cards with [top] and [bottom] space (in the same units as [Ui.dp]). The columns are worked out from the
     * width the grid really gets once it is on the screen (so a page beside the side menu has fewer), and the cards are spread over all of
     * that width, not bunched at the left.
     */
    fun apply(rv: RecyclerView, c: Context, bw: Int, top: Int = 6, bottom: Int = 24) {
        rv.layoutManager = GridLayoutManager(c, columns(c, bw))
        rv.clipToPadding = false; rv.itemAnimator = null
        val spread = Spread().also { spreads[rv] = it; rv.addItemDecoration(it) }
        fit(rv, c, bw, c.resources.displayMetrics.widthPixels, top, bottom, spread)
        rv.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> if (r - l > 0) fit(rv, c, bw, r - l, top, bottom, spread) }
    }

    /** Columns, edges and the gap between cards for a grid [width] pixels wide. Nothing changes (so nothing is laid out again) when they already fit. */
    private fun fit(rv: RecyclerView, c: Context, bw: Int, width: Int, top: Int, bottom: Int, spread: Spread) {
        val side = Ui.dp(c, EDGE)
        val outer = cell(c, bw)
        val usable = width - side * 2
        val cols = (usable / outer + 0.02f).toInt().coerceAtLeast(1)
        val gap = if (cols > 1) (usable - cols * outer) / (cols - 1) else 0f
        val lm = rv.layoutManager as? GridLayoutManager ?: return
        var changed = false
        if (lm.spanCount != cols) { lm.spanCount = cols; changed = true }
        if (spread.cols != cols || kotlin.math.abs(spread.gap - gap) > 0.5f) { spread.cols = cols; spread.gap = gap; changed = true }
        if (rv.paddingLeft != side || rv.paddingRight != side || rv.paddingTop != Ui.dp(c, top) || rv.paddingBottom != Ui.dp(c, bottom))
            rv.setPadding(side, Ui.dp(c, top), side, Ui.dp(c, bottom))
        if (changed) rv.invalidateItemDecorations()
    }
}
