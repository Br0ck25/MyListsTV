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

    /**
     * A grid that works out its own columns on every layout from the width it really has, so a page beside the side menu (or any width) never
     * packs in more cards than fit. The leftover width is shared as equal gaps between the columns, so the cards use the whole width.
     */
    private class FitLayout(c: Context, private val bw: Int) : GridLayoutManager(c, 1) {
        var gap = 0f
        override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
            val outer = cell(ctx, bw)
            val usable = width - paddingLeft - paddingRight
            if (usable > 0) {
                val cols = (usable / outer + 0.02f).toInt().coerceAtLeast(1)
                gap = if (cols > 1) (usable - cols * outer) / (cols - 1) else 0f
                if (spanCount != cols) spanCount = cols
            }
            super.onLayoutChildren(recycler, state)
        }
        private val ctx = c
    }

    private class Spread : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val lm = parent.layoutManager as? FitLayout ?: return
            val col = (view.layoutParams as? GridLayoutManager.LayoutParams)?.spanIndex ?: 0
            out.left = if (lm.spanCount > 1) (col * lm.gap / lm.spanCount).toInt() else 0
        }
    }

    /**
     * Puts [rv] in a grid of [bw]-wide cards with [top] and [bottom] space (in the same units as [Ui.dp]), spread over the whole width
     * between the same edges as a row on Home.
     */
    fun apply(rv: RecyclerView, c: Context, bw: Int, top: Int = 6, bottom: Int = 24) {
        rv.layoutManager = FitLayout(c, bw)
        rv.clipToPadding = false; rv.itemAnimator = null
        rv.addItemDecoration(Spread())
        rv.setPadding(Ui.dp(c, EDGE), Ui.dp(c, top), Ui.dp(c, EDGE), Ui.dp(c, bottom))
    }
}
