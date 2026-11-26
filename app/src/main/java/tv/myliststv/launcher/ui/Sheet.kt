package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The one pop-up of the app (docs/DESIGN.md section 9). Every menu, question, message, confirmation and text entry is a Sheet: a
 * rounded card with a title, a line under it, and a scrolling list of option cards (a message, a picture or a text field can sit
 * among them). Nothing else may use `AlertDialog.Builder` or build its own dialog; DesignGuardTest checks that. Pick the helper that
 * fits ([menu], [message], [ask], [confirm], [busy]) or build one by hand with [card], [label], [box], [text], [image] and [field].
 */
class Sheet(val act: Activity, title: String, subtitle: String? = null) {
    /** One button of [message]. */
    class Act(val label: String, val primary: Boolean = false, val run: () -> Unit = {})

    val list = Ui.vbox(act).apply { setPadding(Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3)) }
    val dialog: AlertDialog
    private var onClose: (() -> Unit)? = null
    private val titleView = Ui.text(act, title, 24f, Ui.TEXT, true)
    fun setTitle(t: CharSequence) { titleView.text = t }

    init {
        val c = act
        val body = Ui.vbox(c).apply { setPadding(Ui.dp(c, 11), Ui.dp(c, 18), Ui.dp(c, 11), Ui.dp(c, 12)) }
        body.addView(titleView, Ui.matchWrap().apply { leftMargin = Ui.dp(c, 15) })
        if (!subtitle.isNullOrBlank()) body.addView(Ui.text(c, subtitle, 14f, Ui.DIM).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 6); leftMargin = Ui.dp(c, 15) })
        body.addView(ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false; clipToPadding = false }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 10) })
        // The same card colour, corners and rows as Settings, centred on the screen.
        val panel = FrameLayout(c).apply { background = Ui.rounded(c, Ui.CARD, Ui.R_DIALOG); clipToOutline = true; addView(body, FrameLayout.LayoutParams(-1, -2)) }
        val root = panel
        dialog = AlertDialog.Builder(c).setView(root).create()
        dialog.setOnDismissListener { onClose?.invoke() }
    }

    fun show(): Sheet {
        dialog.show()
        dialog.window?.apply { setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ui.CLEAR)); setLayout(Ui.dp(act, WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT) }
        return this
    }
    fun dismiss() = dialog.dismiss()
    fun clear() = list.removeAllViews()
    /** Runs when the sheet goes away, however it goes. */
    fun onDismiss(run: () -> Unit): Sheet { onClose = run; return this }
    fun cancelable(on: Boolean): Sheet { dialog.setCancelable(on); return this }

    /** Index among the cards of the one that has focus, so a redraw can put it back. */
    fun focusIndex(): Int = cards().indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
    fun focusAt(i: Int) { val f = cards(); f.getOrNull(i.coerceIn(0, (f.size - 1).coerceAtLeast(0)))?.requestFocus() }
    private fun cards() = (0 until list.childCount).map { list.getChildAt(it) }.filter { it.isFocusable }

    /** A small heading over a group of cards. */
    fun label(t: String) = list.addView(Ui.text(act, t, 13f, Ui.DIM, true), Ui.matchWrap().apply { topMargin = Ui.dp(act, 10); leftMargin = Ui.dp(act, 2) })

    /** A box that is not a button: whatever view is given, on a quiet card. */
    fun box(v: View): View { v.background = Ui.rounded(act, Ui.BG, Ui.R_CARD, Ui.FILL_STRONG); list.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) }); return v }

    /** A paragraph of words (a message, an explanation). */
    fun text(msg: CharSequence): TextView = Ui.text(act, msg, 15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }.also { list.addView(it, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8); bottomMargin = Ui.dp(act, 4) }) }

    /** A code to scan (black on white, whatever the theme), with its address under it. */
    fun image(bmp: Bitmap, caption: String? = null) {
        val c = act
        val box = Ui.vbox(c).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(ImageView(c).apply { setImageBitmap(bmp); setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12)); background = Ui.rounded(c, Ui.QR_LIGHT, Ui.R_DIALOG) }, LinearLayout.LayoutParams(Ui.dp(c, 214), Ui.dp(c, 214)))
        if (caption != null) box.addView(Ui.text(c, caption, 14f, Ui.DIM).apply { gravity = Gravity.CENTER }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
        list.addView(box, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8); bottomMargin = Ui.dp(c, 4) })
    }

    /** A line to type in. Done on the keyboard calls [done]; the keyboard is closed when the sheet goes away. */
    fun field(hint: String, initial: String = "", caps: Boolean = false, done: (String) -> Unit): EditText {
        val e = EditText(act).apply {
            this.hint = hint; setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); setSingleLine(true); setText(initial); setSelection(initial.length); Ui.size(this, 16f)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or (if (caps) android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else android.text.InputType.TYPE_TEXT_VARIATION_URI)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 12))
            background = Ui.rounded(act, Ui.BG, Ui.R_CARD, Ui.FILL_STRONG); foreground = Ui.focusBg(act, Ui.R_CARD, Ui.CLEAR)
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { done(text.toString()); true } else false }
        }
        list.addView(e, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) })
        val prior = onClose
        onClose = { (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0); act.window.decorView.requestFocus(); prior?.invoke() }
        return e
    }

    /** An option card: a title, an optional line under it, an optional word at the right, a tick when [selected], a brand outline when [highlighted]. */
    fun card(title: String, sub: String? = null, trailing: String? = null, selected: Boolean = false, highlighted: Boolean = false, onClick: () -> Unit): View {
        val c = act
        val v = Ui.hbox(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            background = Ui.rounded(c, Ui.CLEAR, Ui.R_PANEL, if (highlighted) Ui.BRAND else Ui.CLEAR); clipToOutline = true
            foreground = Ui.menuRowBg(c, Ui.R_PANEL, selectedToo = false)   // the same blue ring as the menus while focused
            isFocusable = true; isClickable = true; setOnClickListener { onClick() }
        }
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, title, 16f, if (selected || highlighted) Ui.BRAND else Ui.TEXT, true))
        if (!sub.isNullOrBlank()) mid.addView(Ui.text(c, sub, 13f, Ui.DIM).apply { setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
        v.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        if (!trailing.isNullOrBlank()) v.addView(Ui.text(c, trailing, 13f, Ui.DIM, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 12) })
        if (selected) v.addView(ImageView(c).apply { setImageResource(R.drawable.ic_check); setColorFilter(Ui.BRAND) }, LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)).apply { leftMargin = Ui.dp(c, 10) })
        list.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
        return v
    }

    companion object {
        /** Width of every sheet, in design units. */
        const val WIDTH = 480

        /** A list of choices. Each runs and the sheet closes. The first has the focus. */
        fun menu(act: Activity, title: String, subtitle: String? = null, items: List<Pair<String, () -> Unit>>): Sheet {
            val s = Sheet(act, title, subtitle)
            val first = items.map { (label, run) -> s.card(label) { s.dismiss(); run() } }.firstOrNull()
            s.show(); first?.requestFocus()
            return s
        }

        /** A message with buttons as cards under it (the first has the focus; a primary one is outlined). An [image] such as a QR code sits above the buttons. */
        fun message(act: Activity, title: String, message: CharSequence, vararg acts: Act, image: Bitmap? = null, cancelable: Boolean = true): Sheet {
            val s = Sheet(act, title, null).cancelable(cancelable)
            if (message.isNotEmpty()) s.text(message)
            if (image != null) s.image(image)
            val first = acts.map { a -> s.card(a.label, highlighted = a.primary) { s.dismiss(); a.run() } }.firstOrNull()
            s.show(); first?.requestFocus()
            return s
        }

        /** A yes or no. The first button has the focus, so put the safe one first. */
        fun confirm(act: Activity, title: String, message: String, confirmText: String, cancelText: String = "Cancel", onConfirm: () -> Unit): Sheet =
            message(act, title, message, Act(cancelText), Act(confirmText, true, onConfirm))

        /** One line of text to type, then [ok] saves. */
        fun ask(act: Activity, title: String, hint: String, ok: String, message: String? = null, caps: Boolean = false, initial: String = "", onOk: (String) -> Unit): Sheet {
            val s = Sheet(act, title, message)
            lateinit var e: EditText
            val save = { s.dismiss(); onOk(e.text.toString()) }
            e = s.field(hint, initial, caps) { save() }
            s.card(ok, highlighted = true) { save() }
            s.card("Cancel") { s.dismiss() }
            s.show(); e.requestFocus()
            return s
        }

        /** "Please wait": a line and a way out. [onCancel] runs when it is cancelled. */
        fun busy(act: Activity, message: String, onCancel: () -> Unit): Sheet {
            val s = Sheet(act, message, null)
            val cancel = s.card("Cancel") { onCancel(); s.dismiss() }
            s.dialog.setOnCancelListener { onCancel() }   // Back
            s.show(); cancel.requestFocus()
            return s
        }
    }
}
