package tv.myliststv.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Colors and small view builders. Everything is built in code on plain Views, no XML layouts and no theme library. */
object Ui {
    const val BG = 0xFF0A0B11.toInt()
    const val CARD = 0xFF171925.toInt()
    const val TEXT = 0xFFEEF0F7.toInt()
    const val DIM = 0xFF9AA1B8.toInt()
    const val ACCENT = 0xFFFFC65C.toInt()
    const val GOOD = 0xFF59E0AE.toInt()
    const val BAD = 0xFFFF8A8E.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    /** White ring and soft fill while focused, nothing otherwise. */
    fun focusBg(c: Context, radiusDp: Int = 10, fill: Int = 0x22FFFFFF): Drawable {
        val d = GradientDrawable().apply {
            setColor(fill); setStroke(dp(c, 3), Color.WHITE); cornerRadius = dpf(c, radiusDp.toFloat())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), d)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    fun pillBg(c: Context, normal: Int = 0x1AFFFFFF, focusedFill: Int = Color.WHITE): Drawable {
        val on = GradientDrawable().apply { setColor(focusedFill); cornerRadius = dpf(c, 40f) }
        val off = GradientDrawable().apply { setColor(normal); cornerRadius = dpf(c, 40f) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), on)
            addState(intArrayOf(), off)
        }
    }

    fun text(c: Context, s: CharSequence?, sp: Float, color: Int = TEXT, bold: Boolean = false, maxLines: Int = 0): TextView =
        TextView(c).apply {
            text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (maxLines > 0) { this.maxLines = maxLines; ellipsize = android.text.TextUtils.TruncateAt.END }
            includeFontPadding = false
        }

    /** A focusable pill. Dark text on a white pill while focused. */
    fun button(c: Context, label: String, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9))
            background = pillBg(c); isFocusable = true; isClickable = true
            setOnFocusChangeListener { v, f -> (v as TextView).setTextColor(if (f) 0xFF12131A.toInt() else TEXT) }
            setOnClickListener { onClick() }
        }

    fun hbox(c: Context, gapDp: Int = 0): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        if (gapDp > 0) { showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE; dividerDrawable = GradientDrawable().apply { setSize(dp(c, gapDp), 1); setColor(Color.TRANSPARENT) } }
    }

    fun vbox(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** Scale-up on focus unless the box is in lite mode. */
    fun focusScale(v: View, lite: Boolean, amount: Float = 1.06f) {
        if (lite) return
        val prev = v.onFocusChangeListener
        v.setOnFocusChangeListener { view, f ->
            view.animate().scaleX(if (f) amount else 1f).scaleY(if (f) amount else 1f).setDuration(110).start()
            prev?.onFocusChange(view, f)
        }
    }
}
