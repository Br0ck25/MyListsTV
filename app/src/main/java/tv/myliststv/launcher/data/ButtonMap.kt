package tv.myliststv.launcher.data

import tv.myliststv.launcher.*

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import tv.myliststv.launcher.MainActivity

/**
 * Remote buttons the owner has given a job: a button (the colour keys, a streaming-app key...) plus how it is pressed
 * (single, double, long) gives an action. Needs the Home button fallback service, which is what sees keys outside this app.
 * Stored as lines "keyCode|press|action"; action is one of [ACTIONS] or "app:<package>".
 */
object ButtonMap {
    data class Rule(val key: Int, val press: String, val action: String)

    val PRESSES = listOf("single" to "Press", "double" to "Double press", "long" to "Hold")
    val ACTIONS = listOf("home" to "Go to Home", "movies" to "Movies", "series" to "Series", "library" to "My list", "live" to "Live TV", "apps" to "Apps", "search" to "Search", "settings" to "Settings", "profiles" to "Switch profile")
    private val NOT_ALLOWED = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)

    fun allowed(key: Int) = key !in NOT_ALLOWED && key != KeyEvent.KEYCODE_UNKNOWN

    fun rules(): List<Rule> = Prefs.buttonMap.lines().mapNotNull { l -> l.split('|', limit = 3).takeIf { it.size == 3 }?.let { p -> p[0].toIntOrNull()?.let { Rule(it, p[1], p[2]) } } }

    fun save(l: List<Rule>) { Prefs.buttonMap = l.joinToString("\n") { "${it.key}|${it.press}|${it.action}" } }

    /** Adds a rule, replacing any earlier one for the same button and press. */
    fun add(r: Rule) = save(rules().filterNot { it.key == r.key && it.press == r.press } + r)

    fun keyName(key: Int) = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    fun pressName(p: String) = PRESSES.firstOrNull { it.first == p }?.second ?: p
    fun actionName(c: Context, a: String) = ACTIONS.firstOrNull { it.first == a }?.second
        ?: a.removePrefix("app:").let { pkg -> try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }

    /** Does the action: open an app, or tell the launcher where to go. */
    fun run(c: Context, action: String) {
        val i = if (action.startsWith("app:")) c.packageManager.getLeanbackLaunchIntentForPackage(action.removePrefix("app:")) ?: c.packageManager.getLaunchIntentForPackage(action.removePrefix("app:"))
            else Intent(c, MainActivity::class.java).putExtra("go", action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try { i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (i != null) c.startActivity(i) } catch (_: Exception) {}
    }
}
