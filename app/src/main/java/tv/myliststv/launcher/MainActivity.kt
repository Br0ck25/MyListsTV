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
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
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

    private val tabs = listOf("Home", "Movies", "Series", "Live", "Apps", "Addons")
    private val tabViews = ArrayList<TextView>()
    private lateinit var content: FrameLayout
    private lateinit var searchBtn: TextView
    private lateinit var settingsBtn: TextView
    private var current = -1
    private var screen: Any? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSelect: Runnable? = null
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.vbox(this).apply { setBackgroundColor(Ui.BG) }
        val bar = Ui.hbox(this, 8).apply { setPadding(Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 8)) }
        val logo = Ui.hbox(this, 8)
        logo.addView(View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Ui.ACCENT) } },
            LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)))
        logo.addView(Ui.text(this, "My Lists TV", 20f, Ui.TEXT, true))
        bar.addView(logo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ui.dp(this@MainActivity, 24) })
        tabs.forEachIndexed { i, t ->
            val v = tabView(t)
            v.setOnFocusChangeListener { view, f ->
                (view as TextView).setTextColor(if (f) 0xFF12131A.toInt() else if (i == current) Ui.TEXT else Ui.DIM)
                if (f) { pendingSelect?.let { handler.removeCallbacks(it) }; pendingSelect = Runnable { select(i) }.also { handler.postDelayed(it, 140) } }
            }
            v.setOnClickListener { select(i, force = true) }
            tabViews += v
            bar.addView(v)
        }
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        searchBtn = Ui.button(this, "Search") { startActivity(Intent(this, SearchActivity::class.java)) }
        settingsBtn = Ui.button(this, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        bar.addView(searchBtn); bar.addView(settingsBtn)
        root.addView(bar, Ui.matchWrap())
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        content.addView(Ui.text(this, "Loading…", 16f, Ui.DIM).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        val safe = CrashGuard.safeMode(this)
        val start = { ready = true; val t = if (safe) 4 else 0; select(t, force = true); tabViews[t].requestFocus(); afterStart(safe) }
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
            .setMessage("You are seeing the default My Lists catalogs. To play video, add a stream addon in the Addons tab; you can paste its link from your phone. Simple mode hides settings most people never need.")
            .setPositiveButton("Set up addons") { _, _ -> Prefs.setupDone = true; select(5, force = true); tabViews[5].requestFocus() }
            .setNeutralButton("Advanced mode") { _, _ -> Prefs.setupDone = true; Prefs.advanced = true }
            .setNegativeButton("Skip") { _, _ -> Prefs.setupDone = true }.show()
        else if (Profiles.all().size > 1 && !Profiles.askedThisRun) {
            Profiles.askedThisRun = true
            val l = Profiles.all()
            AlertDialog.Builder(this).setTitle("Who is watching?").setCancelable(false)
                .setItems(l.map { it.name + if (it.kids) " (kids)" else "" }.toTypedArray()) { _, i -> Profiles.activeId = l[i].id; WatchStore.init(this); select(0, force = true) }.show()
        }
        Profiles.askedThisRun = true
    }

    private fun tabView(t: String) = TextView(this).apply {
        text = t; Ui.size(this, 15f); setTextColor(Ui.DIM); typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8))
        background = Ui.pillBg(this@MainActivity, 0x00000000); isFocusable = true; isClickable = true
    }

    private fun select(i: Int, force: Boolean = false) {
        if (!ready) return
        if (i == current && !force) return
        current = i
        tabViews.forEachIndexed { idx, v -> if (!v.isFocused) v.setTextColor(if (idx == i) Ui.TEXT else Ui.DIM) }
        (screen as? AddonsScreen)?.stop()
        content.removeAllViews()
        screen = null
        when (i) {
            0, 1, 2 -> {
                val s = BrowseScreen(this, type = when (i) { 1 -> "movie"; 2 -> "series"; else -> null }, withContinue = i == 0, withApps = i == 0,
                    onMeta = ::openMeta, onResume = ::resumeEntry, onApp = ::launch, onAppMenu = ::appMenu)
                screen = s; content.addView(s.view); s.start()
            }
            3 -> { val s = LiveScreen(this); screen = s; content.addView(s.view); s.start() }
            4 -> { val s = AppsScreen(this, ::launch, ::appMenu); screen = s; content.addView(s.view); s.start() }
            5 -> { val s = AddonsScreen(this) { }; screen = s; content.addView(s.view); s.start() }
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
        AlertDialog.Builder(this).setTitle(a.label).setItems(arrayOf("App info", "Uninstall")) { _, which ->
            val pkg = a.component.packageName
            try {
                if (which == 0) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                else startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            } catch (e: Exception) { Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show() }
        }.show()
    }

    override fun onResume() {
        super.onResume()
        (screen as? BrowseScreen)?.refreshContinue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (current != 0) select(0, force = true)
            tabViews[0].requestFocus()
        }
    }

    @Deprecated("Back moves to the tabs; the launcher itself never closes.")
    override fun onBackPressed() {
        val f = currentFocus
        val inBar = f != null && (f.parent === tabViews[0].parent)
        if (!inBar && current >= 0) tabViews[current.coerceIn(0, tabViews.size - 1)].requestFocus()
        // On the tab bar, Back does nothing: this is the home screen.
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) { startActivity(Intent(this, SearchActivity::class.java)); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { (screen as? AddonsScreen)?.stop(); super.onDestroy() }

    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_UI_HIDDEN) ImageLoader.trim() }
}
