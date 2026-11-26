package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * A tall rounded panel on the right edge over a dimmed screen: the bell opens in it (Settings is the same panel, drawn by its own screen).
 * [show] puts the page in; Back closes it.
 */
class SidePanel(private val act: Activity) {
    private val dialog = Dialog(act, R.style.Theme_MyListsTV_Panel)
    private val frame = FrameLayout(act)

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
    }

    fun show(page: View) {
        frame.removeAllViews(); frame.addView(page)
        dialog.show()
        // The size is set once the dialog is showing: setting it earlier is overridden when the content is attached.
        dialog.window!!.setLayout(Ui.dp(act, WIDTH + MARGIN), act.resources.displayMetrics.heightPixels)
        focusFirst(page)
    }

    fun dismiss() = dialog.dismiss()
    fun onDismiss(run: () -> Unit) = dialog.setOnDismissListener { run() }

    private fun focusables(v: View, out: ArrayList<View> = ArrayList()): ArrayList<View> {
        if (v.isFocusable && v.visibility == View.VISIBLE) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) focusables(v.getChildAt(i), out)
        return out
    }
    private fun focusFirst(page: View) { page.post { focusables(page).firstOrNull()?.requestFocus() } }

    companion object {
        /** Panel width and the gap around it, in design units. */
        const val WIDTH = 420
        const val MARGIN = 24

        /** A page's padding, so every page lines up. */
        fun page(c: Activity): LinearLayout = Ui.vbox(c).apply { setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 16)) }

        /** The title line: an optional icon, the title, and an optional control at the right end. */
        fun header(c: Activity, title: String, icon: Int? = null, end: View? = null): View = Ui.hbox(c, 12).apply {
            if (icon != null) addView(Ui.iconTile(c, icon, 32))
            addView(Ui.text(c, title, 24f, Ui.TEXT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
            if (end != null) addView(end)
        }
    }
}
