package tv.myliststv.launcher.data

import tv.myliststv.launcher.*
import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** What the Home screen setup page needs: where we stand, how to change it, and the commands for the phone page. */
object HomeSetup {
    private val SERVICE = "tv.myliststv.launcher/tv.myliststv.launcher.services.HomeButtonService"

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * The Home screens of every other Home app, as "package/class": what the Home button fallback watches for. It must be the
     * Home activity itself and not the whole package, because the system settings app also registers one (FallbackHome) and
     * every settings page opened from this launcher would otherwise count as "the stock launcher came forward".
     */
    fun stockHomeScreens(c: Context): Set<String> =
        c.packageManager.queryIntentActivities(homeIntent(), 0).map { it.activityInfo }.filter { it.packageName != c.packageName }
            .flatMap { listOf("${it.packageName}/${it.name}", "${it.packageName}/${it.targetActivity ?: it.name}") }.toSet()

    fun isHome(c: Context) = c.packageManager.resolveActivity(homeIntent(), 0)?.activityInfo?.packageName == c.packageName

    fun fallbackOn(c: Context) = (Settings.Secure.getString(c.contentResolver, "enabled_accessibility_services") ?: "").split(':').contains(SERVICE)

    /** Turns the fallback on straight away if the app was allowed to (the phone page's grant command); false means open the system page instead. */
    fun enableFallback(c: Context): Boolean = try {
        val now = Settings.Secure.getString(c.contentResolver, "enabled_accessibility_services").orEmpty()
        Settings.Secure.putString(c.contentResolver, "enabled_accessibility_services", if (now.isEmpty()) SERVICE else "$now:$SERVICE") &&
            Settings.Secure.putString(c.contentResolver, "accessibility_enabled", "1")
    } catch (_: SecurityException) { false }

    /** The commands to run from a computer, with this box's address filled in. */
    fun commands(ip: String): List<String> = listOf(
        "adb connect $ip:5555",
        "adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity",
        "adb shell pm grant tv.myliststv.launcher android.permission.WRITE_SECURE_SETTINGS",
        "adb shell settings put secure enabled_accessibility_services $SERVICE",
        "adb shell settings put secure accessibility_enabled 1",
        "adb shell cmd notification allow_listener tv.myliststv.launcher/tv.myliststv.launcher.services.NotificationsService",
    )
}
