package tv.myliststv.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/** The stream picker: every source from every stream addon, best first. OK plays the one you choose. */
class StreamsActivity : Activity() {
    private lateinit var meta: Meta
    private lateinit var videoId: String
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var session: StreamResolver.Session? = null
    private var latest: List<StreamItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meta = Json.metaFromJson(intent.getStringExtra("meta") ?: "") ?: run { finish(); return }
        videoId = intent.getStringExtra("videoId") ?: meta.id
        list = Ui.vbox(this)
        status = Ui.text(this, "Finding sources…", 14f, Ui.DIM)
        val root = Ui.vbox(this).apply { setBackgroundColor(Ui.BG); setPadding(Ui.dp(this@StreamsActivity, 48), Ui.dp(this@StreamsActivity, 24), Ui.dp(this@StreamsActivity, 48), Ui.dp(this@StreamsActivity, 12)) }
        val v = meta.videos.firstOrNull { it.id == videoId }
        root.addView(Ui.text(this, "Choose a source", 26f, Ui.TEXT, true))
        root.addView(Ui.text(this, meta.name + (v?.let { "  ·  S${it.season} E${it.episode}" } ?: ""), 14f, Ui.DIM, false, 1))
        root.addView(status, Ui.wrap().apply { topMargin = Ui.dp(this@StreamsActivity, 6); bottomMargin = Ui.dp(this@StreamsActivity, 10) })
        val scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val s = StreamResolver.Session(
            onUpdate = { l, st -> latest = l; show(l, st, false) },
            onDone = { l, st -> latest = l; show(l, st, true) },
        )
        session = s
        StreamResolver.resolve(meta.type, videoId, StreamResolver.DEFAULT_TIMEOUT_MS, s)
    }

    private fun show(streams: List<StreamItem>, st: List<StreamResolver.AddonStatus>, done: Boolean) {
        val max = Prefs.effectiveMaxResolution()
        val ranked = StreamRank.sorted(streams, max)
        val ok = st.count { it.ok }
        status.text = (if (done) "" else "Still looking…  ") + "${ranked.size} sources from $ok of ${st.size} addons" +
            st.filter { !it.ok }.joinToString("") { "   · ${it.name} did not answer" }
        if (done && ranked.isEmpty()) status.text = if (StreamResolver.providers(meta.type, videoId).isEmpty()) "No stream addon is installed. Add one in the Addons tab." else "No playable sources found."
        val focusedIdx = (0 until list.childCount).firstOrNull { list.getChildAt(it).hasFocus() } ?: -1
        list.removeAllViews()
        ranked.forEachIndexed { i, s ->
            val r = row(s, i == 0 && ranked.isNotEmpty(), s.resolution > max && max > 0)
            r.setOnClickListener { choose(ranked, s) }
            list.addView(r, Ui.matchWrap().apply { bottomMargin = Ui.dp(this@StreamsActivity, 6) })
        }
        if (focusedIdx >= 0 && focusedIdx < list.childCount) list.getChildAt(focusedIdx).requestFocus()
        else if (list.childCount > 0 && !list.hasFocus()) list.getChildAt(0).requestFocus()
    }

    private fun row(s: StreamItem, best: Boolean, tooHigh: Boolean): View {
        val c = this
        val r = Ui.hbox(c, 14).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10))
            Ui.rowStyle(c, this); isFocusable = true; isClickable = true
        }
        r.addView(Ui.text(c, if (s.resolution > 0) "${s.resolution}p" else "?", 20f, Ui.TEXT, true), LinearLayout.LayoutParams(Ui.dp(c, 70), -2))
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, s.name.replace("\n", " ").ifEmpty { s.title }, 14f, Ui.TEXT, true, 1))
        mid.addView(Ui.text(c, s.title.replace("\n", "  ").ifEmpty { s.filename ?: "" }, 12f, Ui.DIM, false, 2))
        mid.addView(Ui.text(c, s.addonName + if (s.sizeBytes > 0) "  ·  " + size(s.sizeBytes) else "", 12f, Ui.DIM, false, 1))
        r.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        val tags = ArrayList<String>()
        if (best) tags += "Best match"; if (s.cached) tags += "Cached"; if (s.hdr) tags += "HDR"; if (tooHigh) tags += "May not play on this box"
        if (tags.isNotEmpty()) r.addView(Ui.text(c, tags.joinToString("  "), 12f, if (tooHigh) Ui.BAD else Ui.GOOD, true))
        return r
    }

    private fun size(b: Long): String = if (b >= 1_073_741_824L) "%.1f GB".format(b / 1_073_741_824.0) else "%d MB".format(b / 1_048_576)

    private fun choose(ranked: List<StreamItem>, s: StreamItem) {
        val v = meta.videos.firstOrNull { it.id == videoId }
        val resume = WatchStore.forMeta(meta.id)
        PlaySession.apply {
            live = null
            this.meta = this@StreamsActivity.meta; this.videoId = this@StreamsActivity.videoId
            season = v?.season ?: -1; episode = v?.episode ?: -1; episodeTitle = v?.title
            this.ranked = ranked; current = s; autoSelected = false
            resumeMs = if (resume != null && resume.videoId == videoId && resume.fraction in 0.02f..0.93f) resume.positionMs else 0L
        }
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }

    override fun onDestroy() { session?.cancelled = true; super.onDestroy() }
}
