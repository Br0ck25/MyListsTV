package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * A tall rounded panel on the right edge over a dimmed screen. The bell, the gear and Apps all open in it. [show] puts the first page
 * in; [push] opens another page on top of it and Back returns to the one before; Back on the first page closes the panel.
 */
class SidePanel(private val act: Activity) {
    private val dialog = Dialog(act, R.style.Theme_MyListsTV_Panel)
    private val frame = FrameLayout(act)
    private val pages = ArrayList<View>()
    private val focusAt = ArrayList<Int>()

    init {
        dialog.window!!.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(0.7f)
            setGravity(Gravity.END or Gravity.TOP)
            setLayout(Ui.dp(act, WIDTH + MARGIN), act.resources.displayMetrics.heightPixels)   // a dialog window only wraps its content unless it is given the height
        }
        frame.background = Ui.rounded(act, Ui.CARD, Ui.R_DIALOG)
        frame.clipToOutline = true
        val root = FrameLayout(act).apply { setPadding(0, Ui.dp(act, MARGIN), Ui.dp(act, MARGIN), Ui.dp(act, MARGIN)); addView(frame, FrameLayout.LayoutParams(-1, -1)) }
        dialog.setContentView(root)
        dialog.setOnKeyListener { _, code, ev ->
            if (code != KeyEvent.KEYCODE_BACK || pages.size < 2) false
            else { if (ev.action == KeyEvent.ACTION_UP) pop(); true }
        }
    }

    fun show(page: View) {
        pages.clear(); focusAt.clear(); pages += page; frame.removeAllViews(); frame.addView(page)
        dialog.show()
        // The size is set once the dialog is showing: setting it earlier is overridden when the content is attached.
        dialog.window!!.setLayout(Ui.dp(act, WIDTH + MARGIN), act.resources.displayMetrics.heightPixels)
        focusFirst(page)
    }

    fun push(page: View) {
        focusAt += focusIndex(pages.last())
        pages += page; frame.removeAllViews(); frame.addView(page); focusFirst(page)
    }

    private fun pop() {
        pages.removeAt(pages.size - 1)
        val back = pages.last()
        frame.removeAllViews(); frame.addView(back); focusFirst(back, focusAt.removeAt(focusAt.size - 1))
    }

    fun dismiss() = dialog.dismiss()
    fun onDismiss(run: () -> Unit) = dialog.setOnDismissListener { run() }

    private fun focusables(v: View, out: ArrayList<View> = ArrayList()): ArrayList<View> {
        if (v.isFocusable && v.visibility == View.VISIBLE) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) focusables(v.getChildAt(i), out)
        return out
    }
    private fun focusIndex(page: View) = focusables(page).indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
    private fun focusFirst(page: View, at: Int = 0) { page.post { focusables(page).let { it.getOrNull(at.coerceIn(0, (it.size - 1).coerceAtLeast(0))) }?.requestFocus() } }

    companion object {
        /** Panel width and the gap around it, in design units. */
        const val WIDTH = 372
        const val MARGIN = 24

        /** A page's padding, so every page lines up. */
        fun page(c: Activity): LinearLayout = Ui.vbox(c).apply { setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 16)) }

        /** The title line: an optional icon, the title, and an optional control at the right end. */
        fun header(c: Activity, title: String, icon: Int? = null, end: View? = null): View = Ui.hbox(c, 12).apply {
            if (icon != null) addView(ImageView(c).apply { setImageResource(icon); setColorFilter(Ui.TEXT) }, LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)))
            addView(Ui.text(c, title, 24f, Ui.TEXT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
            if (end != null) addView(end)
        }

        /** One choice in a panel: an icon and a label on a raised row. */
        fun item(c: Activity, icon: Int, label: String, onClick: () -> Unit): View = Ui.hbox(c, 14).apply {
            setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14)); Ui.rowStyle(c, this, fill = Ui.ELEVATED)
            isFocusable = true; isClickable = true; setOnClickListener { onClick() }
            addView(ImageView(c).apply { setImageResource(icon); setColorFilter(Ui.TEXT) }, LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)))
            addView(Ui.text(c, label, 16f, Ui.TEXT, false, 1))
        }
    }
}
