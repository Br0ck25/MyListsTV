package tv.myliststv.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tv.myliststv.launcher.data.Addon
import tv.myliststv.launcher.data.AddonStore

/** Installed addons: what each provides, on or off, order, remove, and a way to add one. */
class AddonsScreen(private val act: Activity, private val onChanged: () -> Unit) {
    private val scroll = ScrollView(act)
    private val col = Ui.vbox(act)
    val view: View = scroll
    private var focusIndex = 0
    private val phone = tv.myliststv.launcher.data.PhoneServer { link, reply -> tv.myliststv.launcher.data.Links.add(link) { reply(it); reload() } }
    fun stop() = phone.stop()

    init {
        scroll.isFillViewport = true
        col.setPadding(Ui.dp(act, 48), Ui.dp(act, 12), Ui.dp(act, 48), Ui.dp(act, 24))
        scroll.addView(col)
    }

    fun start() { phone.start(); rebuild() }

    private fun rebuild() {
        col.removeAllViews()
        val c = act
        col.addView(Ui.text(c, "Addons", 24f, Ui.TEXT, true))
        col.addView(Ui.text(c, "Catalog addons fill your rows. Stream addons provide the video. Order matters: earlier stream addons win ties.", 13f, Ui.DIM, false, 2),
            Ui.matchWrap().apply { topMargin = Ui.dp(c, 4); bottomMargin = Ui.dp(c, 12) })
        val add = row("Add an addon or playlist", phone.address()?.let { "From your phone, open $it and paste a link or a recovery code. Or press OK to type it here." } ?: "Press OK to type a link.", "OK") { askUrl() }
        col.addView(add, Ui.matchWrap().apply { bottomMargin = Ui.dp(c, 8) })
        val saved = AddonStore.saved()
        val loaded = AddonStore.loaded.associateBy { it.manifestUrl }
        saved.forEachIndexed { i, s ->
            val a = loaded[s.url]
            val title = a?.name ?: "Not loading"
            val provides = a?.let { provides(it) } ?: "Could not read this addon right now"
            val host = s.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
            val r = row(title + if (a != null && a.manifest.version.isNotEmpty()) "  v${a.manifest.version}" else "", "$host · $provides", if (s.enabled) "On" else "Off") {
                focusIndex = i + 1; AddonStore.setEnabled(s.url, !s.enabled); reload()
            }
            r.alpha = if (s.enabled) 1f else 0.6f
            r.setOnLongClickListener { focusIndex = i + 1; menu(s.url, a?.name ?: host); true }
            col.addView(r, Ui.matchWrap().apply { bottomMargin = Ui.dp(c, 8) })
        }
        col.addView(Ui.text(c, "Hold OK on an addon for move up, move down and remove.", 12f, Ui.DIM), Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
        col.post {
            val idx = focusIndex.coerceIn(0, (0 until col.childCount).count { col.getChildAt(it).isFocusable } - 1)
            val focusables = (0 until col.childCount).map { col.getChildAt(it) }.filter { it.isFocusable }
            if (focusIndex > 0 && focusables.isNotEmpty()) focusables[idx].requestFocus()
        }
    }

    private fun provides(a: Addon): String {
        val p = ArrayList<String>()
        if (a.manifest.catalogs.any { !it.searchRequired }) p += "Catalogs"
        if (a.manifest.catalogs.any { it.supportsSearch }) p += "Search"
        if (a.manifest.provides("meta")) p += "Details"
        if (a.manifest.provides("stream")) p += "Streams"
        if (a.manifest.provides("subtitles")) p += "Subtitles"
        return p.joinToString(", ").ifEmpty { "Nothing we use" }
    }

    private fun row(title: String, sub: String, trailing: String, onClick: () -> Unit): LinearLayout {
        val c = act
        val r = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 12))
            setBackgroundColor(Ui.CARD); foreground = Ui.focusBg(c, 8, 0x14FFFFFF)
            isFocusable = true; isClickable = true; setOnClickListener { onClick() }
        }
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, title, 16f, Ui.TEXT, true, 1))
        mid.addView(Ui.text(c, sub, 12f, Ui.DIM, false, 1))
        r.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(Ui.text(c, trailing, 14f, if (trailing == "On") Ui.GOOD else if (trailing == "Off") Ui.DIM else Ui.TEXT, true))
        return r
    }

    private fun menu(url: String, name: String) {
        AlertDialog.Builder(act).setTitle(name)
            .setItems(arrayOf("Move up", "Move down", "Remove")) { _, which ->
                when (which) {
                    0 -> { AddonStore.move(url, -1); focusIndex = (focusIndex - 1).coerceAtLeast(1) }
                    1 -> { AddonStore.move(url, 1); focusIndex += 1 }
                    2 -> confirmRemove(url, name)
                }
                if (which != 2) reload()
            }.show()
    }

    private fun confirmRemove(url: String, name: String) {
        AlertDialog.Builder(act).setTitle("Remove $name?").setMessage("Your other settings stay as they are.")
            .setPositiveButton("Remove") { _, _ -> AddonStore.remove(url); focusIndex = 0; reload() }
            .setNegativeButton("Keep", null).show()
    }

    private fun askUrl() {
        val input = EditText(act).apply {
            hint = "Addon link or IPTV playlist link"; inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
            setSingleLine(true); setTextColor(0xFF000000.toInt())
        }
        AlertDialog.Builder(act).setTitle("Add an addon or playlist").setView(input)
            .setPositiveButton("Install") { _, _ -> install(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun install(raw: String) {
        Toast.makeText(act, "Checking\u2026", Toast.LENGTH_SHORT).show()
        tv.myliststv.launcher.data.Links.add(raw) { msg -> Toast.makeText(act, msg, Toast.LENGTH_LONG).show(); focusIndex = AddonStore.saved().size; reload() }
    }

    private fun reload() { AddonStore.load { rebuild(); onChanged() } }
}
