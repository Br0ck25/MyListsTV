package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tv.myliststv.launcher.data.*

/**
 * The Button Mapper's modals, in the order Settings, Remote & Buttons opens them: Add Button Mapping (press a button, or choose one
 * from the known buttons), Configure (what a newly found button does), Edit (a configured button), the action picker, and the
 * confirmations. Every one is a titled card with a scrolling list of option cards ([Sheet]). Everything saves at once, and [changed]
 * tells the page behind to redraw.
 */
object MapperDialogs {
    /** One modal: a title, a line under it, then a scrolling list of cards. */
    class Sheet(val act: Activity, title: String, subtitle: String?, private val width: Int = 480) {
        val list = Ui.vbox(act).apply { setPadding(Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3)) }
        val dialog: AlertDialog

        init {
            val c = act
            val body = Ui.vbox(c).apply { setPadding(Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 16)) }
            body.addView(Ui.text(c, title, 24f, Ui.TEXT, true))
            if (!subtitle.isNullOrBlank()) body.addView(Ui.text(c, subtitle, 14f, Ui.DIM).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 6) })
            body.addView(ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false; clipToPadding = false }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
            dialog = AlertDialog.Builder(c).setView(body).create()
        }

        fun show(): Sheet { dialog.show(); dialog.window?.setLayout(Ui.dp(act, width), ViewGroup.LayoutParams.WRAP_CONTENT); return this }
        fun dismiss() = dialog.dismiss()
        fun clear() = list.removeAllViews()
        /** Index among the cards of the one that has focus, so a redraw can put it back. */
        fun focusIndex(): Int = (0 until list.childCount).map { list.getChildAt(it) }.filter { it.isFocusable }.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
        fun focusAt(i: Int) { val f = (0 until list.childCount).map { list.getChildAt(it) }.filter { it.isFocusable }; f.getOrNull(i.coerceIn(0, (f.size - 1).coerceAtLeast(0)))?.requestFocus() }

        fun label(t: String) = list.addView(Ui.text(act, t, 13f, Ui.DIM, true), Ui.matchWrap().apply { topMargin = Ui.dp(act, 10); leftMargin = Ui.dp(act, 2) })

        /** A box that is not a button: text on a quiet card. */
        fun box(v: View): View { v.background = Ui.rounded(act, Ui.BG, Ui.R_CARD, Ui.FILL_STRONG); list.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) }); return v }

        fun card(title: String, sub: String? = null, trailing: String? = null, selected: Boolean = false, highlighted: Boolean = false, onClick: () -> Unit): View {
            val c = act
            val v = Ui.hbox(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10))
                Ui.rowStyle(c, this, Ui.R_CARD, Ui.BG)
                if (highlighted) background = Ui.rounded(c, Ui.BG, Ui.R_CARD, Ui.BRAND)
                isFocusable = true; isClickable = true; setOnClickListener { onClick() }
            }
            val mid = Ui.vbox(c)
            mid.addView(Ui.text(c, title, 15f, if (selected || highlighted) Ui.BRAND else Ui.TEXT))
            if (!sub.isNullOrBlank()) mid.addView(Ui.text(c, sub, 13f, Ui.DIM).apply { setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
            v.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            if (!trailing.isNullOrBlank()) v.addView(Ui.text(c, trailing, 13f, Ui.DIM, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 12) })
            if (selected) v.addView(ImageView(c).apply { setImageResource(R.drawable.ic_check); setColorFilter(Ui.BRAND) }, LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)).apply { leftMargin = Ui.dp(c, 10) })
            list.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(c, 6) })
            return v
        }
    }

    /** The icon of a button's row: its own for the common buttons, otherwise by kind. */
    fun iconFor(key: Int): Int = when (key) {
        KeyEvent.KEYCODE_HOME -> R.drawable.ic_home
        KeyEvent.KEYCODE_BACK -> R.drawable.ic_back
        KeyEvent.KEYCODE_MENU -> R.drawable.ic_menu
        KeyEvent.KEYCODE_SEARCH -> R.drawable.ic_search
        KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST -> R.drawable.ic_mic
        KeyEvent.KEYCODE_SETTINGS -> R.drawable.ic_settings
        KeyEvent.KEYCODE_INFO -> R.drawable.ic_info
        else -> when (categoryOf(key)) {
            "Media buttons" -> R.drawable.ic_play
            "App buttons" -> R.drawable.ic_apps
            "TV buttons" -> R.drawable.ic_tv
            "Colour buttons" -> R.drawable.ic_circle
            else -> R.drawable.ic_remote
        }
    }

    private fun pressTitle(p: String) = when (p) { "single" -> "Single Click"; "double" -> "Double Click"; else -> "Long Press" }

    private fun categoryOf(key: Int): String = ButtonMap.CATALOG.firstOrNull { g -> g.second.any { it.first == key } }?.first ?: "Other"

    private val ACTION_NOTES = mapOf(
        "home" to "Go to the Home page and scroll to the very top", "movies" to "Open the Movies page", "series" to "Open the Series page",
        "library" to "Open your Library", "apps" to "Open the Apps page", "search" to "Open Search", "settings" to "Open Settings",
        "profiles" to "Show who is watching, to switch profile",
        "media:play" to "Play or pause what is playing", "media:rewind" to "Rewind what is playing", "media:ffwd" to "Fast forward what is playing",
        "media:prev" to "Go to the previous item", "media:next" to "Go to the next item", "media:stop" to "Stop what is playing",
        "androidsettings" to "Open the Android system settings", "displaysettings" to "Open the Android display settings",
    )
    private val GROUP_NAMES = mapOf("My Lists TV" to "My Lists TV Navigation", "Profiles" to "Profiles", "Media" to "Media Controls", "Android" to "Android")

    // ---------------------------------------------------------------- Add Button Mapping

    /** Press the button on the remote (it listens for ten seconds), or choose it from the known buttons. */
    fun add(act: Activity, changed: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val s = Sheet(act, "Add Button Mapping", "Press the physical remote button you want to configure.", 460)
        val c = act
        val remote = ImageView(c).apply { setImageResource(R.drawable.ic_remote); setColorFilter(Ui.BRAND) }
        val status = Ui.text(c, "Listening for remote button press… (10s)", 16f, Ui.TEXT).apply { gravity = Gravity.CENTER }
        val inner = Ui.vbox(c).apply {
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16))
            addView(remote, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)))
            addView(status, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
            addView(Ui.text(c, "D-pad, OK, and Back navigate this dialog. Press any other remote button to detect it, or choose from known buttons below.", 13f, Ui.DIM).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 6) })
        }
        s.box(inner)
        s.card("Choose from Known Remote Buttons", "Select Home, Back, Menu, Media, Netflix, YouTube, Guide, Colored, or App buttons manually") { s.dismiss(); known(act, changed) }
        val cancel = s.card("Cancel", "Close without adding a button mapping") { s.dismiss() }
        s.show(); cancel.requestFocus()
        var left = 10
        val tick = object : Runnable { override fun run() {
            left--
            if (left <= 0) { s.dismiss(); timedOut(act, changed) } else { status.text = "Listening for remote button press… (${left}s)"; handler.postDelayed(this, 1000) }
        } }
        handler.postDelayed(tick, 1000)
        s.dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        s.dialog.setOnKeyListener { _, code, ev ->
            val nav = code in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
            if (nav || ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            s.dismiss()
            if (ButtonMap.allowed(code)) configure(act, code, changed) else reserved(act, changed)
            true
        }
    }

    private fun timedOut(act: Activity, changed: () -> Unit) {
        val s = Sheet(act, "No Button Detected", "No key event was received before the capture timeout expired.", 460)
        s.box(Ui.text(act, "If you pressed a branded shortcut button, your TV or remote firmware may reserve that key at the system level and not expose it to Android apps. Turning on the Home button fallback allows global key filtering for buttons that Android exposes.", 13f, Ui.DIM).apply { setLineSpacing(0f, 1.1f); setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12)) })
        val retry = s.card("Try Detecting Again", "Start another 10-second button capture window") { s.dismiss(); add(act, changed) }
        s.card("Choose from Known Remote Buttons", "Pick a standard Android TV remote button from the catalog") { s.dismiss(); known(act, changed) }
        s.card("Cancel", "Return to Button Mapper") { s.dismiss() }
        s.show(); retry.requestFocus()
    }

    private fun reserved(act: Activity, changed: () -> Unit) {
        val s = Sheet(act, "System-Reserved Button", "Volume, Power, Back, Home and the arrows stay system-controlled so normal TV operation and remote navigation are never disrupted.", 460)
        val again = s.card("Detect Another Button", "Press a different remote button") { s.dismiss(); add(act, changed) }
        s.card("Cancel", "Close without saving") { s.dismiss() }
        s.show(); again.requestFocus()
    }

    /** The known buttons, by kind. */
    private fun known(act: Activity, changed: () -> Unit) {
        val s = Sheet(act, "Select Remote Button", "Choose a supported remote button category.", 480)
        var first: View? = null
        ButtonMap.CATALOG.forEach { (group, buttons) ->
            s.label(group)
            buttons.forEach { (key, name) -> val v = s.card(name, "KeyCode $key", "#$key") { s.dismiss(); configure(act, key, changed) }; if (first == null) first = v }
        }
        s.show(); first?.requestFocus()
    }

    // ---------------------------------------------------------------- A newly found button

    /** What a found button will do: a draft of its three presses, saved by "Save Mapping". */
    private fun configure(act: Activity, key: Int, changed: () -> Unit) {
        val draft = HashMap<String, String?>().apply { ButtonMap.PRESSES.forEach { (p, _) -> this[p] = ButtonMap.rules().firstOrNull { it.key == key && it.press == p }?.action } }
        fun name(p: String) = draft[p]?.let { ButtonMap.actionName(act, it) } ?: "Not Assigned"
        lateinit var open: () -> Unit
        open = {
            val s = Sheet(act, "Configure ${ButtonMap.keyName(key)}", "Key code: $key • Category: ${categoryOf(key)}", 480)
            s.box(Ui.vbox(act).apply {
                setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
                addView(Ui.text(act, "Status: ${ButtonMap.badge(act, key)}", 15f, Ui.TEXT, true))
                addView(Ui.text(act, "Available in My Lists TV: Yes • Available Globally: ${if (HomeSetup.fallbackOn(act)) "Yes" else "Requires the Home button fallback"}", 13f, Ui.DIM), Ui.matchWrap().apply { topMargin = Ui.dp(act, 4) })
            })
            val first = s.card("Single Click", name("single"), "Edit") { s.dismiss(); pickAction(act, key, "single", draft["single"]) { draft["single"] = it; open() } }
            s.card("Double Click", name("double"), "Edit") { s.dismiss(); pickAction(act, key, "double", draft["double"]) { draft["double"] = it; open() } }
            s.card("Long Press", name("long"), "Edit") { s.dismiss(); pickAction(act, key, "long", draft["long"]) { draft["long"] = it; open() } }
            s.card("Save Mapping", "Save ${ButtonMap.keyName(key)} assignments to Button Mapper", "Confirm", highlighted = true) {
                ButtonMap.PRESSES.forEach { (p, _) -> ButtonMap.assign(key, p, draft[p]) }
                s.dismiss(); changed()
            }
            s.card("Detect Another Button", "Listen for a different physical button press") { s.dismiss(); add(act, changed) }
            s.card("Cancel", "Discard changes and close") { s.dismiss() }
            s.show(); first.requestFocus()
        }
        open()
    }

    // ---------------------------------------------------------------- A configured button

    /** One button: what its presses do, on or off, where it works, restore, delete. The Home button has no hold, no on/off and no scope. */
    fun edit(act: Activity, key: Int, changed: () -> Unit) {
        val home = key == ButtonMap.HOME
        val shown = if (home) "Home" else ButtonMap.keyName(key)
        val s = Sheet(act, "Edit $shown", (if (home) "" else "KeyCode $key • ") + ButtonMap.badge(act, key), 480)
        s.dialog.setOnDismissListener { changed() }
        lateinit var render: () -> Unit
        render = {
            val at = s.focusIndex()
            s.clear()
            ButtonMap.PRESSES.filter { !home || it.first != "long" }.forEach { (p, _) ->
                val mine = ButtonMap.pressLabel(act, key, p)
                val assigned = mine != "Not Assigned" && (!home || ButtonMap.rules().any { it.key == key && it.press == p })
                s.card(pressTitle(p), mine + if (home && !assigned) " (default)" else "", "Change") { pickAction(act, key, p, ButtonMap.rules().firstOrNull { it.key == key && it.press == p }?.action) { ButtonMap.assign(key, p, it); render() } }
                if (assigned) s.card("Clear ${pressTitle(p)} Assignment", "Set ${pressTitle(p)} to Not Assigned") { ButtonMap.assign(key, p, null); render() }
            }
            if (!home) {
                val scope = ButtonMap.scopeOf(key)
                s.card("Active Scope", ButtonMap.scopeName(scope), "Cycle") { ButtonMap.setScope(key, ButtonMap.SCOPES[(ButtonMap.SCOPES.indexOfFirst { it.first == scope } + 1) % ButtonMap.SCOPES.size].first); render() }
                val on = ButtonMap.keyOn(key)
                s.card(if (on) "Disable Mapping" else "Enable Mapping", if (on) "Temporarily disable custom actions for $shown" else "Re-enable custom actions for $shown", if (on) "Enabled" else "Disabled") { ButtonMap.setKeyOn(key, !on); render() }
            }
            s.card(if (home) "Restore Default Home Assignments" else "Restore Default Behavior",
                if (home) "Single: Go to top of Home • Double: Switch profile • Long: Not Assigned" else "Clear all custom actions for $shown") { ButtonMap.removeKey(key); render() }
            if (!home) s.card("Delete Mapping", "Remove $shown from the configured buttons list") {
                s.dialog.setOnDismissListener { }
                s.dismiss()
                confirm(act, "Delete Mapping?", "$shown will go back to doing what it normally does.", "Delete Mapping") { ButtonMap.removeKey(key) }.dialog.setOnDismissListener { changed() }
            }
            s.card("Done", "Return to Button Mapper", highlighted = true) { s.dismiss() }
            s.focusAt(at)
        }
        render()
        s.show(); s.focusAt(0)
    }

    /** A yes or no. Cancel has the focus first, so a stray OK keeps things as they are. */
    fun confirm(act: Activity, title: String, message: String, confirmText: String, onConfirm: () -> Unit): Sheet {
        val s = Sheet(act, title, message, 440)
        val cancel = s.card("Cancel", "Keep things as they are") { s.dismiss() }
        s.card(confirmText, null, null) { s.dismiss(); onConfirm() }
        s.show(); cancel.requestFocus()
        return s
    }

    // ---------------------------------------------------------------- Picking a job

    /** The jobs a press can do, in groups. [done] gets the job, or null for "not assigned" (the default, for Home). */
    private fun pickAction(act: Activity, key: Int, press: String, current: String?, done: (String?) -> Unit) {
        val home = key == ButtonMap.HOME
        val gesture = pressTitle(press)
        val s = Sheet(act, "${if (home) "Home" else ButtonMap.keyName(key)} — $gesture", "Select an action for this button gesture.", 480)
        s.label("Other")
        val first = if (home) s.card("Restore Default", "Reset to default (${ButtonMap.actionName(act, ButtonMap.homeAction(press) ?: "home")})", selected = current == null) { s.dismiss(); done(null) }
        else s.card("Do Nothing (Not Assigned)", "Leave this press type unassigned", selected = current == null) { s.dismiss(); done(null) }
        ButtonMap.ACTION_GROUPS.forEach { (group, acts) ->
            s.label(GROUP_NAMES[group] ?: group)
            acts.forEach { (id, name) -> s.card(name, ACTION_NOTES[id], selected = current == id) { s.dismiss(); done(id) } }
        }
        s.label("Apps")
        s.card("Launch an App…", if (current?.startsWith("app:") == true) ButtonMap.actionName(act, current) else "Open an installed app", selected = current?.startsWith("app:") == true) {
            s.dismiss()
            Http.background {
                val apps = AppRepository.load(act)
                Http.post {
                    val a = Sheet(act, "Select App", "Choose an app to launch.", 480)
                    var top: View? = null
                    apps.forEach { app -> val pkg = app.component.packageName; val v = a.card(app.label, pkg, selected = current == "app:$pkg") { a.dismiss(); done("app:$pkg") }; if (top == null) top = v }
                    a.show(); top?.requestFocus()
                }
            }
        }
        s.show(); first.requestFocus()
    }
}
