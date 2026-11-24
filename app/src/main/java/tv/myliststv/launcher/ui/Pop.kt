package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast

/**
 * A short message at the bottom of the screen, drawn by the app itself. The system's own toast carries the app's icon, which a TV
 * keeps from an older version until it restarts; this one has no icon to go stale. Falls back to the system toast without a screen.
 */
object Pop {
    private val handler = Handler(Looper.getMainLooper())
    private var shown: View? = null
    private var hide: Runnable? = null

    fun toast(c: Context, text: CharSequence, long: Boolean = false) {
        var a: Context? = c
        while (a is ContextWrapper && a !is Activity) a = a.baseContext
        val act = a as? Activity
        val content = act?.takeIf { !it.isFinishing }?.findViewById<ViewGroup>(android.R.id.content)
        if (content == null) { Toast.makeText(c, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show(); return }
        hide?.let { handler.removeCallbacks(it) }
        shown?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val v = Ui.text(act, text, 15f, Ui.TEXT, true).apply {
            gravity = Gravity.CENTER; maxWidth = Ui.dp(act, 620); alpha = 0f
            setPadding(Ui.dp(act, 24), Ui.dp(act, 12), Ui.dp(act, 24), Ui.dp(act, 12))
            background = Ui.rounded(act, Ui.ELEVATED, Ui.R_PILL, Ui.FILL_STRONG)
            elevation = Ui.dp(act, 8).toFloat()
        }
        content.addView(v, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Ui.dp(act, 44) })
        v.animate().alpha(1f).setDuration(150).start()
        shown = v
        val gone = Runnable { v.animate().alpha(0f).setDuration(200).withEndAction { (v.parent as? ViewGroup)?.removeView(v); if (shown === v) shown = null }.start() }
        hide = gone
        handler.postDelayed(gone, if (long) 3500L else 2000L)
    }
}
