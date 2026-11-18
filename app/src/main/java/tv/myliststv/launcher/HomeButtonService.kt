package tv.myliststv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
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

    override fun onInterrupt() {}
}
