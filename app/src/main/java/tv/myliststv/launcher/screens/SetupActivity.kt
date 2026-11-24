package tv.myliststv.launcher.screens

import tv.myliststv.launcher.services.*

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import tv.myliststv.launcher.data.AddonStore
import tv.myliststv.launcher.data.HomeSetup
import tv.myliststv.launcher.data.Prefs
import tv.myliststv.launcher.ui.Ui

/**
 * First run: one full screen per step (Home app, Home button, notifications, addons). Every step can be skipped and
 * all of it can be done later in Settings. What is already on is read from the system each second, so a step
 * disappears by itself once it is done, also after a trip to Android's settings or the phone steps.
 */
class SetupActivity : Activity() {
    private enum class Step(val label: String, val title: String, val body: String) {
        HOME("Home app", "Make My Lists TV your Home app",
            "Then the Home button on your remote brings you here. Pick My Lists TV in the list that opens and choose Always."),
        BUTTON("Home button", "Turn on the Home button",
            "Some TVs keep their own screen behind the Home button. This accessibility switch lets My Lists TV notice that and come forward instead. It only sees which app opened."),
        NOTICES("Notifications", "Show your TV's notifications",
            "Your TV's notifications appear in the bell at the top right of Home. Find My Lists TV in the list and turn it on."),
        ADDONS("Addons", "Add a stream addon",
            "My Lists TV already shows the default catalogs. To play video, add a stream addon. You can paste its link from your phone.")
    }

    private val skipped = mutableSetOf<Step>()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private var shown = ""
    private val poll = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        setContentView(root)
        render()
    }

    override fun onResume() { super.onResume(); render(); handler.postDelayed(poll, 1000) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }

    /** Back leaves the setup (the same as skipping all of it). */
    @Deprecated("Back key") override fun onBackPressed() = finishSetup()

    private fun finishSetup() { Prefs.setupDone = true; finish() }

    private fun done(s: Step) = when (s) {
        Step.HOME -> HomeSetup.isHome(this)
        Step.BUTTON -> HomeSetup.fallbackOn(this)
        Step.NOTICES -> Notices.allowed(this)
        Step.ADDONS -> AddonStore.saved().any { it.url != AddonStore.DEFAULT_URL }
    }

    /** Draws the page again, but only when something changed, so the focus is not taken away each second. */
    private fun render() {
        val steps = Step.values()
        val current = steps.firstOrNull { !done(it) && it !in skipped }
        val signature = steps.joinToString("") { (if (done(it)) "d" else if (it in skipped) "s" else "o") } + current
        if (signature == shown) return
        shown = signature
        root.removeAllViews()

        val page = Ui.hbox(this, 48).apply { setPadding(Ui.dp(this@SetupActivity, 64), Ui.dp(this@SetupActivity, 40), Ui.dp(this@SetupActivity, 64), Ui.dp(this@SetupActivity, 40)) }
        val left = Ui.vbox(this).apply { gravity = Gravity.CENTER_VERTICAL }
        left.addView(android.widget.ImageView(this).apply { setImageResource(tv.myliststv.launcher.R.drawable.ic_launcher) }, LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)).apply { bottomMargin = Ui.dp(this@SetupActivity, 18) })
        left.addView(Ui.text(this, "Set up My Lists TV", 34f, Ui.TEXT, true))
        left.addView(Ui.text(this, "Every step is optional. You can do any of them later in Settings.", 14f, Ui.DIM).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 8) })
        steps.forEach { s ->
            val mark = when { done(s) -> "✓"; s in skipped -> "–"; s == current -> "●"; else -> "○" }
            val on = done(s) || s == current
            val row = Ui.hbox(this, 8)
            row.addView(Ui.text(this, mark, 16f, if (done(s)) Ui.GOOD else if (on) Ui.TEXT else Ui.DIM, true), LinearLayout.LayoutParams(Ui.dp(this, 24), -2))
            row.addView(Ui.text(this, s.label, 16f, if (on) Ui.TEXT else Ui.DIM, s == current))
            left.addView(row, Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 14) })
        }
        page.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        val panel = Ui.vbox(this).apply {
            background = Ui.rounded(this@SetupActivity, Ui.CARD, Ui.R_PANEL); gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@SetupActivity, 32), Ui.dp(this@SetupActivity, 28), Ui.dp(this@SetupActivity, 32), Ui.dp(this@SetupActivity, 28))
        }
        var first: android.view.View? = null
        fun add(label: String, primary: Boolean = false, run: () -> Unit) {
            val b = Ui.button(this, label, primary, run)
            panel.addView(b, Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 12) })
            if (first == null) first = b
        }
        if (current != null) {
            panel.addView(Ui.text(this, "Step ${current.ordinal + 1} of ${steps.size}", 13f, Ui.DIM))
            panel.addView(Ui.text(this, current.title, 24f, Ui.TEXT, true), Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 8) })
            panel.addView(Ui.text(this, current.body, 16f, Ui.DIM).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 12) })
            panel.addView(android.view.View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 12)))
            when (current) {
                Step.HOME -> add("Make My Lists TV my Home app", true) { HomeSetup.requestHome(this) }
                Step.BUTTON -> add("Turn on the Home button", true) {
                    if (HomeSetup.enableFallback(this)) { shown = ""; render() }
                    else try { tv.myliststv.launcher.ui.Pop.toast(this, "Turn on My Lists TV Home button", true); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
                }
                Step.NOTICES -> add("Open notification settings", true) {
                    try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { tv.myliststv.launcher.ui.Pop.toast(this, "This TV has no such setting. Use the phone or computer steps.", true) }
                }
                Step.ADDONS -> add("Add an addon", true) { startActivity(Intent(this, SettingsActivity::class.java).putExtra("open", "addons")) }
            }
            // TVs that block these switches on the TV itself can be set up from a phone or computer.
            if (current != Step.ADDONS) add("Set up from a phone or computer") { HomeSetup.phoneSteps(this) }
            add("Skip this step") { skipped += current; render() }
        } else {
            val all = steps.all { done(it) }
            panel.addView(Ui.text(this, if (all) "You are all set" else "Setup finished", 24f, Ui.TEXT, true))
            panel.addView(Ui.text(this, if (all) "My Lists TV is ready." else "The steps you skipped are in Settings, under Default Launcher and Addons, whenever you want them.", 16f, Ui.DIM).apply { setLineSpacing(0f, 1.15f) },
                Ui.matchWrap().apply { topMargin = Ui.dp(this@SetupActivity, 12) })
            add("Continue", true) { finishSetup() }
        }
        page.addView(panel, LinearLayout.LayoutParams(Ui.dp(this, 420), -1))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        first?.let { f -> root.post { f.requestFocus() } }
    }
}
