package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.app.AlertDialog
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import tv.myliststv.launcher.data.*

/**
 * The Button Mapper's dialogs, opened from Settings, Remote & Buttons: "Add Button Mapping" (press a button, or choose one from a list)
 * and a button's own dialog with its single click, double click and hold, on or off, where it works, and delete. Everything saves
 * at once; [changed] tells the page behind to redraw.
 */
object MapperDialogs {
    /** Press a button on the remote, or choose it from a list when the remote does not report it. */
    fun add(act: Activity, changed: () -> Unit) {
        val d = Ui.dialog(act, "Press a button on your remote", "Press the button you want to use. The arrows, OK, Back, Home, volume and power cannot be changed.",
            Ui.Act("Choose from a list", true) { chooseFromList(act, changed) }, Ui.Act("Cancel"))
        d.setOnKeyListener { _, code, ev ->
            val moving = code in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)
            if (moving || ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (!ButtonMap.allowed(code)) { Toast.makeText(act, "That button cannot be changed. Try another.", Toast.LENGTH_SHORT).show(); return@setOnKeyListener true }
            d.dismiss(); edit(act, code, changed); true
        }
    }

    private fun chooseFromList(act: Activity, changed: () -> Unit) {
        AlertDialog.Builder(act).setTitle("Which kind of button?").setItems(ButtonMap.CATALOG.map { it.first }.toTypedArray()) { _, g ->
            val buttons = ButtonMap.CATALOG[g].second
            AlertDialog.Builder(act).setTitle(ButtonMap.CATALOG[g].first).setItems(buttons.map { it.second }.toTypedArray()) { _, i -> edit(act, buttons[i].first, changed) }.show()
        }.show()
    }

    /** One button: what its presses do, on or off, where it works, delete. The Home button has no hold, no on/off and no scope, and "restore default" instead of delete. */
    fun edit(act: Activity, key: Int, changed: () -> Unit) {
        val c = act
        val home = key == ButtonMap.HOME
        val body = Ui.vbox(c).apply { minimumWidth = Ui.dp(c, 330); setPadding(Ui.dp(c, 20), Ui.dp(c, 18), Ui.dp(c, 20), Ui.dp(c, 14)) }
        val scroll = ScrollView(c).apply { addView(body); isVerticalScrollBarEnabled = false }
        val d = AlertDialog.Builder(c).setView(scroll).create()
        d.setOnDismissListener { changed() }

        fun option(title: String, sub: String, trailing: View?, onClick: () -> Unit): View = Ui.hbox(c).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); Ui.rowStyle(c, this, fill = Ui.BG)
            isFocusable = true; isClickable = true; setOnClickListener { onClick() }
            val mid = Ui.vbox(c)
            mid.addView(Ui.text(c, title, 15f, Ui.TEXT, true, 1))
            if (sub.isNotEmpty()) mid.addView(Ui.text(c, sub, 12f, Ui.DIM, false, 2))
            addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            if (trailing != null) addView(trailing, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 12) })
        }

        lateinit var render: () -> Unit
        fun focusIndex(): Int = (0 until body.childCount).map { body.getChildAt(it) }.filter { it.isFocusable }.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
        render = {
            val at = focusIndex()
            body.removeAllViews()
            val rs = ButtonMap.rules().filter { it.key == key }
            body.addView(Ui.text(c, "Edit ${if (home) "Home" else ButtonMap.keyName(key)}", 20f, Ui.TEXT, true))
            body.addView(Ui.text(c, (if (home) "" else "Key code $key  ·  ") + ButtonMap.status(c, key), 12f, Ui.DIM, false, 2), Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
            fun add(v: View) = body.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
            ButtonMap.PRESSES.filter { !home || it.first != "long" }.forEach { (p, label) ->
                val mine = rs.firstOrNull { it.press == p }
                val shown = if (home) ButtonMap.homeAction(p)?.let { ButtonMap.actionName(c, it) + if (mine == null || !Prefs.homeCustom) " (default)" else "" } else mine?.let { ButtonMap.actionName(c, it.action) }
                add(option(label, shown ?: "Not assigned", Ui.text(c, "Change", 13f, Ui.DIM, true)) { pickAction(c, key, p) { render() } })
                if (mine != null) add(option("Clear $label assignment", if (home) "Use the default again" else "Set $label to Not assigned", null) { ButtonMap.assign(key, p, null); render() })
            }
            if (home) {
                if (rs.isNotEmpty()) add(option("Restore default", "Single click: Home. Double click: switch profile.", null) { ButtonMap.removeKey(key); render() })
                if (!Prefs.homeCustom) body.addView(Ui.text(c, "Turn on Customize Home Button for these jobs to be used.", 12f, Ui.DIM, false, 2), Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
            } else {
                add(option("Enabled", "Off keeps these jobs but stops using them.", Toggle(c, ButtonMap.keyOn(key))) { ButtonMap.setKeyOn(key, !ButtonMap.keyOn(key)); render() })
                val scope = ButtonMap.scopeOf(key)
                add(option("Works", "Where the button does these jobs. Outside My Lists TV needs the Home button fallback.", Ui.text(c, "‹  ${ButtonMap.scopeName(scope)}  ›", 13f, Ui.TEXT, true)) {
                    ButtonMap.setScope(key, ButtonMap.SCOPES[(ButtonMap.SCOPES.indexOfFirst { it.first == scope } + 1) % ButtonMap.SCOPES.size].first); render()
                })
                add(option("Delete this button", "Forgets its jobs. It goes back to doing what it normally does.", null) {
                    Ui.dialog(c, "Delete ${ButtonMap.keyName(key)}?", "Its jobs are removed.", Ui.Act("Delete", true) { ButtonMap.removeKey(key); d.dismiss() }, Ui.Act("Keep"))
                })
            }
            val rows = (0 until body.childCount).map { body.getChildAt(it) }.filter { it.isFocusable }
            rows.getOrNull(at.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))?.requestFocus()
        }
        render()
        d.show()
        d.window?.setLayout(Ui.dp(c, 360), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Kind of job first, then the job. "Not assigned" clears the press. */
    private fun pickAction(act: Activity, key: Int, press: String, done: () -> Unit) {
        val groups = ButtonMap.ACTION_GROUPS
        val title = "${if (key == ButtonMap.HOME) "Home" else ButtonMap.keyName(key)}: ${ButtonMap.pressName(press)}"
        val set = { action: String? -> ButtonMap.assign(key, press, action); done() }
        AlertDialog.Builder(act).setTitle(title).setItems((listOf(if (key == ButtonMap.HOME) "Default" else "Not assigned") + groups.map { it.first } + "An app…").toTypedArray()) { _, i ->
            when {
                i == 0 -> set(null)
                i <= groups.size -> {
                    val acts = groups[i - 1].second
                    AlertDialog.Builder(act).setTitle(groups[i - 1].first).setItems(acts.map { it.second }.toTypedArray()) { _, j -> set(acts[j].first) }.show()
                }
                else -> Http.background {
                    val apps = AppRepository.load(act)
                    Http.post { AlertDialog.Builder(act).setTitle("Open which app?").setItems(apps.map { it.label }.toTypedArray()) { _, k -> set("app:" + apps[k].component.packageName) }.show() }
                }
            }
        }.show()
    }
}
