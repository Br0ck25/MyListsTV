package tv.myliststv.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Settings, built from rows (docs/DESIGN.md section 4): every on/off is a toggle, a choice among a few values is a
 * choice row (OK or Left/Right changes it), anything else is an action. Changes apply at once and are stored in [Prefs].
 */
class SettingsActivity : Activity() {
    /** kind: H header, T toggle, C choice, A action, I info. [act] gets +1 or -1 for choices, anything for the rest. */
    private class Row(val kind: Char, val label: String, val desc: String = "", val value: () -> String = { "" }, val on: () -> Boolean = { false }, val act: (Int) -> Unit = {})

    private fun header(t: String) = Row('H', t)
    private fun toggle(label: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) = Row('T', label, desc, on = get, act = { set(!get()) })
    private fun <T> choice(label: String, desc: String, opts: List<T>, get: () -> T, set: (T) -> Unit, show: (T) -> String = { it.toString() }) =
        Row('C', label, desc, value = { show(get()) }, act = { d -> set(opts[(opts.indexOf(get()).coerceAtLeast(0) + d + opts.size) % opts.size]) })
    private fun action(label: String, desc: String = "", run: () -> Unit) = Row('A', label, desc, act = { run() })
    private fun info(label: String, value: String) = Row('I', label, value = { value })

    private lateinit var col: LinearLayout

    private fun profileMenu() {
        val l = Profiles.all()
        val items = l.map { it.name + if (it.kids) " (kids)" else "" } + listOf("Add profile", "Add kids profile", "Remove ${Profiles.active().name}")
        android.app.AlertDialog.Builder(this).setTitle("Profiles").setItems(items.toTypedArray()) { _, i ->
            when {
                i < l.size -> { Profiles.activeId = l[i].id; WatchStore.init(this); build(col) }
                i == l.size -> askName(false)
                i == l.size + 1 -> askName(true)
                else -> { Profiles.remove(Profiles.activeId); WatchStore.init(this); build(col) }
            }
        }.show()
    }

    private fun askName(kids: Boolean) {
        val e = android.widget.EditText(this).apply { setSingleLine(true); setTextColor(0xFF000000.toInt()) }
        android.app.AlertDialog.Builder(this).setTitle(if (kids) "New kids profile" else "New profile").setView(e)
            .setPositiveButton("Add") { _, _ -> Profiles.add(e.text.toString(), kids); build(col) }.setNegativeButton("Cancel", null).show()
    }

    private fun backupNow() {
        val first = Prefs.backupCode.isEmpty()
        val code = Backup.codeForThisBox()
        Toast.makeText(this, "Backing up\u2026", Toast.LENGTH_SHORT).show()
        Backup.upload(code) { msg ->
            if (first) showCode(code, msg) else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            build(col)
        }
    }

    private fun showCode(code: String, extra: String = "") = android.app.AlertDialog.Builder(this).setTitle("Your recovery code")
        .setMessage("$code\n\nWrite this down. It is the only way to restore your setup on another box, and My Lists cannot recover it for you.${if (extra.isNotEmpty()) "\n\n$extra" else ""}")
        .setPositiveButton("I wrote it down", null).show()

    private fun restoreDialog() {
        val e = android.widget.EditText(this).apply { hint = "XXXX-XXXX-XXXX-XXXX"; setSingleLine(true); setTextColor(0xFF000000.toInt()); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        android.app.AlertDialog.Builder(this).setTitle("Restore a backup").setMessage("Enter the recovery code from your backup. This replaces your addons, playlists, profiles and settings on this box.").setView(e)
            .setPositiveButton("Restore") { _, _ ->
                val code = Backup.clean(e.text.toString())
                if (!Backup.isCode(code)) Toast.makeText(this, "That does not look like a recovery code.", Toast.LENGTH_LONG).show()
                else { Toast.makeText(this, "Restoring\u2026", Toast.LENGTH_SHORT).show(); Backup.restore(this, code) { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); build(col) } }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun lastBackupText() = Prefs.lastBackup.let { if (it == 0L) "Never" else android.text.format.DateFormat.format("MMM d, h:mm a", it).toString() }

    private fun signIn() {
        Toast.makeText(this, "Getting a code\u2026", Toast.LENGTH_SHORT).show()
        Pairing.start { s ->
            if (s == null) { Toast.makeText(this, "Could not reach My Lists. Check your network.", Toast.LENGTH_LONG).show(); return@start }
            val dlg = android.app.AlertDialog.Builder(this).setTitle("Sign in with My Lists").setCancelable(false)
                .setMessage("On your phone, open\n${Pairing.linkUrl(s)}\n\nSign in there and tap Link. This screen updates by itself.\n\nCode: ${s.userCode}")
                .setNegativeButton("Cancel") { _, _ -> Pairing.cancel() }.show()
            Pairing.poll(s) { link ->
                dlg.dismiss()
                if (link == null) Toast.makeText(this, "The code expired. Try again.", Toast.LENGTH_LONG).show()
                else { Pairing.signIn(link); Toast.makeText(this, "Signed in as ${Pairing.userOf(link)}. Your lists replace the defaults.", Toast.LENGTH_LONG).show() }
                build(col)
            }
        }
    }

    private fun signOut() = android.app.AlertDialog.Builder(this).setTitle("Sign out of My Lists?")
        .setMessage("Home goes back to the default catalogs. Your other addons stay.")
        .setPositiveButton("Keep signed in", null).setNegativeButton("Sign out") { _, _ -> Pairing.signOut(); build(col) }.show()

    private fun rows(): List<Row> = buildList {
        add(header("General"))
        if (Prefs.accountLink.isEmpty()) add(action("Sign in with My Lists", "Replaces the default catalogs with your own lists. Takes 30 seconds with your phone.") { signIn() })
        else { add(info("Signed in", Pairing.userOf(Prefs.accountLink))); add(action("Sign out", "Goes back to the default catalogs. Your other addons stay.") { signOut() }) }
        add(action("Switch profile", "Now: ${Profiles.active().name}${if (Profiles.active().kids) " (kids)" else ""}. Each profile keeps its own history.") { profileMenu() })
        add(toggle("Advanced settings", "Shows more options in each group.", { Prefs.advanced }, { Prefs.advanced = it }))
        add(header("Playback"))
        add(toggle("Auto-pick the best source", "Play starts at once. Off shows the source list first.", { Prefs.autoPick }, { Prefs.autoPick = it }))
        add(choice("Maximum resolution", "Auto keeps weak boxes at 1080p.", listOf(0, 2160, 1080, 720), { Prefs.maxResolution }, { Prefs.maxResolution = it }, { if (it == 0) "Auto" else "${it}p" }))
        add(choice("Subtitle language", "Used when a file or a subtitle addon has it.", listOf("en", "es", "fr", "de", "off"), { Prefs.subtitleLang }, { Prefs.subtitleLang = it }, { if (it == "off") "Off" else it.uppercase() }))
        add(toggle("Play next episode automatically", "", { Prefs.autoplayNext }, { Prefs.autoplayNext = it }))
        add(toggle("Show Skip intro", "A button appears during intros and recaps. Times come from IntroDB.", { Prefs.skipIntro }, { Prefs.skipIntro = it }))
        if (Prefs.advanced) {
            add(toggle("Try the next source if one fails", "Switches by itself when a source does not start.", { Prefs.fallbackOn }, { Prefs.fallbackOn = it }))
            add(choice("Source tries", "How many other sources to try.", listOf(1, 2, 3, 5), { Prefs.fallbackTries }, { Prefs.fallbackTries = it }))
            add(choice("Source start timeout", "How long to wait for the first picture.", listOf(6, 10, 15), { Prefs.startupTimeoutSec }, { Prefs.startupTimeoutSec = it }, { "$it s" }))
        }
        add(header("Home"))
        add(toggle("Show Continue watching", "", { Prefs.showContinue }, { Prefs.showContinue = it }))
        add(toggle("Show apps row", "", { Prefs.showAppsRow }, { Prefs.showAppsRow = it }))
        add(header("Backup"))
        add(action("Back up my setup", "Encrypts your addons, playlists, profiles and settings. Last backup: ${lastBackupText()}.") { backupNow() })
        add(action("Restore a backup", "Use your recovery code. You can also paste it on the phone page in the Addons tab.") { restoreDialog() })
        if (Prefs.backupCode.isNotEmpty()) add(action("Show recovery code", "The code that opens your backup.") { showCode(Prefs.backupCode) })
        add(header("Performance"))
        add(choice("Lite mode", "Less motion and memory. Auto turns on for weak boxes.", listOf("Auto", "On", "Off"), { Prefs.liteMode }, { Prefs.liteMode = it }))
        add(action("Clear caches", "Frees stored images and catalog data (${Http.cacheBytes() / 1_048_576} MB now).") { Http.clear(); ImageLoader.clearDisk(); Toast.makeText(this@SettingsActivity, "Caches cleared", Toast.LENGTH_SHORT).show() })
        add(header("About"))
        add(action("Check for updates", "Version 0.1.${Updater.installedBuild(this@SettingsActivity)} installed.") { checkUpdate() })
        add(info("Make this your home screen", "adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity"))
        add(info("Privacy", "No analytics, no tracking. Skip-intro times from IntroDB (introdb.app)."))
    }

    private fun checkUpdate() {
        Toast.makeText(this, "Checking for updates\u2026", Toast.LENGTH_SHORT).show()
        Updater.latest { r ->
            val mine = Updater.installedBuild(this)
            if (r == null) Toast.makeText(this, "Could not check right now.", Toast.LENGTH_LONG).show()
            else if (r.build <= mine) Toast.makeText(this, "You have the latest version.", Toast.LENGTH_LONG).show()
            else android.app.AlertDialog.Builder(this).setTitle("Update available").setMessage("${r.tag} is ready. You have 0.1.$mine.")
                .setPositiveButton("Install") { _, _ -> Updater.install(this, r) { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() } }
                .setNegativeButton("Later", null).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 80), Ui.dp(this@SettingsActivity, 24), Ui.dp(this@SettingsActivity, 80), Ui.dp(this@SettingsActivity, 24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(col) })
        build(col)
    }

    /** Rebuilds every row (so descriptions and values are current) and puts focus back on the same row. */
    private fun build(col: LinearLayout, keep: Int = -1) {
        col.removeAllViews()
        col.addView(Ui.text(this, "Settings", 24f, Ui.TEXT, true))
        rows().forEachIndexed { i, r ->
            val v = view(r) { rebuild(col, i) }
            col.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, if (r.kind == 'H') 20 else 8) })
        }
        col.post { (if (keep >= 0) col.getChildAt(keep + 1) else (1 until col.childCount).map { col.getChildAt(it) }.firstOrNull { it.isFocusable })?.requestFocus() }
    }

    private fun rebuild(col: LinearLayout, rowIndex: Int) {
        // Row i is child i + 1 (the title is child 0). An action may change which rows exist, so clamp.
        build(col, rowIndex.coerceAtMost(rows().size - 1))
    }

    private fun view(r: Row, changed: () -> Unit): android.view.View {
        if (r.kind == 'H') return Ui.text(this, r.label.uppercase(), 12f, Ui.DIM, true)
        val c = this
        val row = Ui.hbox(c).apply { setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12)); Ui.rowStyle(c, this, fill = if (r.kind == 'I') Ui.BG else Ui.CARD) }
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, r.label, 16f, Ui.TEXT, true, 1))
        if (r.desc.isNotEmpty()) mid.addView(Ui.text(c, r.desc, 12f, Ui.DIM, false, 2))
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        val gap = Ui.dp(c, 16)
        when (r.kind) {
            'T' -> row.addView(Toggle(c, r.on()), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'C' -> row.addView(Ui.text(c, "\u2039  ${r.value()}  \u203a", 15f, Ui.TEXT, true).apply { setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6)); background = Ui.pillBg(c, 0x1AFFFFFF, 0x1AFFFFFF) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'A' -> row.addView(Ui.text(c, "\u203a", 22f, Ui.DIM, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'I' -> { row.isFocusable = false; row.isClickable = false; mid.getChildAt(0).let { (it as TextView).setTextColor(Ui.TEXT2) }
                if (r.value().isNotEmpty()) row.addView(Ui.text(c, r.value(), 12f, Ui.DIM, false, 2).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(c, 360), -2).apply { leftMargin = gap }) }
        }
        if (r.kind != 'I') {
            row.isFocusable = true; row.isClickable = true
            row.setOnClickListener { r.act(1); changed() }
            if (r.kind == 'C') row.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { r.act(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); changed(); true } else false
            }
        }
        return row
    }
}
