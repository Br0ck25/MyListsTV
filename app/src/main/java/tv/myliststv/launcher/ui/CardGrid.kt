package tv.myliststv.launcher.ui

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.Prefs

/**
 * The one way a page lays cards out in a grid (Search, Library, Apps), so every page has the same card size, spacing and edges as the
 * rows on Home. The columns are as many whole cards as fit, starting at the same left edge as a row on Home; what is left over stays at
 * the right, and the cells are exactly as wide as the cards, so there are no gaps between them.
 */
object CardGrid {
    private const val MARGIN = 12   // the space around each card, in the same units as Cards.kt (6 on every side)
    private const val EDGE = 42     // the space at each side of the screen, the same as the rows on Home, so the first card lines up with them

    private fun cell(c: Context, bw: Int) = Ui.dp(c, bw) * Prefs.cardScale + Ui.dp(c, MARGIN)

    /** How many cards of base width [bw] (Cards.kt: 118 poster, 208 wide or app) fit across, at the chosen card size. */
    fun columns(c: Context, bw: Int): Int =
        ((c.resources.displayMetrics.widthPixels - Ui.dp(c, EDGE * 2)) / cell(c, bw) + 0.02f).toInt().coerceAtLeast(1)

    /**
     * Puts [rv] in a grid of [bw]-wide cards, centred, with [top] and [bottom] space (in the same units as [Ui.dp]). The number of columns
     * is worked out from the width the grid really gets once it is on the screen, so a page beside the side menu has fewer columns than one
     * across the whole screen, and the cards never overlap.
     */
    fun apply(rv: RecyclerView, c: Context, bw: Int, top: Int = 6, bottom: Int = 24) {
        rv.layoutManager = GridLayoutManager(c, columns(c, bw))
        rv.clipToPadding = false; rv.itemAnimator = null
        fit(rv, c, bw, c.resources.displayMetrics.widthPixels, top, bottom)
        rv.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ -> if (r - l > 0) fit(rv, c, bw, r - l, top, bottom) }
    }

    /** Columns and edges for a grid that is [width] pixels wide. Nothing changes (so nothing is laid out again) when they already fit. */
    private fun fit(rv: RecyclerView, c: Context, bw: Int, width: Int, top: Int, bottom: Int) {
        val side = Ui.dp(c, EDGE)   // left-aligned like a row on Home; what is left over stays at the right
        val cols = ((width - side * 2) / cell(c, bw) + 0.02f).toInt().coerceAtLeast(1)
        // The grid shares its width equally between the columns, so the leftover goes in the right padding: then each column is exactly one card wide.
        val right = (width - side - cols * cell(c, bw)).toInt().coerceAtLeast(0)
        val lm = rv.layoutManager as? GridLayoutManager ?: return
        if (lm.spanCount != cols) lm.spanCount = cols
        if (rv.paddingLeft != side || rv.paddingRight != right || rv.paddingTop != Ui.dp(c, top) || rv.paddingBottom != Ui.dp(c, bottom))
            rv.setPadding(side, Ui.dp(c, top), right, Ui.dp(c, bottom))
    }
}
