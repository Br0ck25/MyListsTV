package tv.myliststv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Details for one title: art, summary, Play or Resume, Sources, and for series the seasons and episodes.
 * Play picks a stream by the user's rule and starts the player; Sources lets the viewer choose.
 */
class DetailsActivity : Activity() {

    private lateinit var meta: Meta
    private val seasonRow = Ui.hbox(this, 8)
    private lateinit var episodes: RecyclerView
    private lateinit var playBtn: TextView
    private lateinit var title: TextView
    private lateinit var metaLine: TextView
    private lateinit var desc: TextView
    private lateinit var bg: ImageView
    private lateinit var poster: ImageView
    private var season = -1
    private var busy: AlertDialog? = null
    private var session: StreamResolver.Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meta = Json.metaFromJson(intent.getStringExtra("meta") ?: "") ?: run { finish(); return }
        val frame = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.35f }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
        val body = Ui.vbox(this).apply { setPadding(Ui.dp(this@DetailsActivity, 48), Ui.dp(this@DetailsActivity, 28), Ui.dp(this@DetailsActivity, 48), Ui.dp(this@DetailsActivity, 12)) }
        val top = Ui.hbox(this, 20).apply { gravity = Gravity.TOP }
        poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Ui.CARD) }
        top.addView(poster, LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 180)))
        val info = Ui.vbox(this)
        title = Ui.text(this, meta.name, 30f, Ui.TEXT, true, 2)
        metaLine = Ui.text(this, "", 14f, Ui.ACCENT, true, 1)
        desc = Ui.text(this, "", 14f, 0xFFC3C8DC.toInt(), false, 5)
        info.addView(title); info.addView(metaLine, Ui.wrap().apply { topMargin = Ui.dp(this@DetailsActivity, 6) })
        info.addView(desc, Ui.matchWrap().apply { topMargin = Ui.dp(this@DetailsActivity, 8) })
        val buttons = Ui.hbox(this, 10)
        playBtn = Ui.button(this, "Play") { playMain() }
        buttons.addView(playBtn)
        buttons.addView(Ui.button(this, "Sources") { sourcesMain() })
        info.addView(buttons, Ui.wrap().apply { topMargin = Ui.dp(this@DetailsActivity, 14) })
        top.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(top, Ui.matchWrap())
        seasonRow.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4))
        body.addView(seasonRow, Ui.matchWrap())
        episodes = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            itemAnimator = null; visibility = View.GONE; clipToPadding = false
        }
        body.addView(episodes, LinearLayout.LayoutParams(-1, Ui.dp(this, 118)))
        frame.addView(body, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        render()
        Catalogs.meta(meta.type, meta.id, meta) { m -> if (m != null && !isFinishing) { meta = m; render() } }
        val auto = intent.getStringExtra("play")
        if (auto != null) frame.post { playVideo(auto) }
    }

    private fun render() {
        title.text = meta.name
        metaLine.text = listOfNotNull(meta.year, meta.runtime, meta.rating?.let { "★ $it" }, meta.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
        desc.text = meta.description ?: ""
        ImageLoader.load(bg, meta.background ?: meta.poster, 960, 540)
        ImageLoader.load(poster, meta.poster, Ui.dp(this, 120), Ui.dp(this, 180))
        val seasons = meta.videos.map { it.season }.distinct().filter { it > 0 }.ifEmpty { meta.videos.map { it.season }.distinct() }
        val resume = WatchStore.forMeta(meta.id)
        playBtn.text = if (resume != null && resume.fraction in 0.02f..0.93f) "Resume" else "Play"
        if (meta.type == "series" && seasons.isNotEmpty()) {
            if (season !in seasons) season = resume?.season?.takeIf { it in seasons } ?: seasons.first()
            seasonRow.removeAllViews()
            seasons.forEach { s ->
                seasonRow.addView(Ui.button(this, if (s == 0) "Specials" else "Season $s") { season = s; render() })
            }
            val list = meta.videos.filter { it.season == season }
            episodes.visibility = View.VISIBLE
            episodes.adapter = EpisodeAdapter(list, resume)
        } else { seasonRow.removeAllViews(); episodes.visibility = View.GONE }
    }

    /** The episode to play from the main button: where the viewer left off, else the first one. */
    private fun mainVideoId(): String {
        if (meta.type != "series") return meta.id
        val resume = WatchStore.forMeta(meta.id)
        if (resume != null && resume.videoId.isNotEmpty()) return resume.videoId
        return meta.videos.firstOrNull { it.season > 0 }?.id ?: meta.videos.firstOrNull()?.id ?: meta.id
    }

    private fun playMain() = playVideo(mainVideoId())
    private fun sourcesMain() = openSources(mainVideoId())

    private fun videoFor(id: String): Video? = meta.videos.firstOrNull { it.id == id }

    /** Finds streams, picks the best, and starts the player. With auto-pick off it opens the stream list instead. */
    private fun playVideo(videoId: String) {
        if (!Prefs.autoPick) { openSources(videoId); return }
        val v = videoFor(videoId)
        showBusy("Finding the best source…")
        var started = false
        val timeoutRunnable = Runnable { }
        val s = StreamResolver.Session(
            onUpdate = { list, _ -> if (!started && list.isNotEmpty()) busy?.setMessage("Found ${list.size} sources…") },
            onDone = { list, _ ->
                if (started) return@Session
                started = true; dismissBusy()
                val ranked = StreamRank.sorted(StreamRank.playable(list, Prefs.effectiveMaxResolution()), Prefs.effectiveMaxResolution())
                if (ranked.isEmpty()) { noStreams(); return@Session }
                startPlayer(videoId, v, ranked, ranked.first(), auto = true)
            },
        )
        session = s
        StreamResolver.resolve(meta.type, videoId, 9000, s)
    }

    private fun openSources(videoId: String) {
        startActivity(Intent(this, StreamsActivity::class.java).putExtra("meta", Json.metaToJson(meta)).putExtra("videoId", videoId))
    }

    private fun startPlayer(videoId: String, v: Video?, ranked: List<StreamItem>, pick: StreamItem, auto: Boolean) {
        val resume = WatchStore.forMeta(meta.id)
        PlaySession.apply {
            this.meta = this@DetailsActivity.meta; this.videoId = videoId
            season = v?.season ?: -1; episode = v?.episode ?: -1; episodeTitle = v?.title
            this.ranked = ranked; current = pick; autoSelected = auto
            resumeMs = if (resume != null && resume.videoId == videoId && resume.fraction in 0.02f..0.93f) resume.positionMs else 0L
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun noStreams() {
        AlertDialog.Builder(this).setTitle("No sources found")
            .setMessage("None of your stream addons returned a playable source. Install a stream addon in the Addons tab, or try again later.")
            .setPositiveButton("OK", null).show()
    }

    private fun showBusy(msg: String) {
        dismissBusy()
        busy = AlertDialog.Builder(this).setMessage(msg).setCancelable(true).setOnCancelListener { session?.cancelled = true }.show()
    }

    private fun dismissBusy() { busy?.dismiss(); busy = null }

    override fun onDestroy() { session?.cancelled = true; dismissBusy(); super.onDestroy() }

    private inner class EpisodeAdapter(private val list: List<Video>, private val resume: WatchStore.Entry?) : RecyclerView.Adapter<EpisodeAdapter.H>() {
        inner class H(val v: LinearLayout, val t: TextView, val s: TextView) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val c = parent.context
            val box = Ui.vbox(c).apply {
                setBackgroundColor(Ui.CARD); setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
                layoutParams = RecyclerView.LayoutParams(Ui.dp(c, 200), Ui.dp(c, 100)).also { it.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4)) }
                isFocusable = true; isClickable = true; foreground = Ui.focusBg(c, 6, 0x14FFFFFF)
            }
            val t = Ui.text(c, "", 14f, Ui.TEXT, true, 2)
            val s = Ui.text(c, "", 12f, Ui.DIM, false, 3)
            box.addView(Ui.text(c, "", 11f, Ui.ACCENT, true, 1)); box.addView(t); box.addView(s)
            return H(box, t, s)
        }
        override fun getItemCount() = list.size
        override fun onBindViewHolder(h: H, position: Int) {
            val v = list[position]
            (h.v.getChildAt(0) as TextView).text = "S${v.season} · E${v.episode}" + if (resume?.videoId == v.id) "  • resume" else ""
            h.t.text = v.title
            h.s.text = v.overview ?: ""
            h.v.setOnClickListener { playVideo(v.id) }
            h.v.setOnLongClickListener { openSources(v.id); true }
        }
    }
}
