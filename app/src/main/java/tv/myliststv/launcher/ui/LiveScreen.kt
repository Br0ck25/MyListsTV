package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.PlayerActivity
import tv.myliststv.launcher.data.*

/**
 * Live TV as a programme guide: groups across the top, a time axis, and one row per channel with what is on now and next
 * laid out by length. The window is three hours from the last half hour. OK on any cell tunes in. Up and down change channel while watching.
 */
class LiveScreen(private val act: Activity) {
    private val note = Ui.text(act, "", 14f, Ui.DIM, false, 3)
    private val groupBar = Ui.hbox(act, 8)
    private val axis = Ui.hbox(act, 0)
    private val rv = RecyclerView(act)
    private var all: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var group: String? = null
    private var windowStart = 0L
    private val perMin = Ui.dp(act, 1) * 3.4f
    private val nameCol = Ui.dp(act, 190)

    val view: View = Ui.vbox(act).also {
        note.setPadding(Ui.dp(act, 48), Ui.dp(act, 10), Ui.dp(act, 48), Ui.dp(act, 4))
        it.addView(note, Ui.matchWrap())
        it.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; setPadding(Ui.dp(act, 42), 0, Ui.dp(act, 42), 0); addView(groupBar) }, Ui.matchWrap())
        axis.setPadding(Ui.dp(act, 42) + nameCol + Ui.dp(act, 8), Ui.dp(act, 6), 0, Ui.dp(act, 2))
        it.addView(axis, Ui.matchWrap())
        rv.layoutManager = LinearLayoutManager(act); rv.itemAnimator = null; rv.clipToPadding = false
        rv.setPadding(Ui.dp(act, 42), 0, Ui.dp(act, 42), Ui.dp(act, 56)); rv.setItemViewCacheSize(6)
        rv.adapter = A()
        it.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun start() {
        note.text = if (LiveStore.playlists().isEmpty()) "No IPTV playlist yet. In the Addons tab choose “Add an addon or playlist” and paste an M3U link." else "Loading channels…"
        LiveStore.load { channels, guides ->
            show(channels)
            Epg.load(guides, channels.mapNotNull { it.tvgId }.toSet()) { rv.adapter?.notifyDataSetChanged() }
        }
    }

    fun show(channels: List<Channel>) {
        all = channels
        if (channels.isNotEmpty()) note.text = "${channels.size} channels. Up and down change channel while watching."
        buildAxis(); buildGroups(); filter()
    }

    private fun buildAxis() {
        val t = System.currentTimeMillis()
        windowStart = t - t % (30 * 60_000L)
        axis.removeAllViews()
        for (i in 0 until 6) axis.addView(Ui.text(act, android.text.format.DateFormat.format("h:mm a", windowStart + i * 30 * 60_000L), 12f, Ui.DIM), LinearLayout.LayoutParams((30 * perMin).toInt(), -2))
    }

    private fun buildGroups() {
        groupBar.removeAllViews()
        val groups = listOf<String?>(null) + all.map { it.group }.distinct().take(14)
        groups.forEach { g ->
            lateinit var b: View
            b = Ui.button(act, g ?: "All") { group = g; filter(); for (k in 0 until groupBar.childCount) groupBar.getChildAt(k).isSelected = groupBar.getChildAt(k) === b }.also { it.isSelected = g == group }
            groupBar.addView(b, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun filter() { shown = all.filter { group == null || it.group == group }; rv.adapter?.notifyDataSetChanged() }

    private fun play(c: Channel) {
        PlaySession.live = shown
        PlaySession.liveIndex = shown.indexOf(c).coerceAtLeast(0)
        PlaySession.meta = Meta("live", "tv", c.name)
        PlaySession.current = StreamItem("IPTV", "iptv", c.url, c.name, c.group, null, null, 0, 0, false, false, null)
        PlaySession.autoSelected = false; PlaySession.resumeMs = 0; PlaySession.ranked = emptyList(); PlaySession.videoId = "live"; PlaySession.season = -1
        act.startActivity(Intent(act, PlayerActivity::class.java))
    }

    private fun cell(text: String, sub: String, w: Int, now: Boolean, c: Channel): View = Ui.vbox(act).apply {
        val p = Ui.dp(act, 8); setPadding(p, Ui.dp(act, 6), p, Ui.dp(act, 6)); gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded(act, if (now) Ui.ELEVATED else Ui.CARD, Ui.R_CARD); foreground = Ui.focusBg(act, Ui.R_CARD, Ui.CLEAR)
        isFocusable = true; isClickable = true; setOnClickListener { play(c) }
        addView(Ui.text(act, text, 13f, Ui.TEXT, true, 1)); addView(Ui.text(act, sub, 11f, Ui.DIM, false, 1))
        layoutParams = LinearLayout.LayoutParams(w, Ui.dp(act, 56)).apply { rightMargin = Ui.dp(act, 4) }
    }

    private inner class A : RecyclerView.Adapter<A.H>() {
        inner class H(val row: LinearLayout) : RecyclerView.ViewHolder(row)
        override fun getItemCount() = shown.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = H(Ui.hbox(act, 0).apply {
            gravity = Gravity.CENTER_VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, Ui.dp(act, 64))
        })
        override fun onBindViewHolder(h: H, position: Int) {
            val c = shown[position]; val row = h.row
            row.removeAllViews()
            val name = Ui.hbox(act, 8).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(act, 8), 0, Ui.dp(act, 8), 0)
                background = Ui.rounded(act, Ui.CARD, Ui.R_CARD); foreground = Ui.focusBg(act, Ui.R_CARD, Ui.CLEAR)
                isFocusable = true; isClickable = true; setOnClickListener { play(c) }
            }
            val logo = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            name.addView(logo, LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 36)))
            name.addView(Ui.text(act, c.name, 13f, Ui.TEXT, true, 2), LinearLayout.LayoutParams(0, -2, 1f))
            ImageLoader.load(logo, c.logo, Ui.dp(act, 36), Ui.dp(act, 36))
            row.addView(name, LinearLayout.LayoutParams(nameCol, Ui.dp(act, 56)).apply { rightMargin = Ui.dp(act, 8) })
            val end = windowStart + 180 * 60_000L; val t = System.currentTimeMillis()
            val progs = Epg.schedule(c.tvgId).filter { it.stop > windowStart && it.start < end }
            if (progs.isEmpty()) { row.addView(cell("No guide for this channel", "OK to watch", (180 * perMin).toInt(), false, c)); return }
            var cursor = windowStart
            for (p in progs) {
                val s = maxOf(p.start, cursor); val e = minOf(p.stop, end)
                if (e <= s) continue
                if (s > cursor) row.addView(View(act), LinearLayout.LayoutParams(((s - cursor) / 60_000f * perMin).toInt(), 1))
                val fmt = { ms: Long -> android.text.format.DateFormat.format("h:mm", ms).toString() }
                row.addView(cell(p.title, "${fmt(p.start)} - ${fmt(p.stop)}", maxOf(((e - s) / 60_000f * perMin).toInt() - Ui.dp(act, 4), Ui.dp(act, 60)), p.start <= t && t < p.stop, c))
                cursor = e
            }
        }
    }
}
