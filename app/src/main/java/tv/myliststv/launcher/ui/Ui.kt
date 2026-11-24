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

/** Colors and small view builders. Everything is built in code on plain Views, no XML layouts and no theme library. */
object Ui {
    // The My Lists palette (dark values) and shape scale. The rules are in docs/DESIGN.md.
    const val BG = 0xFF000000.toInt()
    const val CARD = 0xFF1C1C1E.toInt()
    const val ELEVATED = 0xFF2C2C2E.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT2 = 0xFFEBEBF5.toInt()
    const val DIM = 0xFFAEAEB2.toInt()
    const val BRAND = 0xFF0A84FF.toInt()
    const val BRAND_FILL = 0xFF0A64D8.toInt()
    const val ACCENT = BRAND
    const val GOOD = 0xFF30D158.toInt()
    const val WARN = 0xFFFF9F0A.toInt()
    const val BAD = 0xFFFF453A.toInt()
    const val CLEAR = 0x00000000
    const val FILL = 0x1AFFFFFF                // resting fill of a pill or chip
    const val FILL_STRONG = 0x26FFFFFF         // a chip that sits on artwork
    const val FOCUS_FILL = 0x22FFFFFF          // soft fill inside the white focus ring
    const val TRACK = 0x33FFFFFF               // empty part of a progress bar
    const val TOGGLE_OFF = 0xFF39393D.toInt()
    const val SCRIM_SLATE = 0xB3000000.toInt()  // a loading screen over the title's backdrop
    const val QR_DARK = 0xFF000000.toInt()      // a QR code must be black on white to scan, whatever the theme
    const val QR_LIGHT = 0xFFFFFFFF.toInt()
    const val OVERLAY = 0xE6000000.toInt()     // dims everything behind a slate or an error
    const val BRAND_LINE = 0x800A84FF.toInt()  // brand outline on a banner
    const val R_PILL = 999
    const val R_CARD = 14
    const val R_PANEL = 16
    const val R_DIALOG = 20

    /** The three dark fades laid over artwork so text stays readable: from the left, from the bottom, from the top, and a short one behind card text. */
    private fun fade(o: GradientDrawable.Orientation, vararg colors: Int): Drawable = GradientDrawable(o, colors)
    fun scrimLeft(): Drawable = fade(GradientDrawable.Orientation.LEFT_RIGHT, 0xF2000000.toInt(), 0xAA000000.toInt(), CLEAR)
    fun scrimBottom(): Drawable = fade(GradientDrawable.Orientation.BOTTOM_TOP, 0xFF000000.toInt(), 0xCC000000.toInt(), CLEAR)
    fun scrimTop(): Drawable = fade(GradientDrawable.Orientation.TOP_BOTTOM, 0xCC000000.toInt(), CLEAR)
    fun scrimShade(): Drawable = fade(GradientDrawable.Orientation.BOTTOM_TOP, 0xCC000000.toInt(), CLEAR)

    fun rounded(c: Context, color: Int, radius: Int, outline: Int = CLEAR): Drawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, radius.toFloat()); if (outline != CLEAR) setStroke(dp(c, 1), outline) }

    /** The look of a focusable row or panel: rounded CARD fill, clipped to its corners, white focus ring at the same radius. */
    fun rowStyle(c: Context, v: View, radius: Int = R_PANEL, fill: Int = CARD) {
        v.background = rounded(c, fill, radius); v.clipToOutline = true; v.foreground = focusBg(c, radius, 0x22FFFFFF)
    }

    /**
     * Every size is designed for a 960-unit-wide screen and scaled by the real screen width, because TV boxes report
     * very different densities for the same 1080p picture. Text uses the same scale (see [size]).
     */
    fun scale(c: Context): Float { val m = c.resources.displayMetrics; return maxOf(m.widthPixels, m.heightPixels) / 960f }
    fun dp(c: Context, v: Int): Int = (v * scale(c) + 0.5f).toInt()
    fun dpf(c: Context, v: Float): Float = v * scale(c)
    fun size(t: TextView, sp: Float) = t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * scale(t.context))

    /** White ring and soft fill while focused, nothing otherwise. */
    fun focusBg(c: Context, radiusDp: Int = 10, fill: Int = FOCUS_FILL): Drawable {
        val d = GradientDrawable().apply {
            setColor(fill); setStroke(dp(c, 2), Color.WHITE); cornerRadius = dpf(c, radiusDp.toFloat())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), d)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    /** A round icon button for the top bar: a white ring while focused. */
    fun iconButton(c: Context, icon: Int, desc: String, onClick: () -> Unit): android.widget.ImageView =
        android.widget.ImageView(c).apply {
            setImageResource(icon); contentDescription = desc; setColorFilter(TEXT)
            val p = dp(c, 9); setPadding(p, p, p, p)
            background = pillBg(c)
            isFocusable = true; isClickable = true
            setOnClickListener { onClick() }
        }

    /** A pill: a white ring on a soft fill when focused (the text stays white), a brand-blue outline on a faint fill when selected (the open tab, never a solid block), otherwise [normal]. */
    fun pillBg(c: Context, normal: Int = FILL, focusedFill: Int = FOCUS_FILL): Drawable {
        val pill = { color: Int, ring: Int -> GradientDrawable().apply { setColor(color); cornerRadius = dpf(c, 40f); if (ring != CLEAR) setStroke(dp(c, 2), ring) } }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill(focusedFill, Color.WHITE))
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

    /** One button of [dialog]. */
    class Act(val label: String, val primary: Boolean = false, val run: () -> Unit = {})

    /** A message dialog in the app's own look: title, a sentence or two, and a row of buttons (the first one has the focus). An [image] such as a QR code sits to the left of the words. */
    fun dialog(a: android.app.Activity, title: String, message: CharSequence, vararg acts: Act, image: android.graphics.Bitmap? = null): android.app.AlertDialog {
        val d = android.app.AlertDialog.Builder(a).create()
        val body = vbox(a).apply { minimumWidth = dp(a, 520); setPadding(dp(a, 24), dp(a, 22), dp(a, 24), dp(a, 20)) }
        val words = vbox(a)
        words.addView(text(a, title, 24f, TEXT, true))
        words.addView(text(a, message, 15f, DIM).apply { setLineSpacing(0f, 1.15f); maxWidth = dp(a, 440) }, matchWrap().apply { topMargin = dp(a, 12) })
        val top = hbox(a, 24).apply { gravity = Gravity.TOP }
        if (image != null) top.addView(android.widget.ImageView(a).apply { setImageBitmap(image) }, LinearLayout.LayoutParams(dp(a, 190), dp(a, 190)))
        top.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(top, matchWrap())
        val row = hbox(a, 12)
        acts.forEach { act -> row.addView(button(a, act.label, act.primary) { d.dismiss(); act.run() }) }
        body.addView(row, matchWrap().apply { topMargin = dp(a, 22) })
        d.setView(body); d.show(); row.getChildAt(0)?.requestFocus()
        return d
    }

    /** Asks for one line of text. The keyboard is closed again when the dialog goes away, and Done on the keyboard confirms. */
    fun askText(a: android.app.Activity, title: String, hint: String, ok: String, message: String? = null, caps: Boolean = false, onOk: (String) -> Unit) {
        val e = android.widget.EditText(a).apply {
            this.hint = hint; setHintTextColor(DIM); setTextColor(TEXT); setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or (if (caps) android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else android.text.InputType.TYPE_TEXT_VARIATION_URI)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val d = android.app.AlertDialog.Builder(a).setTitle(title).apply { if (message != null) setMessage(message) }.setView(e)
            .setPositiveButton(ok) { _, _ -> onOk(e.text.toString()) }.setNegativeButton("Cancel", null).create()
        val closeKeyboard = { (a.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0) }
        e.setOnEditorActionListener { _, id, _ -> if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { closeKeyboard(); d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick(); true } else false }
        d.setOnDismissListener { closeKeyboard(); a.window.decorView.requestFocus() }
        d.show()
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
