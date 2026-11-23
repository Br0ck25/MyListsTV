package tv.myliststv.launcher

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.ui.*

/** Up and down between rows land on the card you last had in that row (the first one if you were never there), not on whatever lies at the same height. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class RowFocusTest {
    private fun cards(v: View, out: MutableList<Card> = ArrayList()): List<Card> { if (v is Card) out += v; if (v is ViewGroup) for (i in 0 until v.childCount) cards(v.getChildAt(i), out); return out }

    @Test fun upLandsOnTheLastCardOfTheRowAboveOrItsFirst() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get(); a.setTheme(R.style.Theme_MyListsTV)
        fun row(n: String) = RowModel(n, null, CardKind.POSTER, (0 until 12).map { CardModel("$n$it", null, payload = it) })
        val rows = listOf(row("a"), row("b"))
        val list = RowsList(a, rows, true, { }, { })
        a.setContentView(list)
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080)
        val rowViews = (0 until list.childCount).map { list.getChildAt(it) as RowView }
        assertEquals(2, rowViews.size)
        val below = cards(rowViews[1]).first { it.isFocusable }
        val first = cards(rowViews[0]).first()
        assertSame("never been in the row above: its first card", first, list.focusSearch(below, View.FOCUS_UP))
        rowViews[0].strip.setTag(R.id.tag_row_pos, 2)
        val third = rowViews[0].strip.layoutManager!!.findViewByPosition(2)
        assertSame("been on the third card: that one", third, list.focusSearch(below, View.FOCUS_UP))
    }
}
