package tv.myliststv.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Settings as one table: each option is a label, a current value, and a function that moves to the next value.
 * OK cycles the value. Everything is stored in [Prefs].
 */
class SettingsActivity : Activity() {
    private class Opt(val label: String, val desc: String, val value: () -> String, val next: () -> Unit)

    private fun <T> cycle(options: List<T>, get: () -> T, set: (T) -> Unit): () -> Unit = {
        set(options[(options.indexOf(get()).coerceAtLeast(-1) + 1) % options.size])
    }

    private val simple get() = listOf(
        Opt("Auto-pick best stream", "Play starts at once. Off shows the source list first.", { onOff(Prefs.autoPick) }, { Prefs.autoPick = !Prefs.autoPick }),
        Opt("Maximum resolution", "Auto keeps weak boxes at 1080p.", { Prefs.maxResolution.let { if (it == 0) "Auto" else "${it}p" } },
            cycle(listOf(0, 2160, 1080, 720), { Prefs.maxResolution }, { Prefs.maxResolution = it })),
        Opt("Subtitles", "Preferred language.", { Prefs.subtitleLang.let { if (it == "off") "Off" else it.uppercase() } },
            cycle(listOf("en", "es", "fr", "de", "off"), { Prefs.subtitleLang }, { Prefs.subtitleLang = it })),
        Opt("Play next episode automatically", "", { onOff(Prefs.autoplayNext) }, { Prefs.autoplayNext = !Prefs.autoplayNext }),
        Opt("Show Continue watching", "", { onOff(Prefs.showContinue) }, { Prefs.showContinue = !Prefs.showContinue }),
        Opt("Show apps row on Home", "", { onOff(Prefs.showAppsRow) }, { Prefs.showAppsRow = !Prefs.showAppsRow }),
        Opt("Lite mode", "Less animation and memory. Auto turns on for weak boxes.", { Prefs.liteMode },
            cycle(listOf("Auto", "On", "Off"), { Prefs.liteMode }, { Prefs.liteMode = it })),
    )

    private val advanced get() = listOf(
        Opt("Try the next source if one fails", "Switches by itself when a source does not start.", { onOff(Prefs.fallbackOn) }, { Prefs.fallbackOn = !Prefs.fallbackOn }),
        Opt("Source tries", "How many other sources to try.", { "${Prefs.fallbackTries}" }, cycle(listOf(1, 2, 3, 5), { Prefs.fallbackTries }, { Prefs.fallbackTries = it })),
        Opt("Source start timeout", "Seconds to wait for the first picture.", { "${Prefs.startupTimeoutSec} s" }, cycle(listOf(6, 10, 15), { Prefs.startupTimeoutSec }, { Prefs.startupTimeoutSec = it })),
    )

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 80), Ui.dp(this@SettingsActivity, 24), Ui.dp(this@SettingsActivity, 80), Ui.dp(this@SettingsActivity, 24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(col) })
        build(col)
    }

    private fun build(col: LinearLayout) {
        col.removeAllViews()
        col.addView(Ui.text(this, "Settings", 28f, Ui.TEXT, true))
        val mode = Opt("Mode", "Simple hides settings most people never need.", { if (Prefs.advanced) "Advanced" else "Simple" }, { Prefs.advanced = !Prefs.advanced })
        (listOf(mode) + simple + if (Prefs.advanced) advanced else emptyList()).forEach { o ->
            col.addView(row(o) { o.next(); val i = focusedIndex(col); build(col); col.getChildAt(i)?.requestFocus() }, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        }
        col.addView(row(Opt("Check for updates", "Version 0.1.${Updater.installedBuild(this)} installed.", { "OK" }, { checkUpdate() })) { it.next() },
            Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        col.addView(row(Opt("Clear caches", "Frees stored images and catalog data (${(Http.cacheBytes() / 1_048_576)} MB now).", { "OK" }, { Http.clear(); ImageLoader.clearDisk(); Toast.makeText(this, "Caches cleared", Toast.LENGTH_SHORT).show() })) { it.next() },
            Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        col.addView(Ui.text(this, "To make this your home screen, run on a computer:\nadb shell cmd package set-home-activity tv.myliststv.launcher/.MainActivity\n\nMy Lists TV 0.1 · no analytics, no tracking", 12f, Ui.DIM),
            Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 20) })
        col.post { if (col.hasFocus().not()) col.getChildAt(1)?.requestFocus() }
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

    private fun focusedIndex(col: LinearLayout) = (0 until col.childCount).firstOrNull { col.getChildAt(it).hasFocus() } ?: 1

    private fun row(o: Opt, onClick: (Opt) -> Unit): LinearLayout = Ui.hbox(this).apply {
        setPadding(Ui.dp(this@SettingsActivity, 18), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 18), Ui.dp(this@SettingsActivity, 12))
        setBackgroundColor(Ui.CARD); foreground = Ui.focusBg(this@SettingsActivity, 8, 0x14FFFFFF); isFocusable = true; isClickable = true
        val mid = Ui.vbox(this@SettingsActivity)
        mid.addView(Ui.text(this@SettingsActivity, o.label, 16f, Ui.TEXT, true))
        if (o.desc.isNotEmpty()) mid.addView(Ui.text(this@SettingsActivity, o.desc, 12f, Ui.DIM, false, 2))
        addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Ui.text(this@SettingsActivity, o.value(), 15f, Ui.ACCENT, true).apply { gravity = Gravity.END })
        setOnClickListener { onClick(o) }
    }
}
