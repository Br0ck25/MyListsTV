package tv.myliststv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * The home screen. Plain Views only: a top bar with tabs, and one screen at a time below it.
 * Screens are rebuilt when selected, because their data comes from the cache and a screen left in memory costs RAM.
 */
class MainActivity : Activity() {

    private val tabs = listOf("Home", "Movies", "Series", "Live", "Apps")
    private val tabViews = ArrayList<TextView>()
    private lateinit var content: FrameLayout
    private lateinit var searchBtn: View
    private lateinit var settingsBtn: View
    private lateinit var profileAvatar: TextView
    private lateinit var profileName: TextView
    private var current = -1
    private var screen: Any? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSelect: Runnable? = null
    private var ready = false
    private lateinit var bg: ImageView
    private lateinit var scrims: List<View>
    private lateinit var bellBtn: View
    private lateinit var side: LinearLayout
    private val navStyle = Prefs.navStyle
    private val sideWidth get() = Ui.dp(this, 200)
    private lateinit var bellBadge: TextView
    private var backdropUrl: String? = null

    private fun updateBell() { val n = Notices.list(this).size; bellBadge.text = n.toString(); bellBadge.visibility = if (n > 0) View.VISIBLE else View.GONE }

    private fun updateProfile() { val n = Profiles.active().name; profileName.text = n; profileAvatar.text = n.take(1).uppercase() }

    /** Who is watching: the current profile at the top right; OK switches. */
    private fun profileChip(): View = Ui.hbox(this, 8).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 5))
        background = Ui.pillBg(this@MainActivity); isFocusable = true; isClickable = true
        profileAvatar = Ui.text(this@MainActivity, "", 14f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.rounded(this@MainActivity, Ui.BRAND_FILL, Ui.R_PILL) }
        profileName = Ui.text(this@MainActivity, "", 14f, Ui.TEXT, true, 1)
        addView(profileAvatar, LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 30)))
        addView(profileName, Ui.wrap())
        setOnFocusChangeListener { _, f -> profileName.setTextColor(if (f) Ui.ON_FOCUS else Ui.TEXT) }
        setOnClickListener { pickProfile() }
    }

    private fun pickProfile() {
        val l = Profiles.all()
        AlertDialog.Builder(this).setTitle("Who is watching?").setItems(l.map { it.name + if (it.kids) " (kids)" else "" }.toTypedArray()) { _, i ->
            Profiles.activeId = l[i].id; WatchStore.init(this); updateProfile(); select(0, force = true)
        }.show()
    }

    private fun showNotices() {
        if (!Notices.allowed(this)) {
            AlertDialog.Builder(this).setTitle("Notifications").setMessage("To show your TV's notifications here, give My Lists TV Notification access. They stay on this box and are not stored or sent anywhere.")
                .setPositiveButton("Open the setting") { _, _ -> try { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { android.widget.Toast.makeText(this, "This TV has no such setting. See Settings, Home screen, for the phone steps.", android.widget.Toast.LENGTH_LONG).show() } }
                .setNegativeButton("Not now", null).show()
            return
        }
        val l = Notices.list(this)
        if (l.isEmpty()) { AlertDialog.Builder(this).setTitle("Notifications").setMessage("Nothing new.").setPositiveButton("OK", null).show(); return }
        AlertDialog.Builder(this).setTitle("Notifications")
            .setItems(l.map { "${it.app}: ${it.title}\n${it.text}".trim() }.toTypedArray()) { _, i ->
                try { l[i].open?.send() } catch (_: Exception) {}
                Notices.dismiss(l[i].key); updateBell()
            }.setNeutralButton("Clear all") { _, _ -> Notices.dismissAll(); updateBell() }.setPositiveButton("Close", null).show()
    }
    private var backdropJob: Runnable? = null

    /** Shows [url] behind the screen after the focus has rested for a moment, so scrolling fast does not load every picture. A null leaves the current one. */
    private fun setBackdrop(url: String?) {
        if (url == null || url == backdropUrl) return
        backdropJob?.let { handler.removeCallbacks(it) }
        backdropJob = Runnable { backdropUrl = url; ImageLoader.load(bg, url, Ui.dp(this, if (Prefs.lite()) 640 else 960), Ui.dp(this, if (Prefs.lite()) 360 else 540), fade = !Prefs.lite()) }.also { handler.postDelayed(it, 250) }
    }

    private fun showBackdrop(on: Boolean) {
        val v = if (on) View.VISIBLE else View.GONE
        bg.visibility = v; scrims.forEach { it.visibility = v }
        if (!on) { backdropJob?.let { handler.removeCallbacks(it) }; backdropUrl = null; bg.setImageDrawable(null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notices.onChange = { handler.post { if (::bellBadge.isInitialized) updateBell() } }
        // Layers, back to front: the focused title's backdrop, two dark fades for readability, the screen, then the tab bar.
        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        scrims = listOf(
            View(this).apply { background = Ui.scrimLeft() },
            View(this).apply { background = Ui.scrimBottom() },
        )
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        root.addView(scrims[0], FrameLayout.LayoutParams(-1, -1))
        root.addView(scrims[1], FrameLayout.LayoutParams(-1, Ui.dp(this, 420), Gravity.BOTTOM))
        content = FrameLayout(this).apply { setPadding(0, Ui.dp(this@MainActivity, 64), 0, 0) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val bar = Ui.hbox(this, 8).apply {
            setPadding(Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 8))
            background = Ui.scrimTop()
        }
        side = Ui.vbox(this).apply {
            val p = Ui.dp(this@MainActivity, 10); setPadding(p, Ui.dp(this@MainActivity, 72), p, p)
            background = Ui.rounded(this@MainActivity, Ui.CARD, Ui.R_DIALOG)
            visibility = if (navStyle == 1) View.VISIBLE else View.GONE
        }
        tabs.forEachIndexed { i, t ->
            val v = tabView(t)
            v.setOnFocusChangeListener { view, f ->
                (view as TextView).setTextColor(if (f) Ui.ON_FOCUS else if (i == current) Ui.TEXT else Ui.DIM)
            }
            v.setOnClickListener { select(i, force = true); if (navStyle == 2) closeSide() }
            tabViews += v
            if (navStyle == 0) bar.addView(v) else side.addView(v, LinearLayout.LayoutParams(-1, -2))
        }
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val icon = Ui.dp(this, 40)
        bellBadge = Ui.text(this, "", 11f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.rounded(this@MainActivity, Ui.BRAND_FILL, Ui.R_PILL); visibility = View.GONE; setPadding(Ui.dp(this@MainActivity, 5), 0, Ui.dp(this@MainActivity, 5), 0) }
        bellBtn = FrameLayout(this).apply {
            addView(Ui.iconButton(this@MainActivity, R.drawable.ic_bell, "Notifications") { showNotices() }, FrameLayout.LayoutParams(icon, icon))
            addView(bellBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this@MainActivity, 16), Gravity.TOP or Gravity.END))
        }
        searchBtn = Ui.iconButton(this, R.drawable.ic_search, "Search") { startActivity(Intent(this, SearchActivity::class.java)) }
        settingsBtn = Ui.iconButton(this, R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        bar.addView(profileChip(), LinearLayout.LayoutParams(-2, icon).apply { rightMargin = Ui.dp(this@MainActivity, 8) })
        bar.addView(bellBtn, LinearLayout.LayoutParams(icon, icon)); bar.addView(searchBtn, LinearLayout.LayoutParams(icon, icon)); bar.addView(settingsBtn, LinearLayout.LayoutParams(icon, icon))
        updateProfile()
        if (navStyle != 0) {
            root.addView(side, FrameLayout.LayoutParams(sideWidth, -1, Gravity.START).apply { setMargins(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12)) })
            if (navStyle == 1) content.setPadding(sideWidth + Ui.dp(this, 16), Ui.dp(this, 64), 0, 0)
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        // Moving along the tabs switches page; coming up from a page onto the tabs does not (OK does).
        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            val i = tabViews.indexOf(new)
            if (i >= 0 && old != null && old in tabViews) { pendingSelect?.let { handler.removeCallbacks(it) }; pendingSelect = Runnable { select(i) }.also { handler.postDelayed(it, 140) } }
        }
        content.addView(Ui.text(this, "Loading…", 16f, Ui.DIM).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        val safe = CrashGuard.safeMode(this)
        val start = { ready = true; select(0, force = true); tabViews[0].requestFocus(); afterStart(safe); intent.getStringExtra("go")?.let { go(it) } }
        if (safe) start() else AddonStore.load { start() }
        handler.postDelayed({ CrashGuard.clear(this) }, 30_000)
    }

    /** Safe mode, first-run welcome, or the "who is watching" question, whichever applies. */
    private fun afterStart(safe: Boolean) {
        if (safe) AlertDialog.Builder(this).setTitle("Safe mode").setCancelable(false)
            .setMessage("My Lists TV stopped several times in a row, so addons are off for this start.")
            .setPositiveButton("Start normally") { _, _ -> CrashGuard.clear(this); recreate() }
            .setNegativeButton("Reset addons and caches") { _, _ -> AddonStore.reset(); Http.clear(); ImageLoader.clearDisk(); CrashGuard.clear(this); recreate() }.show()
        else if (!Prefs.setupDone) AlertDialog.Builder(this).setTitle("Welcome to My Lists TV")
            .setMessage("You are seeing the default My Lists catalogs. To play video, add a stream addon in Settings, then Addons; you can paste its link from your phone. Simple mode hides settings most people never need.")
            .setPositiveButton("Set up addons") { _, _ -> Prefs.setupDone = true; startActivity(Intent(this, AddonsActivity::class.java)) }
            .setNeutralButton("Advanced mode") { _, _ -> Prefs.setupDone = true; Prefs.advanced = true }
            .setNegativeButton("Skip") { _, _ -> Prefs.setupDone = true }.show()
        else if (Profiles.all().size > 1 && !Profiles.askedThisRun) {
            Profiles.askedThisRun = true
            pickProfile()
        }
        Profiles.askedThisRun = true
    }

    private fun tabView(t: String) = TextView(this).apply {
        text = t; Ui.size(this, 15f); setTextColor(Ui.DIM); typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8))
        background = Ui.pillBg(this@MainActivity, Ui.CLEAR); isFocusable = true; isClickable = true
    }

    private fun select(i: Int, force: Boolean = false) {
        if (!ready) return
        if (i == current && !force) return
        current = i
        tabViews.forEachIndexed { idx, v -> v.isSelected = navStyle != 0 && idx == i; if (!v.isFocused) v.setTextColor(if (idx == i) Ui.TEXT else Ui.DIM) }
        (screen as? AddonsScreen)?.stop()
        content.removeAllViews()
        screen = null
        showBackdrop(i in 0..2)
        when (i) {
            0, 1, 2 -> {
                val s = BrowseScreen(this, type = when (i) { 1 -> "movie"; 2 -> "series"; else -> null }, withContinue = i == 0, withApps = i == 0,
                    onMeta = ::openMeta, onResume = ::resumeEntry, onApp = ::launch, onAppMenu = ::appMenu, onBackdrop = ::setBackdrop)
                screen = s; content.addView(s.view); s.start()
            }
            3 -> { val s = LiveScreen(this); screen = s; content.addView(s.view); s.start() }
            4 -> { val s = AppsScreen(this, ::launch, ::appMenu); screen = s; content.addView(s.view); s.start() }
        }
    }

    private var lastHome = 0L

    /** Where a mapped remote button sends the launcher. */
    private fun go(where: String) {
        val tab = listOf("home", "movies", "series", "live", "apps").indexOf(where)
        when {
            tab >= 0 -> { select(tab, force = true); tabViews[tab].requestFocus() }
            where == "search" -> startActivity(Intent(this, SearchActivity::class.java))
            where == "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            where == "profiles" -> pickProfile()
        }
    }

    private fun openMeta(m: Meta) {
        startActivity(Intent(this, DetailsActivity::class.java).putExtra("meta", Json.metaToJson(m)))
    }

    private fun resumeEntry(e: WatchStore.Entry) {
        val m = Meta(e.metaId, e.type, e.name, e.poster, e.background)
        startActivity(Intent(this, DetailsActivity::class.java).putExtra("meta", Json.metaToJson(m)).putExtra("play", e.videoId))
    }

    private fun launch(a: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = a.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addCategory(if (a.sideloaded) Intent.CATEGORY_LAUNCHER else Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "Could not open ${a.label}", Toast.LENGTH_SHORT).show() }
    }

    private fun appMenu(a: AppEntry) {
        val pkg = a.component.packageName
        val onHome = current == 0
        val items = if (onHome) arrayOf("Move earlier", "Move later", "Hide from Home", "App info", "Uninstall") else arrayOf("App info", "Uninstall")
        AlertDialog.Builder(this).setTitle(a.label).setItems(items) { _, i ->
            val which = i - if (onHome) 3 else 0
            if (which < 0) {
                Http.background {
                    val order = AppRepository.forHome(this).map { it.component.packageName }.toMutableList()
                    val at = order.indexOf(pkg)
                    if (i == 2) Prefs.appHidden = Prefs.appHidden + pkg
                    else if (at >= 0) { val to = (at + if (i == 0) -1 else 1).coerceIn(0, order.size - 1); order.add(to, order.removeAt(at)); Prefs.appOrder = order }
                    Http.post { select(current, force = true) }
                }
                return@setItems
            }
            try {
                if (which == 0) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                else startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            } catch (e: Exception) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show() }
        }.show()
    }

    private var seenEpoch = Prefs.epoch

    override fun onResume() {
        super.onResume()
        if (Prefs.navStyle != navStyle) { recreate(); return }
        if (::bellBadge.isInitialized) updateBell()
        if (::profileName.isInitialized) updateProfile()
        if (Prefs.epoch != seenEpoch) { seenEpoch = Prefs.epoch; if (ready && current >= 0) select(current, force = true) }
        if (AddonStore.needsRefresh) { AddonStore.needsRefresh = false; AddonStore.load { select(current.coerceAtLeast(0), force = true) } }
        (screen as? BrowseScreen)?.refreshContinue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Home goes to the Home page; pressing it twice quickly asks who is watching.
            val t = System.currentTimeMillis()
            if (t - lastHome < 450) { lastHome = 0; pickProfile(); return }
            lastHome = t
            if (current != 0) select(0, force = true)
            tabViews[0].requestFocus()
        }
        intent.getStringExtra("go")?.let { go(it) }
    }

    @Deprecated("Back moves to the tabs; the launcher itself never closes.")
    override fun onBackPressed() {
        val f = currentFocus
        val inBar = f != null && (f.parent === tabViews[0].parent)
        if (!inBar && current >= 0) tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus()
        // On the tab bar, Back does nothing: this is the home screen.
    }

    /** Hidden side bar: Left from the left edge of a page opens it; Right, OK on a page or Back closes it. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (navStyle == 2 && e.action == KeyEvent.ACTION_DOWN) {
            val f = currentFocus
            val open = side.visibility == View.VISIBLE
            if (!open && e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && f != null && f !in tabViews && f.focusSearch(View.FOCUS_LEFT).let { it == null || !isInContent(it) }) { openSide(); return true }
            if (open && e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { closeSide(); return true }
            if (open && e.keyCode == KeyEvent.KEYCODE_BACK) { closeSide(); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun isInContent(v: View): Boolean { var p: android.view.ViewParent? = v.parent; while (p != null) { if (p === content) return true; p = p.parent }; return false }
    private fun openSide() { side.visibility = View.VISIBLE; tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus() }
    private fun closeSide() { side.visibility = View.GONE; content.requestFocus() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) { startActivity(Intent(this, SearchActivity::class.java)); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { (screen as? AddonsScreen)?.stop(); super.onDestroy() }

    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_UI_HIDDEN) ImageLoader.trim() }
}
