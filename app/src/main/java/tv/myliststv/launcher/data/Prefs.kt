package tv.myliststv.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. Defaults follow docs/PLAN.md. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var appContext: Context

    fun init(c: Context) { appContext = c.applicationContext; sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var liteMode: String                 // "Auto", "On", "Off"
        get() = sp.getString("lite", "Auto") ?: "Auto"
        set(v) = sp.edit().putString("lite", v).apply()

    /** 0 = Auto (device decides), otherwise 2160, 1080 or 720. */
    var maxResolution: Int
        get() = sp.getInt("maxRes", 0)
        set(v) = sp.edit().putInt("maxRes", v).apply()

    var autoPick: Boolean
        get() = sp.getBoolean("autoPick", true)
        set(v) = sp.edit().putBoolean("autoPick", v).apply()

    var fallbackOn: Boolean
        get() = sp.getBoolean("fallbackOn", true)
        set(v) = sp.edit().putBoolean("fallbackOn", v).apply()

    var fallbackTries: Int
        get() = sp.getInt("fallbackTries", 2)
        set(v) = sp.edit().putInt("fallbackTries", v).apply()

    var startupTimeoutSec: Int
        get() = sp.getInt("startupTimeout", 10)
        set(v) = sp.edit().putInt("startupTimeout", v).apply()

    var subtitleLang: String             // "off" or a two-letter code
        get() = sp.getString("subLang", "en") ?: "en"
        set(v) = sp.edit().putString("subLang", v).apply()

    var autoplayNext: Boolean
        get() = sp.getBoolean("autoNext", true)
        set(v) = sp.edit().putBoolean("autoNext", v).apply()

    var showAppsRow: Boolean
        get() = sp.getBoolean("appsRow", true)
        set(v) = sp.edit().putBoolean("appsRow", v).apply()

    var showContinue: Boolean
        get() = sp.getBoolean("showCw", true)
        set(v) = sp.edit().putBoolean("showCw", v).apply()

    var advanced: Boolean
        get() = sp.getBoolean("advanced", false)
        set(v) = sp.edit().putBoolean("advanced", v).apply()

    fun isLowRamBox(): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice || am.memoryClass <= 128
    }

    fun lite(): Boolean = when (liteMode) { "On" -> true; "Off" -> false; else -> isLowRamBox() }

    /** The highest resolution to prefer. Auto keeps weak boxes at 1080p so 4K streams are not picked for them. */
    fun effectiveMaxResolution(): Int = if (maxResolution > 0) maxResolution else if (isLowRamBox()) 1080 else 2160

    fun memoryCacheBytes(): Int = if (lite()) 5 * 1024 * 1024 else 10 * 1024 * 1024
}
