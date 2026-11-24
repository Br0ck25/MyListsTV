package tv.myliststv.launcher.data

import tv.myliststv.launcher.*

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import tv.myliststv.launcher.MainActivity

/**
 * Remote buttons the owner has given a job: a button (the colour keys, a streaming-app key...) plus how it is pressed
 * (single, double, long) gives an action. Stored as lines "keyCode|press|action|scope"; action is one of [ACTION_GROUPS]
 * or "app:<package>"; scope is one of [SCOPES] (a line without a scope is an older rule and works everywhere).
 * A button can also be switched off on its own ([keyOn]) and the whole mapper has a master switch ([Prefs.mapperOn]).
 */
object ButtonMap {
    /** [scope] "all": everywhere. "launcher": only while My Lists TV is open. "global": only outside My Lists TV. Everywhere and outside need the Home button fallback service. */
    data class Rule(val key: Int, val press: String, val action: String, val scope: String = "all")

    val PRESSES = listOf("single" to "Single click", "double" to "Double click", "long" to "Hold")
    val SCOPES = listOf("all" to "In My Lists TV and everywhere", "launcher" to "Only while My Lists TV is open", "global" to "Only outside My Lists TV")

    /** What a button can do, grouped the way the picker shows them. */
    val ACTION_GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "My Lists TV" to listOf("home" to "Go to top of Home", "movies" to "Movies", "series" to "Series", "library" to "Library", "apps" to "Apps", "search" to "Search", "settings" to "Settings"),
        "Profiles" to listOf("profiles" to "Switch profile"),
        "Media" to listOf("media:play" to "Play / Pause", "media:rewind" to "Rewind", "media:ffwd" to "Fast forward", "media:prev" to "Previous", "media:next" to "Next", "media:stop" to "Stop"),
        "Android" to listOf("androidsettings" to "Open Android settings", "displaysettings" to "Open Display settings"),
    )
    val ACTIONS: List<Pair<String, String>> = ACTION_GROUPS.flatMap { it.second }

    private val NOT_ALLOWED = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)

    /** The buttons the picker lists, for a remote whose button does not show up when pressed. Name and key code, by group. */
    val CATALOG: List<Pair<String, List<Pair<Int, String>>>> = listOf(
        "Colour buttons" to listOf(KeyEvent.KEYCODE_PROG_RED to "Red", KeyEvent.KEYCODE_PROG_GREEN to "Green", KeyEvent.KEYCODE_PROG_YELLOW to "Yellow", KeyEvent.KEYCODE_PROG_BLUE to "Blue"),
        "Media buttons" to listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play / Pause", KeyEvent.KEYCODE_MEDIA_PLAY to "Play", KeyEvent.KEYCODE_MEDIA_PAUSE to "Pause", KeyEvent.KEYCODE_MEDIA_STOP to "Stop",
            KeyEvent.KEYCODE_MEDIA_NEXT to "Next", KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Previous", KeyEvent.KEYCODE_MEDIA_REWIND to "Rewind", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Fast forward", KeyEvent.KEYCODE_MEDIA_RECORD to "Record"),
        "TV buttons" to listOf(KeyEvent.KEYCODE_GUIDE to "Guide", KeyEvent.KEYCODE_INFO to "Info", KeyEvent.KEYCODE_MENU to "Menu", KeyEvent.KEYCODE_TV to "TV", KeyEvent.KEYCODE_TV_INPUT to "Input", KeyEvent.KEYCODE_CAPTIONS to "Subtitles",
            KeyEvent.KEYCODE_CHANNEL_UP to "Channel up", KeyEvent.KEYCODE_CHANNEL_DOWN to "Channel down", KeyEvent.KEYCODE_LAST_CHANNEL to "Last channel", KeyEvent.KEYCODE_SEARCH to "Search", KeyEvent.KEYCODE_SETTINGS to "Settings",
            KeyEvent.KEYCODE_BOOKMARK to "Bookmark", KeyEvent.KEYCODE_ASSIST to "Assistant"),
        "App buttons" to (289..296).map { it to "Video app ${it - 288}" } + (297..300).map { it to "Featured app ${it - 296}" },
    )
    private val names: Map<Int, String> = CATALOG.flatMap { it.second }.toMap()

    const val HOME = KeyEvent.KEYCODE_HOME

    /** What the Home button does for a press: the owner's job when Customize Home Button is on, otherwise the factory one. Hold cannot be told apart for Home. */
    fun homeAction(press: String): String? {
        val factory = when (press) { "single" -> "home"; "double" -> "profiles"; else -> null }
        if (!Prefs.homeCustom || press == "long") return factory
        return rules().firstOrNull { it.key == HOME && it.press == press }?.action ?: factory
    }

    fun allowed(key: Int) = key !in NOT_ALLOWED && key != KeyEvent.KEYCODE_UNKNOWN

    fun rules(): List<Rule> = Prefs.buttonMap.lines().mapNotNull { l ->
        l.split('|', limit = 4).takeIf { it.size >= 3 }?.let { p -> p[0].toIntOrNull()?.let { Rule(it, p[1], p[2], p.getOrNull(3)?.takeIf { s -> SCOPES.any { it.first == s } } ?: "all") } }
    }

    fun save(l: List<Rule>) { Prefs.buttonMap = l.joinToString("\n") { "${it.key}|${it.press}|${it.action}|${it.scope}" } }

    /** Adds a rule, replacing any earlier one for the same button and press. */
    fun add(r: Rule) = save(rules().filterNot { it.key == r.key && it.press == r.press } + r)

    /** Gives one press of a button a job (null clears it). A button has one scope, so the new rule takes the scope its other presses already have. */
    fun assign(key: Int, press: String, action: String?, scope: String = scopeOf(key)) {
        val rest = rules().filterNot { it.key == key && it.press == press }
        save(if (action == null) rest else rest + Rule(key, press, action, scope))
    }

    fun scopeOf(key: Int) = rules().firstOrNull { it.key == key }?.scope ?: "all"
    fun setScope(key: Int, scope: String) = save(rules().map { if (it.key == key) it.copy(scope = scope) else it })
    fun removeKey(key: Int) { save(rules().filterNot { it.key == key }); Prefs.buttonMapOff = Prefs.buttonMapOff - key.toString() }
    fun clearAll() { save(emptyList()); Prefs.buttonMapOff = emptyList() }

    fun keyOn(key: Int) = key.toString() !in Prefs.buttonMapOff
    fun setKeyOn(key: Int, on: Boolean) { Prefs.buttonMapOff = if (on) Prefs.buttonMapOff - key.toString() else (Prefs.buttonMapOff + key.toString()).distinct() }

    /** Is this rule in force: the mapper is on and so is its button. */
    fun active(r: Rule) = Prefs.mapperOn && keyOn(r.key)

    /** In plain words, whether a button works right now. */
    fun status(c: Context, key: Int): String {
        val scope = scopeOf(key)
        if (key == HOME) return if (HomeSetup.isHome(c) || HomeSetup.fallbackOn(c)) "Works while My Lists TV is your Home app" else "Needs My Lists TV as your Home app (Launcher setup)"
        return when {
            !Prefs.mapperOn -> "The Button Mapper is off"
            !keyOn(key) -> "Off"
            scope == "launcher" -> "Works while My Lists TV is open"
            HomeSetup.fallbackOn(c) -> if (scope == "global") "Works outside My Lists TV" else "Works in every app"
            scope == "global" -> "Needs the Home button fallback (Launcher setup)"
            else -> "Works while My Lists TV is open. Turn on the Home button fallback for every app"
        }
    }

    /** The short word at the right of a button's row. */
    fun badge(c: Context, key: Int): String {
        val scope = scopeOf(key)
        return when {
            !Prefs.mapperOn -> "Mapper Disabled"
            key == HOME && !Prefs.homeCustom -> "Factory Default"
            key != HOME && !keyOn(key) -> "Disabled"
            key == HOME -> if (HomeSetup.isHome(c) || HomeSetup.fallbackOn(c)) "Available Globally" else "Needs Home app"
            scope == "launcher" -> "In My Lists TV only"
            HomeSetup.fallbackOn(c) -> if (scope == "global") "Outside My Lists TV" else "Available Globally"
            else -> "In My Lists TV only"
        }
    }

    /** What one press of a button does, in words ("Not Assigned" when nothing). */
    fun pressLabel(c: Context, key: Int, press: String): String =
        (if (key == HOME) homeAction(press) else rules().firstOrNull { it.key == key && it.press == press }?.action)?.let { actionName(c, it) } ?: "Not Assigned"

    fun scopeName(scope: String) = SCOPES.firstOrNull { it.first == scope }?.second ?: scope
    fun keyName(key: Int) = names[key] ?: KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    fun pressName(p: String) = PRESSES.firstOrNull { it.first == p }?.second ?: p
    fun actionName(c: Context, a: String) = ACTIONS.firstOrNull { it.first == a }?.second
        ?: a.removePrefix("app:").let { pkg -> try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }

    /** Does the action: open an app or a system page, press a media key, or tell the launcher where to go. */
    fun run(c: Context, action: String) {
        if (action.startsWith("media:")) { media(c, action); return }
        val i = when {
            action.startsWith("app:") -> c.packageManager.getLeanbackLaunchIntentForPackage(action.removePrefix("app:")) ?: c.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
            action == "androidsettings" -> Intent(Settings.ACTION_SETTINGS)
            action == "displaysettings" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> Intent(c, MainActivity::class.java).putExtra("go", action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try { i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (i != null) c.startActivity(i) } catch (_: Exception) {}
    }

    private fun media(c: Context, action: String) {
        val code = when (action) { "media:play" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; "media:rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND; "media:ffwd" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "media:prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "media:next" -> KeyEvent.KEYCODE_MEDIA_NEXT; else -> KeyEvent.KEYCODE_MEDIA_STOP }
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
