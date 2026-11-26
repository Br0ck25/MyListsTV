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
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Settings as a drawer (docs/DESIGN.md section 4): one rounded panel down the right edge over the dimmed screen. It opens on the list
 * of groups under small headings; OK opens a group's page, which slides in with a back arrow on top. Every on/off is a toggle, a
 * choice among a few values is a choice row (OK or Left/Right changes it), anything else is an action. Changes apply at once and
 * are stored in [Prefs]. Back (or Left) goes up one page and then leaves.
 */
class SettingsActivity : Activity() {
    /** kind: H header (starts a group), T toggle, C choice, A action, I info. [act] gets +1 or -1 for choices, anything else otherwise. */
    private class Row(val kind: Char, val label: String, val desc: String = "", val value: () -> String = { "" }, val on: () -> Boolean = { false }, val act: (Int) -> Unit = {}, val icon: Int = 0, val input: ((String) -> Unit)? = null, val image: (() -> android.graphics.Bitmap?)? = null)

    /** A card inside a group (kind S): its title, then the rows that follow it up to the next card. */
    private fun card(title: String) = Row('S', title)

    /** A group of the settings list: its name, the line under the page title, its icon, and a short summary shown at the right. */
    private fun header(t: String, subtitle: String = "", icon: Int = 0, value: () -> String = { "" }) = Row('H', t, subtitle, value = value, icon = icon)
    private class Group(val name: String, val subtitle: String, val icon: Int, val value: String, val rows: List<Row>)
    /** Text typed in the page itself (kind E): [value] is the hint, [input] gets the text, the button says [label]. */
    private fun field(label: String, hint: String, submit: (String) -> Unit) = Row('E', label, value = { hint }, input = submit)
    private fun toggle(label: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) = Row('T', label, desc, on = get, act = { set(!get()) })
    private fun <T> choice(label: String, desc: String, opts: List<T>, get: () -> T, set: (T) -> Unit, show: (T) -> String = { it.toString() }) =
        Row('C', label, desc, value = { show(get()) }, act = { d -> set(opts[(opts.indexOf(get()).coerceAtLeast(0) + d + opts.size) % opts.size]) })
    private fun action(label: String, desc: String = "", icon: Int = 0, value: () -> String = { "" }, run: () -> Unit) = Row('A', label, desc, value = value, act = { run() }, icon = icon)
    private fun info(label: String, value: String) = Row('I', label, value = { value })

    private lateinit var host: android.widget.FrameLayout
    /** The groups under their small headings on the first page. A group not listed here is not shown, so add a new one here. */
    private val sections = listOf(
        "You" to listOf("Account", "Profiles"),
        "Home" to listOf("Layout", "Content & Discovery"),
        "Watching" to listOf("Playback", "Remote & Buttons"),
        "This TV" to listOf("Inputs", "Android settings", "Default Launcher", "Advanced", "About"),
    )
    private var cat = 0
    /** True while a group's page shows; false while the list of groups does. */
    private var open = false
    private var groups: List<Group> = emptyList()
    /** The pages opened inside the open group (the Addons list, one addon), newest last. Back closes the newest. */
    private val subStack = ArrayList<String>()
    private var pendingRemove: String? = null
    private var addonField: EditText? = null
    private var phone: PhoneServer? = null
    private var showQr = false

    private fun openSub(id: String) { subStack.add(id); show(0, slide = true) }

    override fun onResume() { super.onResume(); if (::host.isInitialized) refresh() }

    private val pictures = PicturePicker(this, 41)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); pictures.result(requestCode, resultCode, data) }

    private fun wallpaperMenu() {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Choose a picture" to { pictures.pick { uri ->
            Http.background { val ok = Pictures.save(this, uri, Pictures.wallpaperFile(this), 1280, 720, png = false); Http.post { if (ok) Prefs.bump(); Pop.toast(this, if (ok) "Wallpaper set" else "That file is not a picture this TV can read.", !ok) } }
        } }
        items += "Send from my phone" to { PhonePicture.receive(this, "Send a wallpaper", Pictures.wallpaperFile(this), 1280, 720, false) { Prefs.bump(); Pop.toast(this, "Wallpaper set") } }
        if (Pictures.wallpaperFile(this).exists()) items += "Remove wallpaper" to { Pictures.wallpaperFile(this).delete(); Prefs.bump(); Pop.toast(this, "Wallpaper removed") }
        Sheet.menu(this, "Wallpaper", "Shown when no movie or show is highlighted.", items)
    }

    private fun testSources() {
        if (StreamResolver.providers("movie", "tt0816692").isEmpty()) { Sheet.message(this, "Test my sources", "No stream addon is installed. Add one under Content & Discovery, Addons.", Sheet.Act("OK", true)); return }
        Pop.toast(this, "Testing\u2026")
        StreamResolver.resolve("movie", "tt0816692", StreamResolver.DEFAULT_TIMEOUT_MS, StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { _, st ->
            val lines = st.joinToString("\n\n") { (if (it.ok && it.count > 0) "\u2713 " else "\u2717 ") + it.name + ": " + (if (it.ok && it.count > 0) "${it.count} sources in ${"%.1f".format(it.millis / 1000.0)} s" else (it.note ?: "gave nothing")) }
            Sheet.message(this, "Test my sources", lines.ifEmpty { "No answers." }, Sheet.Act("OK", true))
        }))
    }

    /** Turns the Home button fallback on, or opens the system accessibility page when this app may not do it itself. */
    private fun turnOnFallback() {
        if (HomeSetup.enableFallback(this)) refresh()
        else try { Pop.toast(this, "Turn on My Lists TV Home button", true); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
    }

    private fun askName(kids: Boolean) = Sheet.ask(this, if (kids) "New kids profile" else "New profile", "Name", "Add") { Profiles.add(it, kids); refresh() }

    private fun arrange(what: String) = startActivity(Intent(this, ArrangeActivity::class.java).putExtra("what", what))

    private fun backupNow() {
        val first = Prefs.backupCode.isEmpty()
        val code = Backup.codeForThisBox()
        Pop.toast(this, "Backing up\u2026")
        Backup.upload(code) { msg ->
            if (first) showCode(code, msg) else Pop.toast(this, msg, true)
            refresh()
        }
    }

    private fun showCode(code: String, extra: String = "") = Sheet.message(this, "Your recovery code",
        "$code\n\nWrite this down. It is the only way to restore your setup on another box, and My Lists cannot recover it for you.${if (extra.isNotEmpty()) "\n\n$extra" else ""}",
        Sheet.Act("I wrote it down", true))

    private fun restoreDialog() = Sheet.ask(this, "Restore a backup", "XXXX-XXXX-XXXX-XXXX", "Restore", "Enter the recovery code from your backup. This replaces the addons and Library of the profile in use.", caps = true) { raw ->
        val code = Backup.clean(raw)
        if (!Backup.isCode(code)) Pop.toast(this, "That does not look like a recovery code.", true)
        else { Pop.toast(this, "Restoring\u2026"); Backup.restore(this, code) { msg -> Pop.toast(this, msg, true); refresh() } }
    }

    private fun lastBackupText() = Prefs.lastBackup.let { if (it == 0L) "Never" else android.text.format.DateFormat.format("MMM d, h:mm a", it).toString() }

    private fun signIn() {
        Pop.toast(this, "Getting a code\u2026")
        Pairing.start { s, why ->
            if (s == null) { Sheet.message(this, "Could not sign in", why ?: "Could not reach My Lists.", Sheet.Act("OK", true)); return@start }
            // Scan the code with a phone camera, or open the link by hand.
            val sheet = Sheet(this, "Sign in with My Lists", "Scan this with your phone's camera, sign in and tap Link. Or open ${Pairing.linkUrl(s)} and enter the code ${s.userCode}.", 520).cancelable(false)
            sheet.image(Qr.bitmap(Pairing.linkFull(s), Ui.dp(this, 190)))
            val cancel = sheet.card("Cancel") { Pairing.cancel(); sheet.dismiss() }
            sheet.show(); cancel.requestFocus()
            Pairing.poll(s) { link, name ->
                sheet.dismiss()
                if (link == null) Pop.toast(this, "The code expired. Try again.", true)
                else { Pairing.signIn(link, name); Pop.toast(this, "Signed in as ${name.ifEmpty { "your account" }}. Your lists replace the defaults.", true) }
                refresh()
            }
        }
    }

    private fun signOut() = Sheet.confirm(this, "Sign out of My Lists?", "Home goes back to the default catalogs. Your other addons stay.", "Sign out", "Keep signed in") { Pairing.signOut(); refresh() }

    private fun rows(): List<Row> = buildList {
        val me = this@SettingsActivity
        add(header("Account", "Account and sync status", R.drawable.ic_person) { if (Prefs.accountLink.isEmpty()) "Signed out" else Prefs.accountName.ifEmpty { "Signed in" } })
        add(card(""))
        if (Prefs.accountLink.isEmpty()) add(action("Sign in with My Lists", "For ${Profiles.active().name}: replaces the default catalogs with your own lists, and syncs the Library. Takes 30 seconds with your phone.") { signIn() })
        else { add(info("Signed in as", Prefs.accountName.ifEmpty { "your account" })); add(action("Sign out", "Goes back to the default catalogs. Your other addons stay.") { signOut() }) }
        add(card("Backup"))
        add(action("Back up my setup", "An offline copy of ${Profiles.active().name}'s addons and Library, encrypted with a recovery code (the first profile also saves the profile list and settings). Signed in, these also follow the account. Last backup: ${lastBackupText()}.") { backupNow() })
        add(action("Restore a backup", "Use your recovery code. You can also paste it on the phone page in Settings, then Addons.") { restoreDialog() })
        if (Prefs.backupCode.isNotEmpty()) add(action("Show recovery code", "The code that opens your backup.") { showCode(Prefs.backupCode) })
        add(header("Profiles", "Who is watching. Each profile keeps its own account, addons, Library and history.", R.drawable.ic_people) { Profiles.active().name })
        addAll(profileRows())
        add(header("Layout", "How Home looks and which rows it shows", R.drawable.ic_layout))
        add(card("Look"))
        add(toggle("Show cast, logos and episodes from Cinemeta", "Asks Cinemeta, the standard Stremio metadata addon, for details your catalog addon lacks.", { Prefs.extraDetails }, { Prefs.extraDetails = it }))
        add(toggle("Landscape posters", "Show catalog items as landscape pictures instead of tall posters.", { Prefs.horizontalPosters }, { Prefs.horizontalPosters = it }))
        add(choice("Navigation", "Where Home, Movies, Series and the other pages are listed. Hidden opens with Left.", listOf(0, 1, 2), { Prefs.navStyle }, { Prefs.navStyle = it }, { listOf("Top bar", "Side bar", "Hidden side bar")[it] }))
        add(choice("Highlight colour", "The ring around what is selected, and toggles, tabs and main buttons.", Ui.ACCENTS.indices.toList(), { Prefs.accent }, { Prefs.accent = it }, { Ui.ACCENTS[it].name }))
        add(choice("Card size", "Posters, wide cards and app tiles grow or shrink together.", listOf(-1, 0, 1, 2, 3), { Prefs.cardSize }, { Prefs.cardSize = it }, { listOf("Extra small", "Small", "Medium", "Large", "Extra large")[it + 1] }))
        IconPacks.installed(this@SettingsActivity).let { packs ->
            add(choice("Icon pack", if (packs.isEmpty()) "Install an icon pack (Nova, ADW or Apex format) from the Play Store, then pick it here." else "Changes the icon of every app it covers. Hold OK on an app to change just that one.", listOf("") + packs.map { it.pkg }, { Prefs.iconPack }, { Prefs.iconPack = it }, { p -> packs.firstOrNull { it.pkg == p }?.label ?: "Default" }))
        }
        add(action("Wallpaper", "Your own picture behind Home when no title is highlighted (apps, the Apps page). Pick one from the TV's files.") { wallpaperMenu() })
        add(card("Home rows"))
        add(action("Arrange Home rows", "Move lists and the apps row up or down, or hide them from Home.") { arrange("rows") })
        add(action("Arrange apps row", "Reorder the apps on Home or hide some. The Apps tab still shows all of them.") { arrange("apps") })
        add(toggle("Show Airing Next", "The next episode of the shows you follow on My Lists, soonest first.", { Prefs.showAiring }, { Prefs.showAiring = it }))
        if (Prefs.showAiring) add(toggle("Hide upcoming from Continue Watching", "An episode that is in Airing Next is not also shown in Continue Watching.", { Prefs.hideUpcomingContinue }, { Prefs.hideUpcomingContinue = it }))
        add(toggle("Show Continue Watching", "", { Prefs.showContinue }, { Prefs.showContinue = it }))
        add(toggle("Show apps row", "", { Prefs.showAppsRow }, { Prefs.showAppsRow = it }))
        add(header("Content & Discovery", "Addons, catalogs, and discovery sources", R.drawable.ic_compass) { AddonStore.saved().size.let { "$it addon" + if (it == 1) "" else "s" } })
        add(card(""))
        add(action("Addons", "Manage addons, catalog order, and what each one provides", icon = R.drawable.ic_puzzle) { openSub("addons") })
        add(action("Test my sources", "Asks every stream addon for a well-known movie and says which answered, how fast, and why not if one did not.") { testSources() })
        add(header("Playback", "How video starts and plays", R.drawable.ic_play))
        add(card("Starting playback"))
        add(toggle("Auto-pick the best source", "Play starts at once. Off shows the source list first.", { Prefs.autoPick }, { Prefs.autoPick = it }))
        add(choice("Maximum resolution", "Auto keeps weak boxes at 1080p.", listOf(0, 2160, 1080, 720), { Prefs.maxResolution }, { Prefs.maxResolution = it }, { if (it == 0) "Auto" else "${it}p" }))
        add(card("Subtitles"))
        add(choice("Subtitle language", "Used when a file or a subtitle addon has it.", listOf("en", "es", "fr", "de", "off"), { Prefs.subtitleLang }, { Prefs.subtitleLang = it }, { if (it == "off") "Off" else it.uppercase() }))
        add(card("While watching"))
        add(toggle("Play next episode automatically", "When an episode ends, the next one starts by itself with the same kind of source.", { Prefs.autoplayNext }, { Prefs.autoplayNext = it }))
        add(choice("Sources to try", "If a source will not play, how many others to try before giving up.", listOf(1, 2, 3, 4, 5, 6, 8, 10), { Prefs.fallbackTries }, { Prefs.fallbackTries = it }))
        add(toggle("Show Skip intro", "A button appears during intros and recaps. Times come from IntroDB.", { Prefs.skipIntro }, { Prefs.skipIntro = it }))
        if (Prefs.advanced) {
            add(toggle("Try the next source if one fails", "Switches by itself when a source does not start.", { Prefs.fallbackOn }, { Prefs.fallbackOn = it }))
            add(choice("Source start timeout", "How long to wait for the first picture.", listOf(6, 10, 15), { Prefs.startupTimeoutSec }, { Prefs.startupTimeoutSec = it }, { "$it s" }))
        }
        add(header("Default Launcher", "Make My Lists TV your Home screen, and set up the Home button", R.drawable.ic_home))
        add(card("Home app"))
        add(info("Home app", if (HomeSetup.isHome(this@SettingsActivity)) "This launcher" else "Another launcher"))
        add(action("Make this my Home app", "Opens the system choice. Pick My Lists TV and choose Always.") { if (HomeSetup.isHome(this@SettingsActivity)) Pop.toast(this@SettingsActivity, "My Lists TV is already your Home app.") else HomeSetup.requestHome(this@SettingsActivity) })
        add(card("Home button"))
        add(info("Home button fallback", if (HomeSetup.fallbackOn(this@SettingsActivity)) "On" else "Off"))
        add(action("Turn on the Home button fallback", "For TVs that keep their own launcher as Home. Opens the system accessibility page if needed. It only sees which app came forward.") {
            if (HomeSetup.fallbackOn(this@SettingsActivity)) Pop.toast(this@SettingsActivity, "The Home button fallback is already on.")
            else if (HomeSetup.enableFallback(this@SettingsActivity)) refresh()
            else try { Pop.toast(this@SettingsActivity, "Turn on My Lists TV Home button", true); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
        })
        add(card("Notifications"))
        add(info("Notifications bell", if (Notices.allowed(this@SettingsActivity)) "On" else "Off"))
        add(action("Turn on notifications", "Shows your TV's notifications in the bell at the top right of Home. Opens the system Notification access page.") {
            if (Notices.allowed(this@SettingsActivity)) Pop.toast(this@SettingsActivity, "Notifications are already on.")
            else try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { Pop.toast(this@SettingsActivity, "This TV has no such setting. Use the phone or computer steps.", true) }
        })
        add(card("More"))
        add(action("Screensaver", "Pick My Lists TV backdrops as the screensaver in Android's settings.") {
            try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) } catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } catch (_: Exception) { Pop.toast(this@SettingsActivity, "This TV keeps its screensaver setting somewhere else.", true) } } })
        add(action("Set up from a phone or computer", "Shows an address with the exact steps and commands for TVs that block the buttons above.") { HomeSetup.phoneSteps(this@SettingsActivity) })
        add(header("Remote & Buttons", "Customize remote buttons, Home actions, and app shortcuts in My Lists TV and globally.", R.drawable.ic_remote))
        add(card("Button Mapper"))
        add(toggle("Enable Button Mapper", "Intercept and run custom actions for configured remote buttons.", { Prefs.mapperOn }, { Prefs.mapperOn = it }))
        add(toggle("Customize Home Button", "When disabled, Home uses factory defaults (Single: Go to Top of Home, Double: Profile Switcher).", { Prefs.homeCustom }, { Prefs.homeCustom = it }))
        val globalOn = HomeSetup.fallbackOn(this@SettingsActivity)
        add(action(if (globalOn) "Global button interception active (Accessibility)" else "Global button interception inactive (Accessibility)",
            if (globalOn) "Configured global button mappings work both inside My Lists TV and while other apps are open when Android exposes the key."
            else "While the Home button fallback is off, button mappings work inside My Lists TV. Select to turn it on for global support.",
            value = { if (globalOn) "Active" else "Configure" }) { if (!globalOn) turnOnFallback() })
        add(action("Add Button Mapping", "Press a remote button to detect it and assign single, double, or long-press actions.", icon = R.drawable.ic_add) { MapperDialogs.add(this@SettingsActivity) { refresh() } })
        add(card("Configured Remote Buttons"))
        (listOf(ButtonMap.HOME) + ButtonMap.rules().map { it.key }.distinct().filter { it != ButtonMap.HOME }).forEach { key ->
            val me = this@SettingsActivity
            add(action(ButtonMap.keyName(key), "Single: ${ButtonMap.pressLabel(me, key, "single")} \u2022 Double: ${ButtonMap.pressLabel(me, key, "double")} \u2022 Long: ${ButtonMap.pressLabel(me, key, "long")}",
                icon = MapperDialogs.iconFor(key), value = { ButtonMap.badge(me, key) }) { MapperDialogs.edit(me, key) { refresh() } })
        }
        add(card("Maintenance"))
        if (Prefs.homeCustom || ButtonMap.rules().any { it.key == ButtonMap.HOME }) add(action("Restore Home Defaults", "Reset Home to Single: Go to Top of Home, Double: Profile Switcher, Long: Not Assigned.", icon = R.drawable.ic_restore) {
            ButtonMap.removeKey(ButtonMap.HOME); Prefs.homeCustom = false; refresh() })
        add(action("Reset to Defaults", "Restore default Home button behavior and remove custom button mappings.", icon = R.drawable.ic_restore) {
            MapperDialogs.confirm(this@SettingsActivity, "Reset Button Mapper?", "Home goes back to its factory jobs and every custom button mapping is removed.", "Reset to Defaults") { ButtonMap.clearAll(); Prefs.homeCustom = false; refresh() } })
        add(header("Advanced", "Performance, cache, and the TV's own settings", R.drawable.ic_wrench))
        add(card(""))
        add(toggle("Advanced settings", "Shows more options in each group.", { Prefs.advanced }, { Prefs.advanced = it }))
        add(card("Performance"))
        add(choice("Lite mode", "Less motion and memory. Auto turns on for weak boxes.", listOf("Auto", "On", "Off"), { Prefs.liteMode }, { Prefs.liteMode = it }))
        add(action("Clear caches", "Frees stored images and catalog data (${Http.cacheBytes() / 1_048_576} MB now).") { Http.clear(); ImageLoader.clearDisk(); Pop.toast(this@SettingsActivity, "Caches cleared") })
        add(header("Inputs", "HDMI and the TV's other video inputs", R.drawable.ic_tv))
        add(card(""))
        val inputs = SystemLinks.inputs(me)
        if (inputs.isEmpty()) add(info("No inputs listed", "This TV does not list any inputs to apps. Use its own Input or Source button."))
        inputs.forEach { i -> add(action(i.label, icon = R.drawable.ic_tv) { SystemLinks.openInput(me, i.id) }) }
        add(header("Android settings", "Wi-Fi, display, sound, Bluetooth and the rest of the TV's own settings", R.drawable.ic_settings))
        add(card(""))
        SystemLinks.pages.forEach { (name, page) -> add(action(name, icon = R.drawable.ic_settings) { SystemLinks.open(me, page) }) }
        add(header("About", "Version, updates, and privacy", R.drawable.ic_info) { Version.name(Updater.installedBuild(me)) })
        add(card(""))
        add(choice("Update channel", "Production: releases that have been tested and promoted. Beta: every new build, sooner but it may have bugs.", listOf("production", "beta"), { Prefs.updateChannel }, { Prefs.updateChannel = it }, { if (it == "beta") "Beta" else "Production" }))
        add(action("Check for updates", "Version ${Version.name(Updater.installedBuild(this@SettingsActivity))} installed. Checking the ${if (Prefs.updateChannel == "beta") "beta" else "production"} channel.") { checkUpdate() })
        add(info("Make this your home screen", "adb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity"))
        add(info("Privacy", "No analytics, no tracking. Signed in, your watch progress goes to your My Lists account. Skip-intro times from IntroDB (introdb.app)."))
    }

    // ---------------------------------------------------------------- Pages inside a group (no pop-ups)

    private fun subPage(): Triple<String, String, List<Row>>? {
        val id = subStack.lastOrNull() ?: return null
        return when {
            id == "addons" -> Triple("Addons", "Manage addons, catalog order, and what each one provides", addonsRows())
            id.startsWith("addon:") -> { val url = id.removePrefix("addon:"); Triple(addonName(url), url.removePrefix("https://").removePrefix("http://").substringBefore('/'), addonRows(url)) }
            else -> null
        }
    }

    /** Manage from phone: a code to scan, with its address, and a way to close. */
    private fun showPhonePopup(addr: String) {
        val s = Sheet(this, "Add addons from your phone", "Scan with your phone, then paste the addon's link (or a recovery code) on the page that opens.", 480)
        s.image(Qr.bitmap(addr, Ui.dp(this, 190)), addr)
        val close = s.card("Close") { s.dismiss() }
        s.show(); close.requestFocus()
    }

    private fun addonName(url: String) = AddonStore.loaded.firstOrNull { it.manifestUrl == url }?.name ?: url.removePrefix("https://").removePrefix("http://").substringBefore('/')

    private fun provides(a: Addon): String {
        val p = ArrayList<String>()
        if (a.manifest.catalogs.any { !it.searchRequired }) p += "Catalogs"
        if (a.manifest.catalogs.any { it.supportsSearch }) p += "Search"
        if (a.manifest.provides("meta")) p += "Details"
        if (a.manifest.provides("stream")) p += "Streams"
        if (a.manifest.provides("subtitles")) p += "Subtitles"
        return p.joinToString(", ").ifEmpty { "Nothing we use" }
    }

    /** Reloads the addons, tells Home to reload, and redraws this page. */
    private fun addonsChanged() = AddonStore.load { AddonStore.needsRefresh = true; runOnUiThread { refresh() } }

    private fun installAddon(link: String) {
        if (link.isBlank()) { Pop.toast(this, "Type or paste an addon link first."); return }
        Pop.toast(this, "Checking…")
        Links.add(link.trim()) { msg -> runOnUiThread { Pop.toast(this, msg, true); addonField?.setText(""); addonsChanged() } }
    }

    private fun addonsRows(): List<Row> = buildList {
        val me = this@SettingsActivity
        if (AddonStore.sharing()) {
            add(card(""))
            add(info("Shared addons", "These addons are shared with ${Profiles.all().firstOrNull { it.id == Profiles.addonSource() }?.name ?: "another profile"}. Change them on that profile, or stop sharing under Profiles."))
        } else {
            val server = phone ?: PhoneServer { link, reply -> Links.add(link) { reply(it); addonsChanged() } }.also { it.start(); phone = it }
            val addr = server.address()
            add(card("Install addon"))
            add(field("Install", "https://example.com/manifest.json") { installAddon(it) })
            add(card(""))
            add(action("Manage from phone", if (addr != null) "Scan a QR code to add addons from your phone, and see the address" else "Connect the TV to Wi-Fi to add addons from your phone.", icon = R.drawable.ic_layout) { if (addr != null) showPhonePopup(addr) else Pop.toast(me, "Connect the TV to Wi-Fi first.") })
            add(action("Reorder home catalogs", "Controls catalog row order on Home", icon = R.drawable.ic_list) { arrange("rows") })
            add(action("Refresh Addons", "Pull the latest addon changes for the current profile", icon = R.drawable.ic_refresh) { Pop.toast(me, "Refreshing…"); addonsChanged() })
        }
        add(card("Installed addons"))
        val loaded = AddonStore.loaded.associateBy { it.manifestUrl }
        val saved = AddonStore.saved()
        if (saved.isEmpty()) add(info("No addons yet", "Install one above to get catalogs or streams."))
        saved.forEach { s ->
            val a = loaded[s.url]
            val host = s.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
            add(action((a?.name ?: "Not loading") + if (a != null && a.manifest.version.isNotEmpty()) "  v${a.manifest.version}" else "",
                "$host • ${a?.let { provides(it) } ?: "Could not read this addon right now"}", icon = R.drawable.ic_puzzle, value = { if (s.enabled) "On" else "Off" }) { openSub("addon:${s.url}") })
        }
    }

    private fun addonRows(url: String): List<Row> = buildList {
        val s = AddonStore.saved().firstOrNull { it.url == url }
        if (s == null) { add(card("")); add(info("Removed", "This addon is no longer installed. Press Back.")); return@buildList }
        val shared = AddonStore.sharing()
        add(card(""))
        add(info("Address", url))
        if (shared) { add(info("Shared", "Change this on the profile the addons come from.")); return@buildList }
        add(toggle("Enabled", "Off keeps the addon installed but stops using it.", { AddonStore.saved().firstOrNull { it.url == url }?.enabled ?: false }, { AddonStore.setEnabled(url, it); addonsChanged() }))
        add(action("Move up", "Earlier stream addons win ties.", icon = R.drawable.ic_back) { AddonStore.move(url, -1); addonsChanged() })
        add(action("Move down", "Later addons are tried after earlier ones.", icon = R.drawable.ic_back) { AddonStore.move(url, 1); addonsChanged() })
        if (pendingRemove == url) add(action("Confirm: remove ${addonName(url)}", "This cannot be undone. Your other settings stay as they are.", icon = R.drawable.ic_close) {
            AddonStore.remove(url); pendingRemove = null; subStack.removeAt(subStack.size - 1); addonsChanged(); show(0) })
        else add(action("Remove addon", "Uninstalls it from this profile.", icon = R.drawable.ic_close) { pendingRemove = url; refresh() })
    }

    private fun profileRows(): List<Row> = buildList {
        val me = this@SettingsActivity
        add(card(""))
        Profiles.all().forEach { p ->
            val active = p.id == Profiles.activeId
            add(action(p.name + if (p.kids) " (kids)" else "", if (active) "In use now" else "Switch to this profile", icon = R.drawable.ic_person, value = { if (active) "Active" else "" }) { if (!active) { Profiles.switchTo(me, p.id); refresh() } })
        }
        add(card("Manage"))
        Profiles.all().forEach { p -> add(action("Rename ${p.name}", "Change the name shown for this profile.", icon = R.drawable.ic_person) { Sheet.ask(me, "Rename ${p.name}", "Name", "Save", initial = p.name) { Profiles.rename(p.id, it); refresh() } }) }
        add(action("Add profile", "A separate account, addons, Library and history.", icon = R.drawable.ic_add) { askName(false) })
        add(action("Add kids profile", "A profile that hides titles that are not for children.", icon = R.drawable.ic_add) { askName(true) })
        if (Profiles.all().size > 1) {
            val remove = "profile:${Profiles.activeId}"
            if (pendingRemove == remove) add(action("Confirm: remove ${Profiles.active().name}", "This cannot be undone. Its addons, Library and history go with it.", icon = R.drawable.ic_close) {
                pendingRemove = null; Profiles.remove(Profiles.activeId); Profiles.switchTo(me, Profiles.activeId); refresh() })
            else add(action("Remove ${Profiles.active().name}", "Deletes this profile.", icon = R.drawable.ic_close) { pendingRemove = remove; refresh() })
            add(card("Share addons"))
            add(info("Now", Profiles.addonSource()?.let { s -> "Using ${Profiles.all().first { it.id == s }.name}'s addons" } ?: "Not shared. Each profile keeps its own addons."))
            Profiles.all().filter { it.id != Profiles.activeId && Profiles.addonSource(it.id) == null }.forEach { o ->
                add(action("Use ${o.name}'s addons", "This profile follows ${o.name}'s addon list.", icon = R.drawable.ic_people) { Profiles.shareAddons(Profiles.activeId, o.id); AddonStore.needsRefresh = true; refresh() })
            }
            if (Profiles.addonSource() != null) add(action("Stop sharing (keep a copy)", "This profile keeps its own copy of the addons.", icon = R.drawable.ic_close) { AddonStore.stopSharing(Profiles.activeId); AddonStore.needsRefresh = true; refresh() })
        }
    }

    private fun checkUpdate() {
        Pop.toast(this, "Checking for updates\u2026")
        Updater.latest { r ->
            val mine = Updater.installedBuild(this)
            if (r == null) Pop.toast(this, if (Prefs.updateChannel == "beta") "Could not check right now." else "No production release found yet, or could not check. Switch to Beta to see every build.", true)
            else if (r.build <= mine) Pop.toast(this, "You have the latest version.", true)
            else Sheet.message(this, "Update available", "${r.tag}${if (r.beta) " (beta)" else ""} is ready. You have ${Version.name(mine)}.", Sheet.Act("Later"), Sheet.Act("Install", true) { Updater.install(this, r) { Pop.toast(this, it) } })
        }
    }

    /** Content fades out at the top and bottom of its area, so it looks like it goes behind the panel instead of being cut off against its edge. */
    private fun fadeEdges(s: ScrollView) { s.isVerticalFadingEdgeEnabled = true; s.setFadingEdgeLength(Ui.dp(this, 30)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        // One rounded panel down the right edge, the same as the bell's, over the dimmed screen. [host] holds the page that shows.
        host = android.widget.FrameLayout(c)
        val panel = android.widget.FrameLayout(c).apply { background = Ui.rounded(c, Ui.CARD, Ui.R_DIALOG); clipToOutline = true; addView(host, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        val m = Ui.dp(c, SidePanel.MARGIN)
        val root = android.widget.FrameLayout(c).apply { setBackgroundColor(Ui.SCRIM_SLATE); setPadding(0, m, m, m) }
        root.addView(panel, android.widget.FrameLayout.LayoutParams(Ui.dp(c, SidePanel.WIDTH), -1, Gravity.END))
        setContentView(root)
        refresh()
        if (intent.getStringExtra("open") == "addons") { cat = groups.indexOfFirst { it.name == "Content & Discovery" }.coerceAtLeast(0); open = true; openSub("addons") }
    }

    /** Rebuilds the groups and the page that shows, and puts focus back on the row that had it. [keep] is the row to focus instead. */
    private fun refresh(keep: Int = -1) {
        val focused = host.findFocus()?.let { f -> var v: View? = f; while (v != null && v.tag !is Int) v = v.parent as? View; (v?.tag as? Int) ?: -1 } ?: -1
        val all = rows()
        groups = buildList {
            var h: Row? = null; var cur = ArrayList<Row>()
            for (r in all) if (r.kind == 'H') { h?.let { add(Group(it.label, it.desc, it.icon, it.value(), cur)) }; h = r; cur = ArrayList() } else cur.add(r)
            h?.let { add(Group(it.label, it.desc, it.icon, it.value(), cur)) }
        }
        cat = cat.coerceIn(0, groups.size - 1)
        show(if (keep >= 0) keep else focused)
    }

    /** Puts the first page or the open group's page in the panel, with focus on the row tagged [focusRow] (else the first row). A page slides in from the side unless the box is in lite mode. */
    private fun show(focusRow: Int, slide: Boolean = false, forward: Boolean = true) {
        val page = if (open) groupPage() else mainPage()
        host.removeAllViews(); host.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        if (slide && !Prefs.lite()) { page.translationX = Ui.dp(this, if (forward) 48 else -48).toFloat(); page.alpha = 0f; page.animate().translationX(0f).alpha(1f).setDuration(160).start() }
        page.post { val rs = rowViews(page).filter { canFocus(it) }; (rs.firstOrNull { it.tag == focusRow } ?: rs.firstOrNull())?.requestFocus() }
    }

    /** The views a page tags with their row number (a group's number on the first page), in order. The back row has no number. */
    private fun rowViews(v: View, out: ArrayList<View> = ArrayList()): ArrayList<View> {
        if (((v.tag as? Int) ?: -1) >= 0) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) rowViews(v.getChildAt(i), out)
        return out
    }
    private fun canFocus(v: View) = v.isFocusable || v is ViewGroup && (0 until v.childCount).any { v.getChildAt(it).isFocusable }

    private fun scrolling(v: View) = ScrollView(this).apply { addView(v); isVerticalScrollBarEnabled = false; clipToPadding = false; fadeEdges(this) }

    /** The groups under their small headings. */
    private fun mainPage(): View {
        val me = this
        val list = Ui.vbox(me).apply { setPadding(Ui.dp(me, 14), Ui.dp(me, 8), Ui.dp(me, 14), Ui.dp(me, 16)) }
        sections.forEach { (name, members) ->
            val shown = members.map { m -> groups.indexOfFirst { it.name == m } }.filter { it >= 0 }
            if (shown.isEmpty()) return@forEach
            list.addView(Ui.sectionLabel(me, name), Ui.matchWrap().apply { topMargin = Ui.dp(me, 16); leftMargin = Ui.dp(me, 14); bottomMargin = Ui.dp(me, 4) })
            shown.forEach { i -> list.addView(groupRow(groups[i], i), Ui.matchWrap().apply { topMargin = Ui.dp(me, 2) }) }
        }
        return scrolling(list)
    }

    private fun groupRow(g: Group, i: Int): View = Ui.hbox(this, 14).apply {
        val me = this@SettingsActivity
        tag = i; isFocusable = true; isClickable = true; background = Ui.menuRowBg(me)
        setPadding(Ui.dp(me, 12), Ui.dp(me, 10), Ui.dp(me, 14), Ui.dp(me, 10))
        addView(Ui.iconTile(me, g.icon))
        addView(Ui.text(me, g.name, 16f, Ui.TEXT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
        if (g.value.isNotEmpty()) addView(Ui.text(me, g.value, 14f, Ui.DIM, false, 1))
        addView(Ui.text(me, "\u203a", 22f, Ui.DIM, true))
        setOnClickListener { cat = i; open = true; closeSubs(); show(0, slide = true) }
    }

    /** A group's page (or the Addons page inside it): a back arrow with the title, the line under it, then the rows. */
    private fun groupPage(): View {
        val me = this
        val g = groups[cat]; val sub = subPage()
        val title = sub?.first ?: g.name
        val subtitle = sub?.second ?: g.subtitle
        val rs = sub?.third ?: g.rows
        val page = Ui.vbox(me)
        val back = Ui.hbox(me, 12).apply {
            tag = -1; isFocusable = true; isClickable = true; background = Ui.menuRowBg(me)
            setPadding(Ui.dp(me, 12), Ui.dp(me, 8), Ui.dp(me, 14), Ui.dp(me, 8))
            addView(Ui.iconTile(me, R.drawable.ic_back, 32))
            addView(Ui.text(me, title, 24f, Ui.TEXT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { goBack() }
        }
        page.addView(back, Ui.matchWrap().apply { setMargins(Ui.dp(me, 14), Ui.dp(me, 14), Ui.dp(me, 14), 0) })
        if (subtitle.isNotEmpty()) page.addView(Ui.text(me, subtitle, 14f, Ui.DIM).apply { setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { topMargin = Ui.dp(me, 6); leftMargin = Ui.dp(me, 28); rightMargin = Ui.dp(me, 28) })
        val list = Ui.vbox(me).apply { setPadding(Ui.dp(me, 14), Ui.dp(me, 4), Ui.dp(me, 14), Ui.dp(me, 16)) }
        rs.forEachIndexed { i, r ->
            if (r.kind == 'S') {
                // A heading over the rows that follow it, up to the next heading.
                if (r.label.isNotEmpty()) list.addView(Ui.sectionLabel(me, r.label), Ui.matchWrap().apply { topMargin = Ui.dp(me, 16); leftMargin = Ui.dp(me, 14); bottomMargin = Ui.dp(me, 2) })
                else list.addView(View(me), Ui.matchWrap().apply { height = Ui.dp(me, 6) })
                return@forEachIndexed
            }
            val v = view(r) { refresh(keep = i) }
            v.tag = i
            list.addView(v, Ui.matchWrap().apply { topMargin = Ui.dp(me, 2) })
        }
        page.addView(scrolling(list), LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun closeSubs() { subStack.clear(); pendingRemove = null; phone?.stop(); phone = null; showQr = false }

    /** Up one page: out of an addon, out of the Addons list, out of the group, then out of Settings. */
    private fun goBack() {
        when {
            subStack.isNotEmpty() -> { subStack.removeAt(subStack.size - 1); pendingRemove = null; if (subStack.isEmpty()) { phone?.stop(); phone = null; showQr = false }; show(0, slide = true, forward = false) }
            open -> { open = false; closeSubs(); show(cat, slide = true, forward = false) }
            else -> finish()
        }
    }

    private fun view(r: Row, changed: () -> Unit): View {
        val c = this
        if (r.kind == 'E') {
            // The link field with its button beside it: the field takes the width, the button sits at the right.
            val field = EditText(c).apply {
                hint = r.value(); setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); setSingleLine(true); Ui.size(this, 16f)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
                background = Ui.rounded(c, Ui.BG, Ui.R_PANEL, Ui.FILL_STRONG); foreground = Ui.focusBg(c, Ui.R_PANEL, Ui.CLEAR)
                addonField = this
                setOnEditorActionListener { _, id, _ -> if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { r.input?.invoke(text.toString()); true } else false }
            }
            val go = Ui.text(c, r.label, 16f, Ui.TEXT, true).apply {
                gravity = Gravity.CENTER; setPadding(Ui.dp(c, 22), Ui.dp(c, 12), Ui.dp(c, 22), Ui.dp(c, 12))
                background = Ui.pillBg(c, Ui.FILL, Ui.FOCUS_FILL); isFocusable = true; isClickable = true
                setOnClickListener { r.input?.invoke(field.text.toString()) }
            }
            return Ui.hbox(c, 10).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(field, LinearLayout.LayoutParams(0, -2, 1f)); addView(go, LinearLayout.LayoutParams(-2, -2))
            }
        }
        if (r.kind == 'Q') return Ui.hbox(c, 16).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4))
            r.image?.invoke()?.let { addView(android.widget.ImageView(c).apply { setImageBitmap(it) }, LinearLayout.LayoutParams(Ui.dp(c, 150), Ui.dp(c, 150))) }
            addView(Ui.text(c, r.desc, 14f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val row = Ui.hbox(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); if (r.kind != 'I') background = Ui.menuRowBg(c) }
        if (r.icon != 0) row.addView(Ui.iconTile(c, r.icon, 32), LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)).apply { rightMargin = Ui.dp(c, 14) })
        val mid = Ui.vbox(c)
        // A row of the mapper may need its whole sentence; the others stay short.
        val long = r.icon != 0 || r.kind == 'T' || r.kind == 'A' && r.value().isNotEmpty()
        mid.addView(Ui.text(c, r.label, 16f, Ui.TEXT, true, if (long) 0 else 1))
        if (r.desc.isNotEmpty()) mid.addView(Ui.text(c, r.desc, 12f, Ui.DIM, false, if (long) 0 else 2), Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        val gap = Ui.dp(c, 16)
        when (r.kind) {
            'T' -> row.addView(Toggle(c, r.on()), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'C' -> row.addView(Ui.text(c, "\u2039  ${r.value()}  \u203a", 15f, Ui.TEXT, true).apply { setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6)); background = Ui.pillBg(c, Ui.FILL, Ui.FILL) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            'A' -> {
                if (r.value().isNotEmpty()) row.addView(Ui.text(c, r.value(), 14f, Ui.TEXT2), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
                row.addView(Ui.text(c, "\u203a", 22f, Ui.DIM, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap })
            }
            'I' -> { row.isFocusable = false; row.isClickable = false; (mid.getChildAt(0) as TextView).setTextColor(Ui.TEXT2)
                val v = r.value()
                // A long text goes under its title, as wide as the row and in full; a short value sits at the right.
                if (v.length > 30) mid.addView(Ui.text(c, v, 13f, Ui.DIM).apply { setLineSpacing(0f, 1.1f) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 4) })
                else if (v.isNotEmpty()) row.addView(Ui.text(c, v, 13f, Ui.DIM).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = gap }) }
        }
        if (r.kind != 'I') {
            row.isFocusable = true; row.isClickable = true
            row.setOnClickListener { r.act(1); changed() }
            if (r.kind == 'C') row.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) { r.act(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1); changed(); true } else false
            }
            else row.setOnKeyListener { _, code, ev -> if (ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) { goBack(); true } else false }
        }
        return row
    }

    @Deprecated("Back goes up one page, then leaves.")
    override fun onBackPressed() = goBack()

    override fun onDestroy() { phone?.stop(); super.onDestroy() }
}
