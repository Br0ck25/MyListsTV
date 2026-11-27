package tv.myliststv.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. Defaults follow docs/PLAN.md. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var appContext: Context

    /** Goes up whenever a setting that changes what Home shows is changed, so the screen behind Settings rebuilds when you come back. */
    @Volatile var epoch = 0
        private set

    /** Tells Home to rebuild when it is next shown, for a change that is not a saved setting (a picked wallpaper or icon). */
    fun bump() { epoch++ }

    fun init(c: Context) {
        appContext = c.applicationContext; sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp.getString("backupCode", null)?.let { if (Secure.needsWrap(it)) sp.edit().putString("backupCode", Secure.wrap(it)).apply() }    // wraps a code saved before the Keystore was used
    }

    var liteMode: String                 // "Auto", "On", "Off"
        get() = sp.getString("lite", "Auto") ?: "Auto"
        set(v) { sp.edit().putString("lite", v).apply(); epoch++ }

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

    /** Which releases Settings, About offers: "production" (releases promoted by the owner) or "beta" (every build from main, newest first). */
    var updateChannel: String
        get() = sp.getString("updateChannel", "production") ?: "production"
        set(v) = sp.edit().putString("updateChannel", v).apply()

    var subtitleLang: String             // "off" or a two-letter code
        get() = sp.getString("subLang", "en") ?: "en"
        set(v) = sp.edit().putString("subLang", v).apply()

    var skipIntro: Boolean
        get() = sp.getBoolean("skipIntro", true)
        set(v) = sp.edit().putBoolean("skipIntro", v).apply()

    /** Ask Cinemeta (the standard Stremio metadata addon) for logos, country, awards and episodes the catalog addon lacks. */
    var extraDetails: Boolean
        get() = sp.getBoolean("extraDetails", true)
        set(v) { sp.edit().putBoolean("extraDetails", v).apply(); epoch++ }

    var autoplayNext: Boolean
        get() = sp.getBoolean("autoNext", true)
        set(v) = sp.edit().putBoolean("autoNext", v).apply()

    /** Collections from My Lists as rows on Home. */
    var showCollections: Boolean
        get() = sp.getBoolean("collections", true)
        set(v) { sp.edit().putBoolean("collections", v).apply(); epoch++ }

    var showAppsRow: Boolean
        get() = sp.getBoolean("appsRow", true)
        set(v) { sp.edit().putBoolean("appsRow", v).apply(); epoch++ }

    /** Catalog items as wide backdrop pictures (true) or tall posters (false). */
    var horizontalPosters: Boolean
        get() = sp.getBoolean("horizontalPosters", false)
        set(v) { sp.edit().putBoolean("horizontalPosters", v).apply(); epoch++ }

    /** Size of posters, wide cards and app tiles together: 0 small, 1 medium, 2 large. */
    var cardSize: Int
        get() = sp.getInt("cardSize", 1)
        set(v) { sp.edit().putInt("cardSize", v).apply(); epoch++ }
    /** The card size setting: -1 extra small, 0 small, 1 medium (the default), 2 large, 3 extra large. */
    val cardScale get() = when (cardSize) { -1 -> 0.7f; 0 -> 0.85f; 1 -> 1f; 2 -> 1.2f; else -> 1.4f }

    private fun strings(k: String) = (sp.getString(k, "") ?: "").split('\n').filter { it.isNotEmpty() }
    private fun putStrings(k: String, v: List<String>) { sp.edit().putString(k, v.joinToString("\n")).apply(); epoch++ }
    /** Home rows (by title) and apps in the order the owner chose, and the ones hidden from Home. Unlisted ones go after, in their usual order. */
    /** The rows were renamed (Your apps, Continue watching, Airing next); what an older version saved still counts under the new names. */
    private fun renamed(l: List<String>) = l.map { when (it) { "Your apps" -> "Your Apps"; "Continue watching" -> "Continue Watching"; "Airing next" -> "Airing Next"; else -> it } }
    var homeOrder: List<String> get() = renamed(strings("homeOrder")); set(v) = putStrings("homeOrder", v)
    var homeHidden: List<String> get() = renamed(strings("homeHidden")); set(v) = putStrings("homeHidden", v)
    var appOrder: List<String> get() = strings("appOrder"); set(v) = putStrings("appOrder", v)
    /** The top-bar pages in the owner's order, and the ones hidden (see Pages). */
    var pageOrder: List<String> get() = strings("pageOrder"); set(v) = putStrings("pageOrder", v)
    var pageHidden: List<String> get() = strings("pageHidden"); set(v) = putStrings("pageHidden", v)
    fun pages(): List<String> = Pages.visible(Pages.all, pageOrder, pageHidden)

    var appHidden: List<String> get() = strings("appHidden"); set(v) = putStrings("appHidden", v)

    /** The package of the icon pack in use, or empty for the apps' own icons. */
    var iconPack: String get() = sp.getString("iconPack", "") ?: ""; set(v) { sp.edit().putString("iconPack", v).apply(); epoch++ }

    /** The names the owner gave apps, by package. */
    val appNames: Map<String, String> get() = strings("appNames").mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    /** Names an app; a blank name puts its own name back. */
    fun setAppName(pkg: String, name: String) {
        val n = name.replace(Regex("\\s+"), " ").trim()
        putStrings("appNames", appNames.filterKeys { it != pkg }.map { "${it.key}\t${it.value}" } + if (n.isEmpty()) emptyList() else listOf("$pkg\t$n"))
    }
    /** Sort key for a home row: its place in [homeOrder], else Your Apps first, then Continue Watching and Airing Next, then the catalogs. */
    fun homeRank(title: String): Int = homeOrder.indexOf(title).let { if (it >= 0) it else when (title) { "Your Apps" -> -3; "Continue Watching" -> -2; "Airing Next" -> -1; else -> 5000 } }

    /** Where the pages (Home, Movies...) are listed: 0 across the top, 1 side bar, 2 side bar that stays hidden until Left is pressed. */
    var navStyle: Int
        get() = sp.getInt("navStyle", 0)
        set(v) { sp.edit().putInt("navStyle", v).apply(); epoch++ }

    /** The highlight colour: a place in [tv.myliststv.launcher.ui.Ui.ACCENTS] (the focus ring, selections, toggles, primary buttons). 0 is blue. */
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) { sp.edit().putInt("accent", v).apply(); epoch++ }

    /** Remote button rules, see ButtonMap. */
    var buttonMap: String
        get() = sp.getString("buttonMap", "") ?: ""
        set(v) = sp.edit().putString("buttonMap", v).apply()
    /** The Button Mapper's master switch, and the buttons switched off one by one (key codes). */
    var mapperOn: Boolean
        get() = sp.getBoolean("mapperOn", true)
        set(v) = sp.edit().putBoolean("mapperOn", v).apply()
    /** "Customize Home Button": when off, Home does its factory jobs (single press: Home, double press: profile switcher). */
    var homeCustom: Boolean
        get() = sp.getBoolean("homeCustom", false)
        set(v) = sp.edit().putBoolean("homeCustom", v).apply()
    var buttonMapOff: List<String>
        get() = strings("buttonMapOff")
        set(v) = putStrings("buttonMapOff", v)

    /** The audio and subtitle language the viewer last chose for a title, so the next episode starts the same way. Null when never chosen. */
    fun recallTracks(metaId: String?): Pair<String?, String?> =
        (sp.getString("tr_$metaId", "|") ?: "|").split('|').let { it.getOrNull(0)?.ifEmpty { null } to it.getOrNull(1)?.ifEmpty { null } }

    fun rememberTrack(metaId: String?, audio: Boolean, lang: String) {
        if (metaId == null) return
        val (a, s) = recallTracks(metaId)
        sp.edit().putString("tr_$metaId", (if (audio) lang else a.orEmpty()) + "|" + (if (audio) s.orEmpty() else lang)).apply()
    }

    /** The "Airing Next" row on Home: the next episode of shows the account follows, soonest first. */
    /** With Airing Next on: a next-up episode that Airing Next already lists is left out of Continue Watching. */
    var hideUpcomingContinue: Boolean
        get() = sp.getBoolean("hideUpcomingContinue", true)
        set(v) { sp.edit().putBoolean("hideUpcomingContinue", v).apply(); epoch++ }
    var showAiring: Boolean
        get() = sp.getBoolean("showAiring", true)
        set(v) { sp.edit().putBoolean("showAiring", v).apply(); epoch++ }

    /** The last Airing Next answer for the profile in use (JSON), so Home shows it at once. */
    var airingJson: String
        get() = sp.getString(Profiles.key("airing"), "") ?: ""
        set(v) = sp.edit().putString(Profiles.key("airing"), v).apply()

    var showContinue: Boolean
        get() = sp.getBoolean("showCw", true)
        set(v) { sp.edit().putBoolean("showCw", v).apply(); epoch++ }

    /** Recovery code for this box's online backup. Empty until the first backup. */
    var backupCode: String
        get() = Secure.unwrap(sp.getString(Profiles.key("backupCode"), "") ?: "").orEmpty()
        set(v) = sp.edit().putString(Profiles.key("backupCode"), Secure.wrap(v)).apply()

    /** The signed-in My Lists addon link, or empty when using the default catalogs. */
    var accountLink: String
        get() = Secure.unwrap(sp.getString(Profiles.key("accountLink"), "") ?: "").orEmpty()
        set(v) = sp.edit().putString(Profiles.key("accountLink"), Secure.wrap(v)).apply()

    /** The My Lists account name of the profile in use, shown as "Signed in as ...". Empty until the account tells us. */
    var accountName: String
        get() = sp.getString(Profiles.key("accountName"), "") ?: ""
        set(v) = sp.edit().putString(Profiles.key("accountName"), v).apply()

    /** Another profile's account link (for the addons a profile shares from it). */
    fun accountLinkOf(id: String): String = Secure.unwrap(sp.getString(Profiles.key("accountLink", id), "") ?: "").orEmpty()

    var lastBackup: Long
        get() = sp.getLong(Profiles.key("lastBackup"), 0L)
        set(v) = sp.edit().putLong(Profiles.key("lastBackup"), v).apply()

    /** Forgets what a removed profile kept here. */
    fun forget(id: String) { sp.edit().remove(Profiles.key("accountName", id)).remove(Profiles.key("accountLink", id)).remove(Profiles.key("backupCode", id)).remove(Profiles.key("lastBackup", id)).apply() }

    /** Settings as JSON for a backup. The recovery code, last-backup time and first-run flag stay on the box. */
    fun export(): org.json.JSONObject = org.json.JSONObject(sp.all.filterKeys { !it.startsWith("tr_") && !it.startsWith("accountLink") && !it.startsWith("airing") && !it.startsWith("backupCode") && !it.startsWith("lastBackup") && it != "setupDone" })

    fun import(o: org.json.JSONObject) {
        val e = sp.edit()
        o.keys().forEach { k -> when (val v = o.get(k)) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is String -> e.putString(k, v) } }
        e.apply()
    }

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

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
