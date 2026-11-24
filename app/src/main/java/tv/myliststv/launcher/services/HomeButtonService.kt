package tv.myliststv.launcher.services

import tv.myliststv.launcher.*
import tv.myliststv.launcher.screens.*

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import tv.myliststv.launcher.data.ButtonGestures
import tv.myliststv.launcher.data.ButtonMap
import tv.myliststv.launcher.data.HomeSetup

/**
 * For boxes whose system keeps its own launcher as Home: when the original launcher's Home screen comes to the front, open this one instead.
 * It looks only at which app's window changed. It never reads what is on screen (canRetrieveWindowContent is off) and sends nothing anywhere.
 */
class HomeButtonService : AccessibilityService() {
    private var stock: Set<String> = emptySet()

    override fun onServiceConnected() { stock = HomeSetup.stockHomeScreens(this) }

    /** The app whose window came forward last, so a button can work only outside (or only inside) My Lists TV. */
    private var frontApp = ""
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        e.packageName?.toString()?.let { frontApp = it }
        if ("${e.packageName}/${e.className}" in stock) {
            // A Home intent, so the launcher knows Home was pressed (single press: Home, double press: profiles) and not only that it came forward.
            startActivity(Intent(this, MainActivity::class.java).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private val gestures = ButtonGestures({ ButtonMap.rules().filter { ButtonMap.active(it) && (it.scope == "all" || (it.scope == "global" && frontApp != packageName)) } }) { ButtonMap.run(this, it) }

    /** The button mapper outside the launcher: the rules that work everywhere, or only outside My Lists TV. Mapped buttons are consumed. */
    override fun onKeyEvent(e: KeyEvent): Boolean = gestures.onKey(e)

    override fun onInterrupt() {}
}
