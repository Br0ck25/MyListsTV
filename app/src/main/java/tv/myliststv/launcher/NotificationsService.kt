package tv.myliststv.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The bell at the top right of Home. Once the owner gives Notification access, the system hands this service the
 * notifications that are showing; the list lives only in memory and is never stored or sent anywhere.
 */
class NotificationsService : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; changed() }
    override fun onListenerDisconnected() { if (instance === this) instance = null; changed() }
    override fun onNotificationPosted(sbn: StatusBarNotification) = changed()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = changed()
    private fun changed() { Notices.onChange?.invoke() }

    companion object { @Volatile var instance: NotificationsService? = null }
}

object Notices {
    data class Item(val key: String, val app: String, val title: String, val text: String, val open: PendingIntent?)

    /** Called on a background thread when the list changes. */
    @Volatile var onChange: (() -> Unit)? = null

    fun allowed(c: Context) = (Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: "").contains(c.packageName)

    /** What is showing now, newest first: not ours, not ongoing (media, downloads), and not the group headers. */
    fun list(c: Context): List<Item> {
        val s = NotificationsService.instance ?: return emptyList()
        return try {
            s.activeNotifications.filter { it.packageName != c.packageName && !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .sortedByDescending { it.postTime }.map {
                    val e = it.notification.extras
                    val app = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(it.packageName, 0)).toString() } catch (_: Exception) { it.packageName }
                    Item(it.key, app, e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "", e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "", it.notification.contentIntent)
                }
        } catch (_: Exception) { emptyList() }
    }

    fun dismiss(key: String) { try { NotificationsService.instance?.cancelNotification(key) } catch (_: Exception) {} }
    fun dismissAll() { try { NotificationsService.instance?.cancelAllNotifications() } catch (_: Exception) {} }
    fun component(c: Context) = ComponentName(c, NotificationsService::class.java).flattenToString()
}
