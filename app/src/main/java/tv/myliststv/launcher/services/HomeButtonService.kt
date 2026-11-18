package tv.myliststv.launcher.services

import tv.myliststv.launcher.*
import tv.myliststv.launcher.screens.*

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import tv.myliststv.launcher.data.ButtonMap
import tv.myliststv.launcher.data.HomeSetup

/**
 * For boxes whose system keeps its own launcher as Home: when the original launcher comes to the front, open this one instead.
 * It looks only at which app's window changed. It never reads what is on screen (canRetrieveWindowContent is off) and sends nothing anywhere.
 */
class HomeButtonService : AccessibilityService() {
    private var stock: Set<String> = emptySet()

    override fun onServiceConnected() { stock = HomeSetup.stockLaunchers(this) }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.packageName?.toString() in stock)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longDone = false
    private var waitingSecond: Runnable? = null

    /** The button mapper: single, double and long presses of the buttons the owner mapped. Mapped buttons are consumed. */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        val rules = ButtonMap.rules().filter { it.key == e.keyCode }
        if (rules.isEmpty()) return false
        val single = rules.firstOrNull { it.press == "single" }; val double = rules.firstOrNull { it.press == "double" }; val long = rules.firstOrNull { it.press == "long" }
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) longDone = false
            if (long != null && e.repeatCount >= 1 && !longDone) { longDone = true; ButtonMap.run(this, long.action) }
            return true
        }
        if (longDone) { longDone = false; return true }
        val waiting = waitingSecond
        if (double == null) single?.let { ButtonMap.run(this, it.action) }
        else if (waiting != null) { handler.removeCallbacks(waiting); waitingSecond = null; ButtonMap.run(this, double.action) }
        else waitingSecond = Runnable { waitingSecond = null; single?.let { ButtonMap.run(this, it.action) } }.also { handler.postDelayed(it, 300) }
        return true
    }

    override fun onInterrupt() {}
}
