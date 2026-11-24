package tv.myliststv.launcher

import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

import android.app.Activity
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
import android.view.ViewGroup
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

    private val tabs = listOf("Home", "Movies", "Series", "Search", "Library", "Apps")
    private val tabViews = ArrayList<TextView>()
    private lateinit var content: FrameLayout
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
    private lateinit var clock: TextView
    private val clockTick = object : Runnable { override fun run() { clock.text = android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(java.util.Date()); handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L) } }
    private lateinit var topBar: LinearLayout
    /** True while an app is being moved with the D-pad (hold OK on an app, Move). */
    private var moving = false
    private val navStyle = Prefs.navStyle
    private val sideWidth get() = Ui.dp(this, 200)
    private lateinit var bellBadge: TextView
    private var backdropUrl: String? = null

    private fun updateBell() { val n = Notices.list(this).size + Alerts.all().size; bellBadge.text = n.toString(); bellBadge.visibility = if (n > 0) View.VISIBLE else View.GONE }

    private fun updateProfile() { val n = Profiles.active().name; profileName.text = n; profileAvatar.text = n.take(1).uppercase() }

    /** Who is watching: the current profile at the top right; OK switches. */
    private var profileChipView: View? = null

    private fun profileChip(): View = Ui.hbox(this, 8).apply {
        profileChipView = this
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 5))
        background = Ui.pillBg(this@MainActivity); isFocusable = true; isClickable = true
        profileAvatar = Ui.text(this@MainActivity, "", 14f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.rounded(this@MainActivity, Ui.BRAND_FILL, Ui.R_PILL) }
        profileName = Ui.text(this@MainActivity, "", 14f, Ui.TEXT, true, 1)
        addView(profileAvatar, LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 30)))
        addView(profileName, Ui.wrap())
        setOnClickListener { pickProfile() }
    }

    private fun pickProfile() {
        val l = Profiles.all()
        Sheet.menu(this, "Who is watching?", null, l.map { p ->
            (p.name + if (p.kids) " (kids)" else "") to {
                Profiles.switchTo(this, p.id); updateProfile(); AddonStore.needsRefresh = false; AddonStore.load { dropPages(); select(0, force = true) }; Library.pull { }; SetupSync.pull { reloadAddons() }; Progress.pull { (screen as? BrowseScreen)?.refreshContinue() }
            }
        })
    }

    /** The bell: a panel down the right edge. New-episode alerts for your Library first, then the TV's own notifications when access is given. */
    private fun showNotices() {
        val c = this
        val panel = SidePanel(c)
        val list = Ui.vbox(c)
        val page = SidePanel.page(c)
        page.addView(SidePanel.header(c, "Notifications", R.drawable.ic_bell, Ui.button(c, "Clear all") { Alerts.clear(); Notices.dismissAll(); updateBell(); fill(list, panel) }))
        page.addView(android.widget.ScrollView(c).apply { addView(list); isVerticalScrollBarEnabled = false; clipToPadding = false }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 14) })
        panel.show(page)
        fill(list, panel)
    }

    private fun fill(list: LinearLayout, panel: SidePanel) {
        val c = this
        list.removeAllViews()
        val allowed = Notices.allowed(c)
        val own = Alerts.all(); val sys = if (allowed) Notices.list(c) else emptyList()
        fun card(pkg: String, app: String, time: Long, title: String, text: String, open: () -> Unit, clear: () -> Unit) {
            val row = Ui.hbox(c, 10).apply { gravity = Gravity.CENTER_VERTICAL }
            val body = Ui.vbox(c).apply {
                setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12)); Ui.rowStyle(c, this, fill = Ui.ELEVATED); isFocusable = true; isClickable = true
                setOnClickListener { open(); panel.dismiss() }
                val top = Ui.hbox(c, 8)
                top.addView(android.widget.ImageView(c).apply { try { setImageDrawable(if (pkg.isEmpty()) getDrawable(R.drawable.ic_launcher) else packageManager.getApplicationIcon(pkg)) } catch (_: Exception) { } }, LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)))
                top.addView(Ui.text(c, app, 12f, Ui.DIM, false, 1), LinearLayout.LayoutParams(0, -2, 1f))
                if (time > 0) top.addView(Ui.text(c, android.text.format.DateFormat.format("HH:mm", time).toString(), 12f, Ui.DIM))
                addView(top); addView(Ui.text(c, title, 16f, Ui.TEXT, true, 1), Ui.matchWrap().apply { topMargin = Ui.dp(c, 6) })
                if (text.isNotEmpty()) addView(Ui.text(c, text, 14f, Ui.TEXT2, false, 2))
            }
            row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Ui.iconButton(c, R.drawable.ic_close, "Clear") { clear(); updateBell(); fill(list, panel) }, LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)))
            list.addView(row, Ui.matchWrap().apply { bottomMargin = Ui.dp(c, 10) })
        }
        own.forEach { a -> card("", "My Lists TV", 0L, a.title, a.text, { openMeta(Library.all().firstOrNull { it.id == a.metaId } ?: Meta(a.metaId, a.type, a.title)) }, { Alerts.remove(a.id) }) }
        sys.forEach { n -> card(n.pkg, n.app, n.time, n.title, n.text, { try { n.open?.send() } catch (_: Exception) {}; Notices.dismiss(n.key) }, { Notices.dismiss(n.key) }) }
        if (own.isEmpty() && sys.isEmpty()) list.addView(Ui.text(c, if (allowed) "Nothing new. New episodes of series in your Library show up here." else "Nothing new yet.", 14f, Ui.DIM, false, 3))
        if (!allowed) list.addView(Ui.button(c, "Show the TV's own notifications too") {
            try { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { Pop.toast(c, "This TV has no such setting. See Settings, Launcher setup, for the phone steps.", true) }
        }, Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
    }

    /** The gear: Apps, Inputs, Launcher settings (the full settings) and Android settings, in the same right-hand panel. */
    private fun showSettingsPanel() {
        val c = this
        val panel = SidePanel(c)
        val page = SidePanel.page(c)
        page.addView(SidePanel.header(c, "Settings"))
        fun item(icon: Int, label: String, go: () -> Unit) = page.addView(SidePanel.item(c, icon, label, go), Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) })
        item(R.drawable.ic_apps, "Apps") { panel.push(appsPage(panel)) }
        item(R.drawable.ic_tv, "Inputs") { panel.push(SystemPanel.inputs(c, panel)) }
        item(R.drawable.ic_home, "Launcher settings") { panel.dismiss(); startActivity(Intent(c, SettingsActivity::class.java)) }
        item(R.drawable.ic_settings, "Android settings") { panel.push(SystemPanel.androidSettings(c, panel)) }
        panel.show(page)
    }

    /** Every app as a banner with its name under it, two across; OK opens it, hold OK for its menu. */
    private fun appsPage(panel: SidePanel): View {
        val c = this
        val page = SidePanel.page(c)
        page.addView(SidePanel.header(c, "Apps"))
        val grid = androidx.recyclerview.widget.RecyclerView(c).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(c, 2); itemAnimator = null; clipToPadding = false; setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8))
        }
        page.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 10) })
        Http.background {
            val apps = AppRepository.load(c)
            Http.post { grid.adapter = AppGridAdapter(c, apps, { panel.dismiss(); launch(it) }, { appMenu(it) }) }
        }
        return page
    }

    private var backdropJob: Runnable? = null

    /** Shows [url] behind the screen after the focus has rested for a moment, so scrolling fast does not load every picture. A null leaves the current one. */
    private fun setBackdrop(url: String?) {
        if (url == "") { if (Pictures.wallpaperFile(this).exists()) idleBackdrop(); return }    // "" means no title: show the wallpaper
        if (url == null || url == backdropUrl) return
        backdropJob?.let { handler.removeCallbacks(it) }
        backdropJob = Runnable { backdropUrl = url; ImageLoader.load(bg, url, Ui.dp(this, if (Prefs.lite()) 640 else 960), Ui.dp(this, if (Prefs.lite()) 360 else 540), fade = !Prefs.lite()) }.also { handler.postDelayed(it, 250) }
    }

    private fun showBackdrop(on: Boolean) {
        val wall = Pictures.wallpaperFile(this).exists()
        val v = if (on || wall) View.VISIBLE else View.GONE
        bg.visibility = v; scrims.forEach { it.visibility = v }
        if (!on) { backdropJob?.let { handler.removeCallbacks(it) }; backdropUrl = null; bg.setImageDrawable(null) }
        if (wall && bg.drawable == null) idleBackdrop()
    }

    /** No title is highlighted: the owner's wallpaper, if they picked one. */
    private fun idleBackdrop() {
        backdropJob?.let { handler.removeCallbacks(it) }; backdropUrl = null
        val f = Pictures.wallpaperFile(this).takeIf { it.exists() } ?: return
        Http.background {
            val bmp = try { android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) } catch (_: Throwable) { null }
            Http.post { if (bmp != null && backdropUrl == null) bg.setImageBitmap(bmp) }
        }
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
        topBar = Ui.hbox(this, 8).apply {
            setPadding(Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 8))
            background = Ui.scrimTop()
        }
        side = Ui.vbox(this).apply {
            val p = Ui.dp(this@MainActivity, 10); setPadding(p, Ui.dp(this@MainActivity, 16), p, p)
            background = Ui.rounded(this@MainActivity, Ui.SIDE_GLASS, Ui.R_DIALOG)
            visibility = if (navStyle == 1) View.VISIBLE else View.GONE
        }
        tabs.forEachIndexed { i, t ->
            val v = tabView(t)
            v.setOnFocusChangeListener { view, f ->
                (view as TextView).setTextColor(if (f || i == current) Ui.TEXT else Ui.DIM)
            }
            v.setOnClickListener { select(i, force = true); if (navStyle == 2) closeSide() }
            tabViews += v
            if (navStyle == 0) topBar.addView(v) else side.addView(v, LinearLayout.LayoutParams(-1, -2))
        }
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val icon = Ui.dp(this, 40)
        bellBadge = Ui.text(this, "", 11f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.rounded(this@MainActivity, Ui.BRAND_FILL, Ui.R_PILL); visibility = View.GONE; setPadding(Ui.dp(this@MainActivity, 5), 0, Ui.dp(this@MainActivity, 5), 0) }
        bellBtn = FrameLayout(this).apply {
            addView(Ui.iconButton(this@MainActivity, R.drawable.ic_bell, "Notifications") { showNotices() }, FrameLayout.LayoutParams(icon, icon))
            addView(bellBadge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this@MainActivity, 16), Gravity.TOP or Gravity.END))
        }
        settingsBtn = Ui.iconButton(this, R.drawable.ic_settings, "Settings") { showSettingsPanel() }
        if (navStyle == 0) topBar.addView(profileChip(), LinearLayout.LayoutParams(-2, icon).apply { rightMargin = Ui.dp(this@MainActivity, 8) })
        else side.addView(profileChip(), 0, LinearLayout.LayoutParams(-1, icon).apply { bottomMargin = Ui.dp(this@MainActivity, 10) })
        topBar.addView(bellBtn, LinearLayout.LayoutParams(icon, icon));  topBar.addView(settingsBtn, LinearLayout.LayoutParams(icon, icon))
        clock = Ui.text(this, "", 16f, Ui.TEXT2, true).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(this@MainActivity, 14), 0, 0, 0) }
        topBar.addView(clock, LinearLayout.LayoutParams(-2, icon))
        updateProfile()
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        if (navStyle != 0) {
            root.addView(side, FrameLayout.LayoutParams(sideWidth, -1, Gravity.START).apply { setMargins(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12)) })
            if (navStyle == 1) { content.setPadding(slimWidth - Ui.dp(this, 12), Ui.dp(this, 64), 0, 0); setSideOpen(false) }
        }
        setContentView(root)
        // Moving along the tabs switches page; coming up from a page onto the tabs does not (OK does).
        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            val i = tabViews.indexOf(new)
            if (navStyle == 1) { val inSide = new != null && isIn(new, side); if (inSide != sideOpen) setSideOpen(inSide) else refreshRailTint() }
            if (navStyle != 0 && i >= 0) tabViews.forEachIndexed { k, v -> v.isSelected = k == i }   // the highlight follows the focus at once, no trail
            if (navStyle == 1) refreshRailTint()
            if (i >= 0 && old != null && old in tabViews) { pendingSelect?.let { handler.removeCallbacks(it) }; pendingSelect = Runnable { select(i) }.also { handler.postDelayed(it, 140) } }
        }
        content.addView(Ui.text(this, "Loading…", 16f, Ui.DIM).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        val safe = CrashGuard.safeMode(this)
        val start = { ready = true; select(0, force = true); tabViews[0].requestFocus(); afterStart(safe); if (!safe) syncAccount(); intent.getStringExtra("go")?.let { go(it) } }
        if (safe) start() else AddonStore.load { start() }
        handler.postDelayed({ CrashGuard.clear(this) }, 30_000)
    }

    /** Safe mode, first-run welcome, or the "who is watching" question, whichever applies. */
    private fun afterStart(safe: Boolean) {
        if (safe) Sheet.message(this, "Safe mode", "My Lists TV stopped several times in a row, so addons are off for this start.",
            Sheet.Act("Start normally", true) { CrashGuard.clear(this); recreate() },
            Sheet.Act("Reset addons and caches") { AddonStore.reset(); Http.clear(); ImageLoader.clearDisk(); CrashGuard.clear(this); recreate() }, cancelable = false)
        else if (!Prefs.setupDone) welcome()
        else if (Profiles.all().size > 1 && !Profiles.askedThisRun) {
            Profiles.askedThisRun = true
            pickProfile()
        }
        Profiles.askedThisRun = true
    }

    /** First run: the setup screens (Home app, Home button, notifications, addons), each one skippable. */
    private fun welcome() = startActivity(Intent(this, SetupActivity::class.java))

    private var sideOpen = true
    private val slimWidth get() = Ui.dp(this, 72)

    /** Side bar (always there): a slim strip of icons that opens into the labelled panel while the focus is on it. */
    private fun setSideOpen(open: Boolean) {
        if (navStyle != 1 || !::side.isInitialized) return
        sideOpen = open
        side.layoutParams = (side.layoutParams as FrameLayout.LayoutParams).apply { width = if (open) sideWidth else slimWidth }
        side.background = if (open) Ui.rounded(this, Ui.SIDE_GLASS, Ui.R_DIALOG) else android.graphics.drawable.ColorDrawable(Ui.CLEAR)
        sideItems().forEach { it.showLabel(open, Ui.dp(this, 14)) }
        if (::profileName.isInitialized) profileName.visibility = if (open) View.VISIBLE else View.GONE
        // Slim: no pills, just the icons; the one that is focused gets a ring. Open: the names, and the open page outlined.
        sideItems().forEach {
            it.background = if (open) Ui.pillBg(this, Ui.CLEAR) else Ui.focusBg(this, Ui.R_PILL, Ui.FOCUS_FILL)
            it.setPadding(Ui.dp(this, if (open) 16 else 18), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12))
        }
        profileChipView?.apply {
            background = if (open) Ui.pillBg(this@MainActivity) else Ui.focusBg(this@MainActivity, Ui.R_PILL, Ui.FOCUS_FILL)
            (this as LinearLayout).gravity = if (open) Gravity.CENTER_VERTICAL else Gravity.CENTER
            setPadding(Ui.dp(this@MainActivity, if (open) 6 else 0), Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, if (open) 16 else 0), Ui.dp(this@MainActivity, 5))
        }
        refreshRailTint()
    }

    /** While the strip is slim only the icons show: the open page's is blue, the others dim. */
    private fun refreshRailTint() {
        if (navStyle != 1 || sideOpen) return
        tabViews.forEach { it.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (it.isSelected) Ui.BRAND else Ui.DIM) }
    }

    private fun sideItems(): List<TabText> = tabViews.map { it as TabText }

    private val tabIcons = listOf(R.drawable.ic_home, R.drawable.ic_play, R.drawable.ic_tv, R.drawable.ic_search, R.drawable.ic_bookmark, R.drawable.ic_apps)

    private fun tabView(t: String) = TabText(this).apply {
        text = t; label = t; Ui.size(this, if (navStyle != 0) 20f else 15f); typeface = android.graphics.Typeface.DEFAULT_BOLD
        val v = if (navStyle != 0) 12 else 8
        setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, v), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, v))
        background = Ui.pillBg(this@MainActivity, Ui.CLEAR); isFocusable = true; isClickable = true
        // The side bar shows an icon before each name, in the same colour as the name.
        if (navStyle != 0) {
            val d = getDrawable(tabIcons[tabs.indexOf(t).coerceAtLeast(0)])!!.mutate(); val s = Ui.dp(this@MainActivity, 22)
            d.setBounds(0, 0, s, s); setCompoundDrawables(d, null, null, null); compoundDrawablePadding = Ui.dp(this@MainActivity, 14); gravity = Gravity.CENTER_VERTICAL
        }
        setTextColor(Ui.DIM)
    }

    /** Home, Movies, Series and Search stay built for a few minutes, so going back to one is not a reload and keeps its place and its focus. */
    private class Kept(val screen: Any, val view: View, val at: Long)
    private val pages = HashMap<Int, Kept>()
    private val PAGE_KEEP_MS = 5 * 60_000L

    /** What the pages show changed (addons, profile, settings): none of the built pages is right any more. */
    private fun dropPages() = pages.clear()

    private fun select(i: Int, force: Boolean = false) {
        if (!ready) return
        if (i == current && !force) return
        if (force) pages.remove(i)
        current = i
        tabViews.forEachIndexed { idx, v -> v.isSelected = navStyle != 0 && idx == i; if (!v.isFocused) v.setTextColor(if (idx == i) Ui.TEXT else Ui.DIM) }
        refreshRailTint()
        (screen as? SearchScreen)?.stop()
        content.removeAllViews()
        screen = null
        showBackdrop(i in 0..4)
        val kept = pages[i]
        if (kept != null && System.currentTimeMillis() - kept.at < PAGE_KEEP_MS) {
            screen = kept.screen; content.addView(kept.view)
            (kept.screen as? BrowseScreen)?.refreshContinue()   // only what changed while you were away
            return
        }
        fun keep(s: Any, v: View) {
            pages[i] = Kept(s, v, System.currentTimeMillis())
            // Only the four newest pages stay built (Home, Movies, Series and Search) (a small box has little memory to spare).
            while (pages.size > 4) pages.minByOrNull { it.value.at }?.let { pages.remove(it.key) }
        }
        when (i) {
            0, 1, 2 -> {
                val s = BrowseScreen(this, type = when (i) { 1 -> "movie"; 2 -> "series"; else -> null }, withContinue = i == 0, withApps = i == 0,
                    onMeta = ::openMeta, onResume = ::resumeEntry, onApp = ::launch, onAppMenu = ::appMenu, onBackdrop = ::setBackdrop)
                screen = s; content.addView(s.view); s.start(); keep(s, s.view)
            }
            3 -> { val s = SearchScreen(this, ::openMeta, ::setBackdrop); screen = s; content.addView(s.view); s.start(); keep(s, s.view) }
            4 -> { val s = LibraryScreen(this, ::openMeta, ::setBackdrop); screen = s; content.addView(s.view); s.start() }
            5 -> { val s = AppsScreen(this, ::launch, ::appMenu); screen = s; content.addView(s.view); s.start() }
        }
    }

    private var lastHome = 0L
    private var lastSync = 0L

    private fun reloadAddons() { AddonStore.needsRefresh = false; AddonStore.load { dropPages(); select(current.coerceAtLeast(0), force = true) } }

    /** Where a mapped remote button sends the launcher. */
    private fun go(where: String) {
        val tab = listOf("home", "movies", "series", "search", "library", "apps").indexOf(where)
        when {
            tab >= 0 -> { select(tab); tabViews[tab].requestFocus() }
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
        try { startActivity(intent) } catch (e: Exception) { Pop.toast(this, "Could not open ${a.label}") }
    }

    private fun appMenu(a: AppEntry) {
        val pkg = a.component.packageName
        val system = { intent: Intent -> try { startActivity(intent) } catch (e: Exception) { Pop.toast(this, "Not available on this device") } }
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (current == 0) {
            items += "Move" to { moving = true; Pop.toast(this, "Move it with Left and Right, then press OK", true) }
            items += "Hide from Home" to { Prefs.appHidden = Prefs.appHidden + pkg; if ((screen as? BrowseScreen)?.hideApp(pkg) != true) select(current, force = true) }
        }
        items += "Rename" to { Sheet.ask(this, "Rename app", "Name", "Save", "Leave it empty to use the app's own name.", initial = a.label) { Prefs.setAppName(pkg, it); select(current, force = true) } }
        items += "Change icon" to { iconMenu(a) }
        items += "App info" to { system(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }
        items += "Uninstall" to { system(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
        Sheet.menu(this, a.label, "Installed app", items)
    }

    private val pictures = PicturePicker(this, 42)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); pictures.result(requestCode, resultCode, data) }

    /** A picture of the owner's own for one app (an icon pack changes them all, in Settings). */
    private fun iconMenu(a: AppEntry) {
        val file = Pictures.iconFile(this, a.component.packageName)
        val redraw = { dropPages(); select(current, force = true) }
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Choose a picture" to { pictures.pick { uri ->
            Http.background { val ok = Pictures.save(this, uri, file, 480, 270, png = true); Http.post { if (ok) redraw() else Pop.toast(this, "That file is not a picture this TV can read.", true) } }
        } }
        if (file.exists()) items += "Back to the usual icon" to { file.delete(); redraw() }
        Sheet.menu(this, "Icon for ${a.label}", "Pick any picture on the TV. Square ones look best; wide ones fill the whole card.", items)
    }

    private var seenEpoch = Prefs.epoch

    override fun onPause() { handler.removeCallbacks(clockTick); super.onPause() }

    /** Pulls what the account holds (Library, addons, Continue Watching, Airing Next) at most every five minutes; also right after the first load, which onResume cannot do as the pages are not ready yet. */
    private fun syncAccount() {
        val t = System.currentTimeMillis()
        if (t - lastSync <= 5 * 60_000L) return
        lastSync = t
        Library.pull { if (current == 4) select(4, force = true) }
        SetupSync.pull { reloadAddons() }
        Progress.pull { (screen as? BrowseScreen)?.refreshContinue() }
        AccountWatched.pull { (screen as? BrowseScreen)?.refreshChecks(); if (current >= 4) select(current, force = true) }
    }

    override fun onResume() {
        if (::clock.isInitialized) { handler.removeCallbacks(clockTick); clockTick.run() }
        super.onResume()
        if (Prefs.navStyle != navStyle) { recreate(); return }
        if (ready) {
            Alerts.check { if (::bellBadge.isInitialized) updateBell() }
            syncAccount()
        }
        if (::bellBadge.isInitialized) updateBell()
        if (::profileName.isInitialized) updateProfile()
        if (Prefs.epoch != seenEpoch) { seenEpoch = Prefs.epoch; dropPages(); bg.setImageDrawable(null); backdropUrl = null; if (ready && current >= 0) select(current, force = true) }
        if (AddonStore.needsRefresh) { AddonStore.needsRefresh = false; AddonStore.load { dropPages(); select(current.coerceAtLeast(0), force = true) } }
        (screen as? BrowseScreen)?.refreshContinue()
        // Back from a details page: nothing is focused now, so put the focus where it was (otherwise Down lands on whatever sits under the pointer).
        content.post { if (ready && currentFocus == null) { if ((screen as? BrowseScreen)?.restoreFocus() != true) tabViews.getOrNull(current)?.requestFocus() } }
    }

    private var pendingHome: Runnable? = null
    private var lastHomeSeen = 0L

    /** One of the Home button's jobs: to the top of Home, to another page, or any job the button mapper can do. */
    private fun runHome(action: String) {
        when (action) {
            "home" -> { if (current != 0) select(0); tabViews[0].requestFocus() }
            "movies", "series", "library", "apps", "search", "settings", "profiles" -> go(action)
            else -> ButtonMap.run(this, action)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Home goes to the Home page; pressing it twice quickly asks who is watching.
            val t = System.currentTimeMillis()
            // One press can arrive twice (from the system and from the fallback service); anything this close is the same press.
            if (t - lastHomeSeen < 300) return
            lastHomeSeen = t
            val double = ButtonMap.homeAction("double")
            if (double != null && t - lastHome < 700) { lastHome = 0; pendingHome?.let { handler.removeCallbacks(it) }; pendingHome = null; runHome(double); return }
            lastHome = t
            val single = ButtonMap.homeAction("single") ?: "home"
            // Going Home is harmless to repeat, so it happens at once. Any other job waits to see whether a second press makes it a double press.
            if (single == "home" || double == null) runHome(single)
            else Runnable { pendingHome = null; runHome(single) }.also { pendingHome = it; handler.postDelayed(it, 700) }
        }
        intent.getStringExtra("go")?.let { go(it) }
    }

    @Deprecated("Back walks toward the top; the launcher itself never closes.")
    override fun onBackPressed() {
        val f = currentFocus
        val inBar = f != null && (f.parent === tabViews[0].parent)
        if (inBar || current < 0) return   // On the tab bar, Back does nothing: this is the home screen.
        val strip = f?.parent as? androidx.recyclerview.widget.RecyclerView
        val pos = if (f != null) strip?.getChildAdapterPosition(f) ?: -1 else -1
        // Not on the first tile: go to the first tile of this row (or of this grid).
        if (strip != null && pos > 0) { focusItem(strip, 0); return }
        // On the first tile of a row: go to the first tile of the page, unless this already is it.
        var list: View? = strip?.parent as? View
        while (list != null && list !is RowsList) list = list.parent as? View
        if (list is RowsList) {
            val first = list.findViewHolderForAdapterPosition(0)?.itemView as? RowView
            if (first == null || strip !== first.strip) {
                list.scrollToPosition(0)
                afterLayout(list) { (list.findViewHolderForAdapterPosition(0)?.itemView as? RowView)?.strip?.let { focusItem(it, 0) } }
                return
            }
        }
        tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus()
    }

    /** Scrolls [rv] to [pos] and puts the focus on that item once it exists. */
    private fun focusItem(rv: androidx.recyclerview.widget.RecyclerView, pos: Int) {
        rv.setTag(R.id.tag_row_pos, pos)
        rv.scrollToPosition(pos)
        afterLayout(rv) { rv.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }
    }

    /** Runs [then] after [rv]'s next layout, when the item it scrolled to exists. */
    private fun afterLayout(rv: androidx.recyclerview.widget.RecyclerView, then: () -> Unit) {
        rv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) { rv.removeOnLayoutChangeListener(this); rv.post(then) }
        })
        rv.requestLayout()
    }

    /** Side bars: Left from the left edge of a page goes to the side bar (and opens it when hidden); Right, or Back, closes a hidden one. While moving an app, Left and Right move it. */
    /** Mapped buttons inside the launcher: the Only-in-My-Lists-TV rules, plus the Everywhere ones while the fallback service is not there to catch them. */
    private val launcherKeys = ButtonGestures({ ButtonMap.rules().filter { ButtonMap.active(it) && (it.scope == "launcher" || (it.scope == "all" && !HomeSetup.fallbackOn(this))) } }) { ButtonMap.run(this, it) }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (launcherKeys.onKey(e)) return true
        if (e.action == KeyEvent.ACTION_DOWN) {
            val f = currentFocus
            if (moving && f != null && moveApp(e, f)) return true
            // Up from a page onto the top bar lands on the tab you are on, not on whichever tab lies above the card.
            if (navStyle == 0 && e.keyCode == KeyEvent.KEYCODE_DPAD_UP && f != null && isInContent(f) && ready) {
                val up = f.focusSearch(View.FOCUS_UP)
                if (up != null && isIn(up, topBar)) { tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus(); return true }
            }
            // Down from the top bar's pages goes to the first row's remembered card, as Up and Down do between rows.
            if (navStyle == 0 && e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f in tabViews && ready && (screen as? BrowseScreen)?.enter() == true) return true
            // With a side bar, Up from the top row goes to the bell, search and settings at the top right (the one nearest across), not into the side bar.
            if (navStyle != 0 && e.keyCode == KeyEvent.KEYCODE_DPAD_UP && f != null && isInContent(f) && ready) {
                val up = f.focusSearch(View.FOCUS_UP)
                if (up != null && !isInContent(up)) { nearestTopIcon(f).requestFocus(); return true }
            }
            if (navStyle != 0 && f != null) {
                if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (isInContent(f) && atLeftEdge(f)) { openSide(); return true }
                    // In the top bar, Left stops at its first button instead of dropping into a row or opening the side bar.
                    if (isIn(f, topBar) && f.focusSearch(View.FOCUS_LEFT).let { it == null || !isIn(it, topBar) }) return true
                }
                if (navStyle == 2 && side.visibility == View.VISIBLE && (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || e.keyCode == KeyEvent.KEYCODE_BACK)) { closeSide(); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    /** Of the three buttons at the top right, the one whose middle is nearest across to [from]. */
    private fun nearestTopIcon(from: View): View {
        fun midX(v: View): Int { val p = IntArray(2); v.getLocationOnScreen(p); return p[0] + v.width / 2 }
        val x = midX(from)
        return listOf((bellBtn as ViewGroup).getChildAt(0), settingsBtn).minByOrNull { Math.abs(midX(it) - x) }!!
    }

    private fun isIn(v: View, container: View): Boolean { var p: android.view.ViewParent? = v.parent; while (p != null) { if (p === container) return true; p = p.parent }; return false }
    private fun isInContent(v: View) = isIn(v, content)

    /** Is this card the first one in its strip, or in the first column of its grid? */
    private fun atLeftEdge(f: View): Boolean {
        val rv = f.parent as? androidx.recyclerview.widget.RecyclerView ?: return false
        val pos = rv.getChildAdapterPosition(f)
        return when (val lm = rv.layoutManager) { is androidx.recyclerview.widget.GridLayoutManager -> pos % lm.spanCount == 0; else -> pos == 0 }
    }

    private fun openSide() { side.visibility = View.VISIBLE; tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus() }
    private fun closeSide() { side.visibility = View.GONE; content.requestFocus() }

    /** Moving an app: Left and Right swap it with its neighbour and save the order; OK or Back puts it down. Returns true when the key was used. */
    private fun moveApp(e: KeyEvent, f: View): Boolean {
        val rv = f.parent as? androidx.recyclerview.widget.RecyclerView
        val adapter = rv?.adapter as? CardAdapter
        if (rv == null || adapter == null) { moving = false; return false }
        val pos = rv.getChildAdapterPosition(f)
        return when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val to = pos + if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                if (to in 0 until (rv.adapter?.itemCount ?: 0)) {
                    adapter.move(pos, to)
                    Prefs.appOrder = adapter.payloads().mapNotNull { (it as? AppEntry)?.component?.packageName }
                    rv.post { rv.findViewHolderForAdapterPosition(to)?.itemView?.requestFocus() }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> { moving = false; Pop.toast(this, "Placed"); true }
            else -> { moving = false; false }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) { go("search"); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { (screen as? SearchScreen)?.stop(); super.onDestroy() }

    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_UI_HIDDEN) ImageLoader.trim() }
}

/** A tab label whose icon follows the text colour. */
private class TabText(c: android.content.Context) : TextView(c) {
    var label = ""
    /** The side bar is a slim strip of icons until it opens; then each icon gets its name. */
    fun showLabel(on: Boolean, gap: Int) { text = if (on) label else ""; compoundDrawablePadding = if (on) gap else 0; gravity = if (on) Gravity.CENTER_VERTICAL else Gravity.CENTER }
    override fun setTextColor(color: Int) { super.setTextColor(color); compoundDrawableTintList = android.content.res.ColorStateList.valueOf(color) }
}
