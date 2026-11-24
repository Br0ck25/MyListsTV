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

    /** The system's own choice of Home app (Android 10 and up), or its Home settings page on older versions. */
    fun requestHome(a: android.app.Activity) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) a.startActivityForResult(a.getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 1)
            else a.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try { a.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { android.widget.Toast.makeText(a, "This TV has no Home setting. Use the phone or computer steps.", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    /** Shows the address of the phone page with the exact steps and commands, for TVs that block the switches on the TV itself. */
    fun phoneSteps(a: android.app.Activity) {
        val server = PhoneServer { _, done -> done("Nothing to add here.") }
        server.start()
        val address = server.address()?.let { "$it/setup" } ?: "Connect the TV to Wi-Fi first."
        android.app.AlertDialog.Builder(a).setTitle("Open this on your phone").setMessage(address)
            .setPositiveButton("Done", null).setOnDismissListener { server.stop() }.show()
    }

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
