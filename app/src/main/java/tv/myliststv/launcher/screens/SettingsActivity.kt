package tv.myliststv.launcher.screens

import tv.myliststv.launcher.*
import tv.myliststv.launcher.services.*

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Settings as a split menu (docs/DESIGN.md section 4): the groups run down the left, and the rows of the group under the
 * focus show on the right. Every on/off is a toggle, a choice among a few values is a choice row (OK or Left/Right changes
 * it), anything else is an action. Changes apply at once and are stored in [Prefs]. Back returns from the rows to the groups.
 */
class SettingsActivity : Activity() {
    /** kind: H header (starts a group), T toggle, C choice, A action, I info. [act] gets +1 or -1 for choices, anything else otherwise. */
    private class Row(val kind: Char, val label: String, val desc: String = "", val value: () -> String = { "" }, val on: () -> Boolean = { false }, val act: (Int) -> Unit = {})

    private fun header(t: String) = Row('H', t)
    private fun toggle(label: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) = Row('T', label, desc, on = get, act = { set(!get()) })
    private fun <T> choice(label: String, desc: String, opts: List<T>, get: () -> T, set: (T) -> Unit, show: (T) -> String = { it.toString() }) =
        Row('C', label, desc, value = { show(get()) }, act = { d -> set(opts[(opts.indexOf(get()).coerceAtLeast(0) + d + opts.size) % opts.size]) })
    private fun action(label: String, desc: String = "", run: () -> Unit) = Row('A', label, desc, act = { run() })
    private fun info(label: String, value: String) = Row('I', label, value = { value })

    private lateinit var rail: LinearLayout
    private lateinit var col: LinearLayout
    private var cat = 0
    private var groups: List<Pair<String, List<Row>>> = emptyList()

    override fun onResume() { super.onResume(); if (::rail.isInitialized) refresh() }

    private fun androidSettings() {
        val pages = listOf("All settings" to Settings.ACTION_SETTINGS, "Wi-Fi and network" to Settings.ACTION_WIFI_SETTINGS, "Display" to Settings.ACTION_DISPLAY_SETTINGS,
            "Sound" to Settings.ACTION_SOUND_SETTINGS, "Bluetooth and remotes" to Settings.ACTION_BLUETOOTH_SETTINGS, "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "Date and time" to Settings.ACTION_DATE_SETTINGS, "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        android.app.AlertDialog.Builder(this).setTitle("Android settings").setItems(pages.map { it.first }.toTypedArray()) { _, i ->
            try { startActivity(Intent(pages[i].second)) } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { Toast.makeText(this, "This TV does not open its settings from apps.", Toast.LENGTH_LONG).show() }
            }
        }.show()
    }

    /** Press a button, say how it is pressed, say what it does. */
    private fun addButton() {
        val d = android.app.AlertDialog.Builder(this).setTitle("Press a button on your remote").setMessage("Press the button you want to use.").setNegativeButton("Cancel", null).create()
        d.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN || code == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (!ButtonMap.allowed(code)) { Toast.makeText(this, "That button cannot be changed. Try another.", Toast.LENGTH_SHORT).show(); return@setOnKeyListener true }
            d.dismiss(); pickPress(code); true
        }
        d.show()
    }

    private fun pickPress(key: Int) = android.app.AlertDialog.Builder(this).setTitle("${ButtonMap.keyName(key)}: how is it pressed?")
        .setItems(ButtonMap.PRESSES.map { it.second }.toTypedArray()) { _, i -> pickAction(key, ButtonMap.PRESSES[i].first) }.show()

    private fun pickAction(key: Int, press: String) {
        val names = ButtonMap.ACTIONS.map { it.second } + "Open an app\u2026"
        android.app.AlertDialog.Builder(this).setTitle("What should it do?").setItems(names.toTypedArray()) { _, i ->
            if (i < ButtonMap.ACTIONS.size) { ButtonMap.add(ButtonMap.Rule(key, press, ButtonMap.ACTIONS[i].first)); refresh() }
            else Http.background {
                val apps = AppRepository.load(this)
                Http.post { android.app.AlertDialog.Builder(this).setTitle("Open which app?").setItems(apps.map { it.label }.toTypedArray()) { _, k -> ButtonMap.add(ButtonMap.Rule(key, press, "app:" + apps[k].component.packageName)); refresh() }.show() }
            }
        }.show()
    }

    /** Asks each stream addon for one well-known title and reports what each did. */
    private fun testSources() {
        if (StreamResolver.providers("movie", "tt0816692").isEmpty()) { android.app.AlertDialog.Builder(this).setTitle("Test my sources").setMessage("No stream addon is installed. Add one under Addons.").setPositiveButton("OK", null).show(); return }
        Toast.makeText(this, "Testing\u2026", Toast.LENGTH_SHORT).show()
        StreamResolver.resolve("movie", "tt0816692", StreamResolver.DEFAULT_TIMEOUT_MS, StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { _, st ->
            val lines = st.joinToString("\n\n") { (if (it.ok && it.count > 0) "\u2713 " else "\u2717 ") + it.name + ": " + (if (it.ok && it.count > 0) "${it.count} sources in ${"%.1f".format(it.millis / 1000.0)} s" else (it.note ?: "gave nothing")) }
            android.app.AlertDialog.Builder(this).setTitle("Test my sources").setMessage(lines.ifEmpty { "No answers." }).setPositiveButton("OK", null).show()
        }))
    }

    private fun requestHome() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) startActivityForResult(getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 1)
            else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { Toast.makeText(this, "This TV has no Home setting. Use the phone or computer steps.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun setupPage() {
        val server = PhoneServer { _, done -> done("Nothing to add here.") }
        server.start()
        val a = server.address()?.let { "$it/setup" } ?: "Connect the TV to Wi-Fi first."
        android.app.AlertDialog.Builder(this).setTitle("Open this on your phone").setMessage(a)
            .setPositiveButton("Done", null).setOnDismissListener { server.stop() }.show()
    }

    private fun profileMenu() {
        val l = Profiles.all()
        val items = l.map { it.name + if (it.kids) " (kids)" else "" } + listOf("Add profile", "Add kids profile", "Remove ${Profiles.active().name}")
        android.app.AlertDialog.Builder(this).setTitle("Profiles").setItems(items.toTypedArray()) { _, i ->
            when {
                i < l.size -> { Profiles.switchTo(this, l[i].id); refresh() }
                i == l.size -> askName(false)
                i == l.size + 1 -> askName(true)
                else -> { Profiles.remove(Profiles.activeId); Profiles.switchTo(this, Profiles.activeId); refresh() }
            }
        }.show()
    }

    /** Choose another profile whose addons this one uses, or stop sharing. */
    private fun shareMenu() {
        val me = Profiles.activeId
        val others = Profiles.all().filter { it.id != me && Profiles.addonSource(it.id) == null }
        val items = others.map { "Use ${it.name}'s addons" } + if (Profiles.addonSource() != null) listOf("Stop sharing (keep a copy)") else emptyList()
        if (items.isEmpty()) { Toast.makeText(this, "No other profile can be shared from.", Toast.LENGTH_SHORT).show(); return }
        android.app.AlertDialog.Builder(this).setTitle("Share addons").setItems(items.toTypedArray()) { _, i ->
            if (i < others.size) Profiles.shareAddons(me, others[i].id) else AddonStore.stopSharing(me)
            AddonStore.needsRefresh = true; refresh()
        }.show()
    }

    private fun askName(kids: Boolean) = Ui.askText(this, if (kids) "New kids profile" else "New profile", "Name", "Add") { Profiles.add(it, kids); refresh() }

    /**
     * One dialog for ordering and hiding anything in a list (Home rows, apps). OK on an item opens its menu; the dialog
     * comes back afterwards so several moves are quick. [save] gets the full order and the hidden ones.
     */
    private fun arrange(title: String, keys: List<Pair<String, String>>, hidden: List<String>, at: Int = 0, save: (List<String>, List<String>) -> Unit) {
        val order = keys.map { it.first }
        val names = keys.associate { it }
        android.app.AlertDialog.Builder(this).setTitle(title)
            .setItems(order.map { names[it] + if (it in hidden) "  (hidden)" else "" }.toTypedArray()) { _, i ->
                val k = order[i]
                android.app.AlertDialog.Builder(this).setTitle(names[k]).setItems(arrayOf("Move up", "Move down", if (k in hidden) "Show on Home" else "Hide from Home")) { _, m ->
                    val o = order.toMutableList(); var h = hidden
                    when (m) {
                        0 -> if (i > 0) o.add(i - 1, o.removeAt(i))
                        1 -> if (i < o.size - 1) o.add(i + 1, o.removeAt(i))
                        else -> h = if (k in h) h - k else h + k
                    }
                    save(o, h)
                    arrange(title, o.map { it to names[it]!! }, h, if (m == 0) i - 1 else if (m == 1) i + 1 else i, save)
                }.show()
            }.setPositiveButton("Done") { _, _ -> refresh() }.setOnCancelListener { refresh() }.create().also { it.listView.setSelection(at.coerceAtLeast(0)); it.show() }
    }

    private fun arrangeRows() {
        val titles = listOf("Continue watching") + Catalogs.browseRows(null, 8).map { (_, c) -> Catalogs.rowTitle(null, c) } + "Your apps"
        arrange("Home rows", titles.distinct().sortedBy { Prefs.homeRank(it) }.map { it to it }, Prefs.homeHidden) { o, h -> Prefs.homeOrder = o; Prefs.homeHidden = h }
    }

    private fun arrangeApps() = Http.background {
        val order = Prefs.appOrder
        val apps = AppRepository.load(this).sortedBy { order.indexOf(it.component.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        Http.post { arrange("Apps row", apps.map { it.component.packageName to it.label }, Prefs.appHidden) { o, h -> Prefs.appOrder = o; Prefs.appHidden = h } }
    }

    private fun backupNow() {
        val first = Prefs.backupCode.isEmpty()
        val code = Backup.codeForThisBox()
        Toast.makeText(this, "Backing up\u2026", Toast.LENGTH_SHORT).show()
        Backup.upload(code) { msg ->
            if (first) showCode(code, msg) else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun showCode(code: String, extra: String = "") = android.app.AlertDialog.Builder(this).setTitle("Your recovery code")
        .setMessage("$code\n\nWrite this down. It is the only way to restore your setup on another box, and My Lists cannot recover it for you.${if (extra.isNotEmpty()) "\n\n$extra" else ""}")
        .setPositiveButton("I wrote it down", null).show()

    private fun restoreDialog() = Ui.askText(this, "Restore a backup", "XXXX-XXXX-XXXX-XXXX", "Restore", "Enter the recovery code from your backup. This replaces the addons and Library of the profile in use.", caps = true) { raw ->
        val code = Backup.clean(raw)
        if (!Backup.isCode(code)) Toast.makeText(this, "That does not look like a recovery code.", Toast.LENGTH_LONG).show()
        else { Toast.makeText(this, "Restoring\u2026", Toast.LENGTH_SHORT).show(); Backup.restore(this, code) { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); refresh() } }
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
                refresh()
            }
        }
    }

    private fun signOut() = android.app.AlertDialog.Builder(this).setTitle("Sign out of My Lists?")
        .setMessage("Home goes back to the default catalogs. Your other addons stay.")
        .setPositiveButton("Keep signed in", null).setNegativeButton("Sign out") { _, _ -> Pairing.signOut(); refresh() }.show()

    private fun rows(): List<Row> = buildList {
        add(header("Addons"))
        add(action("Manage addons", "Catalog and stream addons, order, on and off. You can paste links from your phone.") { startActivity(Intent(this@SettingsActivity, AddonsActivity::class.java)) })
        add(action("Test my sources", "Asks every stream addon for a well-known movie and says which answered, how fast, and why not if one did not.") { testSources() })
        add(header("General"))
        if (Prefs.accountLink.isEmpty()) add(action("Sign in with My Lists", "For ${Profiles.active().name}: replaces the default catalogs with your own lists, and syncs the Library. Takes 30 seconds with your phone.") { signIn() })
        else { add(info("Signed in", Pairing.userOf(Prefs.accountLink))); add(action("Sign out", "Goes back to the default catalogs. Your other addons stay.") { signOut() }) }
        add(action("Switch profile", "Now: ${Profiles.active().name}${if (Profiles.active().kids) " (kids)" else ""}. Each profile keeps its own account, addons, Library and history.") { profileMenu() })
        add(action("Android settings", "Wi-Fi, display, sound, Bluetooth, apps and the rest of the TV's own settings.") { androidSettings() })
        if (Profiles.all().size > 1) add(action("Share addons", "Now: ${Profiles.addonSource()?.let { s -> "using ${Profiles.all().first { it.id == s }.name}'s addons" } ?: "not shared"}. A profile can use another profile's addons, so a kids profile needs no account of its own. History and Library stay separate.") { shareMenu() })
        add(toggle("Advanced settings", "Shows more options in each group.", { Prefs.advanced }, { Prefs.advanced = it }))
        add(header("Playback"))
        add(toggle("Auto-pick the best source", "Play starts at once. Off shows the source list first.", { Prefs.autoPick }, { Prefs.autoPick = it }))
        add(choice("Maximum resolution", "Auto keeps weak boxes at 1080p.", listOf(0, 2160, 1080, 720), { Prefs.maxResolution }, { Prefs.maxResolution = it }, { if (it == 0) "Auto" else "${it}p" }))
        add(choice("Subtitle language", "Used when a file or a subtitle addon has it.", listOf("en", "es", "fr", "de", "off"), { Prefs.subtitleLang }, { Prefs.subtitleLang = it }, { if (it == "off") "Off" else it.uppercase() }))
        add(toggle("Play next episode automatically", "When an episode ends, the next one starts by itself with the same kind of source.", { Prefs.autoplayNext }, { Prefs.autoplayNext = it }))
        add(choice("Sources to try", "If a source will not play, how many others to try before giving up.", listOf(1, 2, 3, 4, 5, 6, 8, 10), { Prefs.fallbackTries }, { Prefs.fallbackTries = it }))
        add(toggle("Show Skip intro", "A button appears during intros and recaps. Times come from IntroDB.", { Prefs.skipIntro }, { Prefs.skipIntro = it }))
        if (Prefs.advanced) {
            add(toggle("Try the next source if one fails", "Switches by itself when a source does not start.", { Prefs.fallbackOn }, { Prefs.fallbackOn = it }))
            add(choice("Source start timeout", "How long to wait for the first picture.", listOf(6, 10, 15), { Prefs.startupTimeoutSec }, { Prefs.startupTimeoutSec = it }, { "$it s" }))
        }
        add(header("Layout"))
        add(toggle("Show cast, logos and episodes from Cinemeta", "Asks Cinemeta, the standard Stremio metadata addon, for details your catalog addon lacks.", { Prefs.extraDetails }, { Prefs.extraDetails = it }))
        add(toggle("Wide posters", "Show catalog items as wide pictures instead of tall posters.", { Prefs.horizontalPosters }, { Prefs.horizontalPosters = it }))
        add(choice("Navigation", "Where Home, Movies, Series and the other pages are listed. Hidden opens with Left.", listOf(0, 1, 2), { Prefs.navStyle }, { Prefs.navStyle = it }, { listOf("Top bar", "Side bar", "Hidden side bar")[it] }))
        add(choice("Card size", "Posters, wide cards and app tiles grow or shrink together.", listOf(0, 1, 2), { Prefs.cardSize }, { Prefs.cardSize = it }, { listOf("Small", "Medium", "Large")[it] }))
        add(action("Arrange Home rows", "Move lists and the apps row up or down, or hide them from Home.") { arrangeRows() })
        add(action("Arrange apps row", "Reorder the apps on Home or hide some. The Apps tab still shows all of them.") { arrangeApps() })
        add(toggle("Show Continue watching", "", { Prefs.showContinue }, { Prefs.showContinue = it }))
        add(toggle("Show apps row", "", { Prefs.showAppsRow }, { Prefs.showAppsRow = it }))
        add(header("Backup"))
        add(action("Back up my setup", "An offline copy of ${Profiles.active().name}'s addons and Library, encrypted with a recovery code (the first profile also saves the profile list and settings). Signed in, these also follow the account. Last backup: ${lastBackupText()}.") { backupNow() })
        add(action("Restore a backup", "Use your recovery code. You can also paste it on the phone page in Settings, then Addons.") { restoreDialog() })
        if (Prefs.backupCode.isNotEmpty()) add(action("Show recovery code", "The code that opens your backup.") { showCode(Prefs.backupCode) })
        add(header("Performance"))
        add(choice("Lite mode", "Less motion and memory. Auto turns on for weak boxes.", listOf("Auto", "On", "Off"), { Prefs.liteMode }, { Prefs.liteMode = it }))
        add(action("Clear caches", "Frees stored images and catalog data (${Http.cacheBytes() / 1_048_576} MB now).") { Http.clear(); ImageLoader.clearDisk(); Toast.makeText(this@SettingsActivity, "Caches cleared", Toast.LENGTH_SHORT).show() })
        add(header("Remote buttons"))
        add(info("Button mapper", if (HomeSetup.fallbackOn(this@SettingsActivity)) "On" else "Needs the Home button fallback (Home screen group)"))
        ButtonMap.rules().forEach { r -> add(action("${ButtonMap.keyName(r.key)}: ${ButtonMap.pressName(r.press)}", "${ButtonMap.actionName(this@SettingsActivity, r.action)}. OK removes it.") {
            android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Remove this button setting?").setPositiveButton("Remove") { _, _ -> ButtonMap.save(ButtonMap.rules().filterNot { it == r }); refresh() }.setNegativeButton("Keep", null).show() }) }
        add(action("Add a button", "Give a remote button (colour keys, app keys) a job on a press, double press or hold. Home, Back, arrows, OK, volume and power cannot be changed.") { addButton() })
        add(header("Launcher setup"))
        add(info("Home app", if (HomeSetup.isHome(this@SettingsActivity)) "This launcher" else "Another launcher"))
        add(action("Make this my Home app", "Opens the system choice. Pick My Lists TV and choose Always.") { requestHome() })
        add(info("Home button fallback", if (HomeSetup.fallbackOn(this@SettingsActivity)) "On" else "Off"))
        add(action("Turn on the Home button fallback", "For TVs that keep their own launcher as Home. Opens the system accessibility page if needed. It only sees which app came forward.") {
            if (HomeSetup.enableFallback(this@SettingsActivity)) refresh()
            else try { Toast.makeText(this@SettingsActivity, "Turn on My Lists TV Home button", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
        })
        add(info("Notifications bell", if (Notices.allowed(this@SettingsActivity)) "On" else "Off"))
        add(action("Turn on notifications", "Shows your TV's notifications in the bell at the top right of Home. Opens the system Notification access page.") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { Toast.makeText(this@SettingsActivity, "This TV has no such setting. Use the phone or computer steps.", Toast.LENGTH_LONG).show() }
        })
        add(action("Screensaver", "Pick My Lists TV backdrops as the screensaver in Android's settings.") {
            try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) } catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } catch (_: Exception) { Toast.makeText(this@SettingsActivity, "This TV keeps its screensaver setting somewhere else.", Toast.LENGTH_LONG).show() } } })
        add(action("Set up from a phone or computer", "Shows an address with the exact steps and commands for TVs that block the buttons above.") { setupPage() })
        add(header("About"))
        add(action("Check for updates", "Version 0.1.${Updater.installedBuild(this@SettingsActivity)} installed.") { checkUpdate() })
        add(info("Make this your home screen", "adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity"))
        add(info("Privacy", "No analytics, no tracking. Signed in, your watch progress goes to your My Lists account. Skip-intro times from IntroDB (introdb.app)."))
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
        val c = this
        val root = Ui.hbox(c).apply { setBackgroundColor(Ui.BG); gravity = Gravity.TOP }
        rail = Ui.vbox(c).apply { setPadding(Ui.dp(c, 40), Ui.dp(c, 36), Ui.dp(c, 16), Ui.dp(c, 24)) }
        root.addView(rail, LinearLayout.LayoutParams(Ui.dp(c, 280), -1))
        col = Ui.vbox(c).apply { setPadding(Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 56), Ui.dp(c, 32)) }
        root.addView(ScrollView(c).apply { addView(col); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        refresh(focusRail = true)
    }

    /** Rebuilds the group list and the rows of the selected group. [keep] is the row to put focus back on. */
    private fun refresh(keep: Int = -1, focusRail: Boolean = false) {
        val all = rows()
        groups = buildList { var name = ""; var cur = ArrayList<Row>(); for (r in all) if (r.kind == 'H') { if (name.isNotEmpty()) add(name to cur); name = r.label; cur = ArrayList() } else cur.add(r); if (name.isNotEmpty()) add(name to cur) }
        cat = cat.coerceIn(0, groups.size - 1)
        if (rail.childCount != groups.size + 1) {
            rail.removeAllViews()
            rail.addView(Ui.text(this, "Settings", 24f, Ui.TEXT, true), Ui.wrap().apply { bottomMargin = Ui.dp(this@SettingsActivity, 16) })
            groups.forEachIndexed { i, (name, _) -> rail.addView(railItem(name, i), Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 4) }) }
        }
        for (i in groups.indices) rail.getChildAt(i + 1).isSelected = i == cat
        buildPanel(keep)
        if (focusRail) rail.post { rail.getChildAt(cat + 1)?.requestFocus() }
    }

    private fun railItem(name: String, i: Int): View = Ui.text(this, name, 16f, Ui.TEXT, true).apply {
        id = View.generateViewId()
        setPadding(Ui.dp(this@SettingsActivity, 18), Ui.dp(this@SettingsActivity, 11), Ui.dp(this@SettingsActivity, 18), Ui.dp(this@SettingsActivity, 11))
        background = Ui.pillBg(this@SettingsActivity, Ui.CLEAR); isFocusable = true; isClickable = true
        setOnFocusChangeListener { v, f -> (v as TextView).setTextColor(if (f) Ui.ON_FOCUS else Ui.TEXT); if (f && cat != i) { cat = i; for (k in groups.indices) rail.getChildAt(k + 1).isSelected = k == i; buildPanel(-1) } }
        setOnClickListener { cat = i; buildPanel(-1); col.getChildAt(1)?.requestFocus() }
    }

    private fun buildPanel(keep: Int) {
        col.removeAllViews()
        val (name, rs) = groups[cat]
        col.addView(Ui.text(this, name, 24f, Ui.TEXT, true))
        val railId = rail.getChildAt(cat + 1).id
        var firstFocusable: View? = null
        rs.forEachIndexed { i, r ->
            val v = view(r) { refresh(keep = i) }
            v.id = View.generateViewId()
            if (v.isFocusable) { v.nextFocusLeftId = railId; if (firstFocusable == null) firstFocusable = v }
            col.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        }
        rail.getChildAt(cat + 1).nextFocusRightId = firstFocusable?.id ?: View.NO_ID
        if (keep >= 0) col.post { col.getChildAt(keep + 1)?.requestFocus() }
    }

    private fun view(r: Row, changed: () -> Unit): View {
        val c = this
        val row = Ui.hbox(c).apply { setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12)); Ui.rowStyle(c, this, fill = if (r.kind == 'I') Ui.BG else Ui.CARD) }
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, r.label, 16f, Ui.TEXT, true, 1))
        if (r.desc.isNotEmpty()) mid.addView(Ui.text(c, r.desc, 12f, Ui.DIM, false, 2))
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        val gap = Ui.dp(c, 16)
        when (r.kind) {
            'T' -> row.addView(Toggle(c, r.on()), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'C' -> row.addView(Ui.text(c, "\u2039  ${r.value()}  \u203a", 15f, Ui.TEXT, true).apply { setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6)); background = Ui.pillBg(c, Ui.FILL, Ui.FILL) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'A' -> row.addView(Ui.text(c, "\u203a", 22f, Ui.DIM, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'I' -> { row.isFocusable = false; row.isClickable = false; (mid.getChildAt(0) as TextView).setTextColor(Ui.TEXT2)
                if (r.value().isNotEmpty()) row.addView(Ui.text(c, r.value(), 12f, Ui.DIM, false, 2).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(c, 320), -2).apply { leftMargin = gap }) }
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

    @Deprecated("Back goes from the rows to the groups, then leaves.")
    override fun onBackPressed() {
        if (col.hasFocus()) rail.getChildAt(cat + 1)?.requestFocus() else super.onBackPressed()
    }
}
