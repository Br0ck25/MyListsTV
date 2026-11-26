package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

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
import tv.myliststv.launcher.data.Prefs

/** Colors and small view builders. Everything is built in code on plain Views, no XML layouts and no theme library. */
object Ui {
    // The My Lists palette (dark values) and shape scale. The rules are in docs/DESIGN.md.
    const val BG = 0xFF000000.toInt()
    const val CARD = 0xFF1C1C1E.toInt()
    const val ELEVATED = 0xFF2C2C2E.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT2 = 0xFFEBEBF5.toInt()
    const val DIM = 0xFFAEAEB2.toInt()
    /** A highlight colour the owner can pick (Settings, Layout): the colour itself, and a darker one that carries white text (5:1 or better). */
    class Accent(val name: String, val main: Int, val fill: Int)
    val ACCENTS = listOf(
        Accent("Blue", 0xFF0A84FF.toInt(), 0xFF0A64D8.toInt()), Accent("Green", 0xFF30D158.toInt(), 0xFF1E7B34.toInt()),
        Accent("Purple", 0xFFBF5AF2.toInt(), 0xFF8E34BF.toInt()), Accent("Orange", 0xFFFF9F0A.toInt(), 0xFFA85B00.toInt()),
        Accent("Pink", 0xFFFF375F.toInt(), 0xFFC4153F.toInt()), Accent("Cyan", 0xFF64D2FF.toInt(), 0xFF0A708F.toInt()),
        Accent("White", 0xFFFFFFFF.toInt(), 0xFF48484A.toInt()),
    )
    private val accent get() = ACCENTS[Prefs.accent.coerceIn(0, ACCENTS.size - 1)]
    val BRAND get() = accent.main                              // the highlight colour: focus ring, selection, toggle on, primary
    val BRAND_FILL get() = accent.fill                         // fills that carry white text
    val ACCENT get() = BRAND
    const val GOOD = 0xFF30D158.toInt()
    const val WARN = 0xFFFF9F0A.toInt()
    const val BAD = 0xFFFF453A.toInt()
    const val CLEAR = 0x00000000
    const val FILL = 0x1AFFFFFF                // resting fill of a pill or chip
    const val FILL_STRONG = 0x26FFFFFF         // a chip that sits on artwork
    const val TRACK = 0x33FFFFFF               // empty part of a progress bar
    const val TRACK_PLAYER = 0x66FFFFFF        // the empty part of the player's bar, clear against any picture
    const val TOGGLE_OFF = 0xFF39393D.toInt()
    const val SCRIM_SLATE = 0xB3000000.toInt()  // a loading screen over the title's backdrop
    const val QR_DARK = 0xFF000000.toInt()      // a QR code must be black on white to scan, whatever the theme
    const val QR_LIGHT = 0xFFFFFFFF.toInt()
    const val OVERLAY = 0xE6000000.toInt()     // dims everything behind a slate or an error
    const val SIDE_GLASS = 0xE01C1C1E.toInt()  // the player's side panel over the picture: the card colour, a little see-through
    val BRAND_LINE get() = (BRAND and 0x00FFFFFF) or 0x80000000.toInt()   // highlight outline on a banner
    val BRAND_SOFT get() = (BRAND and 0x00FFFFFF) or 0x2B000000          // soft fill inside the ring of whatever has focus
    val FOCUS_FILL get() = BRAND_SOFT
    const val R_PILL = 999
    const val R_CARD = 14
    const val R_PANEL = 16
    const val R_DIALOG = 20
    const val R_TILE = 10                      // the small rounded square behind a menu row's icon

    /** The colour a title's backdrop gives the room: its average, made vivid enough to show as a tint. */
    fun ambient(b: android.graphics.Bitmap): Int {
        val s = android.graphics.Bitmap.createScaledBitmap(b, 8, 4, true)
        var r = 0; var g = 0; var bl = 0
        for (x in 0 until 8) for (y in 0 until 4) { val p = s.getPixel(x, y); r += Color.red(p); g += Color.green(p); bl += Color.blue(p) }
        if (s !== b) s.recycle()
        val hsv = FloatArray(3); Color.RGBToHSV(r / 32, g / 32, bl / 32, hsv)
        hsv[1] = hsv[1].coerceIn(0.45f, 0.9f); hsv[2] = hsv[2].coerceIn(0.5f, 0.85f)
        return Color.HSVToColor(hsv)
    }

    /** A soft glow of [color] from the top right, laid over the backdrop so the room takes on the focused title's colours. */
    fun glow(c: Context, color: Int): Drawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dpf(c, 760f); setGradientCenter(0.78f, 0.12f)
        colors = intArrayOf((color and 0x00FFFFFF) or 0x73000000, CLEAR)
    }

    /** The three dark fades laid over artwork so text stays readable: from the left, from the bottom, from the top, and a short one behind card text. */
    private fun fade(o: GradientDrawable.Orientation, vararg colors: Int): Drawable = GradientDrawable(o, colors)
    fun scrimLeft(): Drawable = fade(GradientDrawable.Orientation.LEFT_RIGHT, 0xF2000000.toInt(), 0xAA000000.toInt(), CLEAR)
    fun scrimBottom(): Drawable = fade(GradientDrawable.Orientation.BOTTOM_TOP, 0xFF000000.toInt(), 0xCC000000.toInt(), CLEAR)
    fun scrimTop(): Drawable = fade(GradientDrawable.Orientation.TOP_BOTTOM, 0xCC000000.toInt(), CLEAR)
    fun scrimShade(): Drawable = fade(GradientDrawable.Orientation.BOTTOM_TOP, 0xCC000000.toInt(), CLEAR)

    fun rounded(c: Context, color: Int, radius: Int, outline: Int = CLEAR): Drawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, radius.toFloat()); if (outline != CLEAR) setStroke(dp(c, 1), outline) }

    /** The look of a focusable row or panel: rounded CARD fill, clipped to its corners, the blue menu ring at the same radius while focused. */
    fun rowStyle(c: Context, v: View, radius: Int = R_PANEL, fill: Int = CARD) {
        v.background = rounded(c, fill, radius); v.clipToOutline = true; v.foreground = menuRowBg(c, radius, selectedToo = false)
    }

    /**
     * Every size is designed for a 960-unit-wide screen and scaled by the real screen width, because TV boxes report
     * very different densities for the same 1080p picture. Text uses the same scale (see [size]).
     */
    fun scale(c: Context): Float { val m = c.resources.displayMetrics; return maxOf(m.widthPixels, m.heightPixels) / 960f }
    fun dp(c: Context, v: Int): Int = (v * scale(c) + 0.5f).toInt()
    fun dpf(c: Context, v: Float): Float = v * scale(c)
    fun size(t: TextView, sp: Float) = t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * scale(t.context))

    /** The highlight ring and a soft fill while focused, nothing otherwise. */
    fun focusBg(c: Context, radiusDp: Int = 10, fill: Int = FOCUS_FILL): Drawable {
        val d = GradientDrawable().apply {
            setColor(fill); setStroke(dp(c, 2), BRAND); cornerRadius = dpf(c, radiusDp.toFloat())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), d)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    /** A menu row (settings, side bar, pop-up cards): a blue ring on a soft blue fill while focused (and selected, unless [selectedToo] is off), otherwise [normal]. */
    fun menuRowBg(c: Context, radius: Int = R_PANEL, normal: Int = CLEAR, selectedToo: Boolean = true): Drawable {
        val shape = { color: Int, ring: Int -> GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, radius.toFloat()); if (ring != CLEAR) setStroke(dp(c, 2), ring) } }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(BRAND_SOFT, BRAND))
            if (selectedToo) addState(intArrayOf(android.R.attr.state_selected), shape(BRAND_SOFT, BRAND))
            addState(intArrayOf(), shape(normal, CLEAR))
        }
    }

    /** The icon of a menu row: [icon] in white on a small rounded square. */
    fun iconTile(c: Context, icon: Int, size: Int = 36): View = android.widget.ImageView(c).apply {
        setImageResource(icon); setColorFilter(TEXT); background = rounded(c, FILL, R_TILE)
        val p = dp(c, size / 6 + 3); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size))
    }

    /** A small blue heading over a group of menu rows. */
    fun sectionLabel(c: Context, t: String): TextView = text(c, t, 14f, BRAND, true)

    /** A round icon button for the top bar: a white ring while focused. */
    fun iconButton(c: Context, icon: Int, desc: String, onClick: () -> Unit): android.widget.ImageView =
        android.widget.ImageView(c).apply {
            setImageResource(icon); contentDescription = desc; setColorFilter(TEXT)
            val p = dp(c, 9); setPadding(p, p, p, p)
            background = pillBg(c)
            isFocusable = true; isClickable = true
            setOnClickListener { onClick() }
        }

    /** A pill: the highlight ring on a soft fill when focused (the text stays white), a highlight outline on a faint fill when selected (the open tab, never a solid block), otherwise [normal]. */
    fun pillBg(c: Context, normal: Int = FILL, focusedFill: Int = FOCUS_FILL): Drawable {
        val pill = { color: Int, ring: Int -> GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, 40f); if (ring != CLEAR) setStroke(dp(c, 2), ring) } }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill(focusedFill, BRAND))
            addState(intArrayOf(android.R.attr.state_selected), pill(FILL_STRONG, BRAND))
            addState(intArrayOf(), pill(normal, CLEAR))
        }
    }

    fun text(c: Context, s: CharSequence?, sp: Float, color: Int = TEXT, bold: Boolean = false, maxLines: Int = 0): TextView =
        TextView(c).apply {
            text = s; size(this, sp); setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (maxLines > 0) { this.maxLines = maxLines; ellipsize = android.text.TextUtils.TruncateAt.END }
            includeFontPadding = false
        }

    /** [primary] is the one main action on a screen: filled brand blue until focused. */
    fun button(c: Context, label: String, primary: Boolean = false, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label; size(this, 15f); setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(c, 18), dp(c, 9), dp(c, 18), dp(c, 9))
            background = if (primary) pillBg(c, BRAND_FILL, BRAND_FILL) else pillBg(c); isFocusable = true; isClickable = true
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
