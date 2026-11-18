package tv.myliststv.launcher.screens

import tv.myliststv.launcher.*
import tv.myliststv.launcher.services.*

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
 * Details for one title, laid out like a streaming app: the title's backdrop behind everything, its logo (or name),
 * Play / Sources / Trailer, the facts (genres, runtime, year, rating, certification, crew, country, awards), the story,
 * then tabs for Episodes and Cast & crew. Play picks a source by the viewer's rule; Sources lets them choose.
 */
class DetailsActivity : Activity() {

    private lateinit var meta: Meta
    private lateinit var bg: ImageView
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private lateinit var buttons: LinearLayout
    private lateinit var metaLine: TextView
    private lateinit var chips: LinearLayout
    private lateinit var crew: TextView
    private lateinit var desc: TextView
    private lateinit var extras: TextView
    private lateinit var tabRow: LinearLayout
    private lateinit var panel: FrameLayout
    private var tab = 0
    private var season = -1
    private var buttonsSig = ""
    private var tabsSig = ""
    private var focusedOnce = false
    private var busy: AlertDialog? = null
    private var session: StreamResolver.Session? = null
    private val lite get() = Prefs.lite()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        meta = Json.metaFromJson(intent.getStringExtra("meta") ?: "") ?: run { finish(); return }
        val c = this
        val frame = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        bg = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(bg, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(c).apply { background = Ui.scrimLeft() }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(c).apply { background = Ui.scrimBottom() }, FrameLayout.LayoutParams(-1, Ui.dp(c, 420), Gravity.BOTTOM))
        val body = Ui.vbox(c).apply { setPadding(Ui.dp(c, 48), Ui.dp(c, 36), Ui.dp(c, 48), Ui.dp(c, 32)) }
        val textWidth = Ui.dp(c, 560)
        logo = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_START; visibility = View.GONE }
        body.addView(logo, LinearLayout.LayoutParams(Ui.dp(c, 320), Ui.dp(c, 96)))
        title = Ui.text(c, meta.name, 34f, Ui.TEXT, true, 2).apply { maxWidth = textWidth }
        body.addView(title)
        buttons = Ui.hbox(c, 10)
        body.addView(buttons, Ui.wrap().apply { topMargin = Ui.dp(c, 14) })
        metaLine = Ui.text(c, "", 14f, Ui.TEXT2, false, 2).apply { maxWidth = textWidth }
        body.addView(metaLine, Ui.wrap().apply { topMargin = Ui.dp(c, 14) })
        chips = Ui.hbox(c, 8)
        body.addView(chips, Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
        crew = Ui.text(c, "", 13f, Ui.DIM, false, 2).apply { maxWidth = textWidth }
        body.addView(crew, Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
        desc = Ui.text(c, "", 15f, Ui.TEXT, false, 5).apply { maxWidth = textWidth }
        body.addView(desc, Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
        extras = Ui.text(c, "", 12f, Ui.DIM, false, 2).apply { maxWidth = textWidth }
        body.addView(extras, Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
        tabRow = Ui.hbox(c, 8)
        body.addView(tabRow, Ui.wrap().apply { topMargin = Ui.dp(c, 22) })
        panel = FrameLayout(c)
        body.addView(panel, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
        frame.addView(ScrollView(c).apply { addView(body); isFillViewport = true; isVerticalScrollBarEnabled = false }, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        render()
        Catalogs.meta(meta.type, meta.id, meta) { m -> if (m != null && !isFinishing) { meta = m; render() } }
        val auto = intent.getStringExtra("play")
        if (auto != null) frame.post { playVideo(auto) }
    }

    private fun render() {
        val c = this
        title.text = meta.name
        ImageLoader.load(bg, meta.background ?: meta.poster, Ui.dp(c, if (lite) 640 else 960), Ui.dp(c, if (lite) 360 else 540), fade = !lite)
        if (meta.logo != null) {
            logo.visibility = View.VISIBLE
            // The logo replaces the plain title once it has loaded; if it cannot load, the title stays.
            ImageLoader.load(logo, meta.logo, Ui.dp(c, 320), Ui.dp(c, 96), alpha = true) { ok -> title.visibility = if (ok) View.GONE else View.VISIBLE; logo.visibility = if (ok) View.VISIBLE else View.GONE }
        }
        val resume = WatchStore.forMeta(meta.id)
        val resumable = resume != null && resume.fraction in 0.02f..0.93f
        val watched = resume != null && resume.fraction > 0.93f
        val sig = "$resumable|$watched|${meta.trailers.firstOrNull()}|${meta.type}|${Library.has(meta.id)}"
        if (sig != buttonsSig) {
            buttonsSig = sig; buttons.removeAllViews()
            val at = if (meta.type == "series" && resume != null && resume.season >= 0) " S${resume.season} E${resume.episode}" else ""
            val play = Ui.button(c, (if (resumable) "Resume" else if (at.isNotEmpty()) "Next" else "Play") + at, primary = true) { playMain() }
            val round = Ui.dp(c, 48)
            buttons.addView(play, LinearLayout.LayoutParams(-2, round))
            val saved = Library.has(meta.id)
            buttons.addView(Ui.iconButton(c, if (saved) R.drawable.ic_bookmark else R.drawable.ic_add, if (saved) "Remove from Library" else "Add to Library") {
                val now = Library.toggle(meta); Toast.makeText(c, if (now) "Added to Library" else "Removed from Library", Toast.LENGTH_SHORT).show(); buttonsSig = ""; render(); buttons.getChildAt(1)?.requestFocus()
            }.also { it.isSelected = saved }, LinearLayout.LayoutParams(round, round))
            buttons.addView(Ui.iconButton(c, R.drawable.ic_list, "Choose a source") { sourcesMain() }, LinearLayout.LayoutParams(round, round))
            meta.trailers.firstOrNull()?.let { id -> buttons.addView(Ui.iconButton(c, R.drawable.ic_trailer, "Trailer") { openTrailer(id) }, LinearLayout.LayoutParams(round, round)) }
            if (meta.type == "movie") buttons.addView(Ui.iconButton(c, R.drawable.ic_check, if (watched) "Mark unwatched" else "Mark watched") { toggleWatched(watched) }.also { it.isSelected = watched }, LinearLayout.LayoutParams(round, round))
            if (!focusedOnce) { focusedOnce = true; play.post { play.requestFocus() } }
        }
        metaLine.text = listOfNotNull(if (meta.type == "series") "Series" else "Movie", meta.genres.take(3).joinToString(", ").ifEmpty { null }, meta.runtime, meta.year).joinToString("  \u2022  ")
        chips.removeAllViews()
        meta.certification?.let { chips.addView(chip(it)) }
        statusLabel()?.let { chips.addView(chip(it)) }
        meta.rating?.let { chips.addView(chip("\u2605 $it  IMDb")) }
        chips.visibility = if (chips.childCount == 0) View.GONE else View.VISIBLE
        crew.text = listOfNotNull(meta.directors.take(3).takeIf { it.isNotEmpty() }?.let { "Director: ${it.joinToString(", ")}" }, meta.writers.take(3).takeIf { it.isNotEmpty() }?.let { "Writers: ${it.joinToString(", ")}" }).joinToString("     ")
        crew.visibility = if (crew.text.isEmpty()) View.GONE else View.VISIBLE
        desc.text = meta.description ?: ""
        extras.text = listOfNotNull(nextEpisodeLine(), meta.country, meta.awards).joinToString("  \u2022  ")
        extras.visibility = if (extras.text.isEmpty()) View.GONE else View.VISIBLE
        renderTabs()
    }

    private fun statusLabel(): String? = when (meta.status?.lowercase()) { "continuing", "returning series", "ongoing" -> "ONGOING"; "ended", "canceled", "cancelled" -> "ENDED"; else -> null }

    /** "Next episode: S4 \u00b7 E6 \u00b7 Friday, Nov 19" for the first episode that has not aired yet. */
    private fun nextEpisodeLine(): String? {
        val now = System.currentTimeMillis()
        val v = meta.videos.filter { it.season > 0 }.firstOrNull { (it.airDate()?.time ?: 0L) > now } ?: return null
        return "Next episode: S${v.season} \u00b7 E${v.episode} \u00b7 " + java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.US).format(v.airDate()!!)
    }

    private fun chip(text: String) = Ui.text(this, text, 13f, Ui.TEXT, true).apply {
        setPadding(Ui.dp(this@DetailsActivity, 12), Ui.dp(this@DetailsActivity, 4), Ui.dp(this@DetailsActivity, 12), Ui.dp(this@DetailsActivity, 4))
        background = Ui.rounded(this@DetailsActivity, Ui.FILL_STRONG, Ui.R_PILL)
    }

    /** People shown in Cast & crew: directors first, then writers, then cast, each name once. */
    private fun people(): List<Pair<String, String>> =
        (meta.directors.map { it to "Director" } + meta.writers.map { it to "Writer" } + meta.cast.map { it to "Cast" }).distinctBy { it.first }.take(16)

    private fun renderTabs() {
        val names = ArrayList<String>()
        if (meta.type == "series") names += "Episodes"
        if (people().isNotEmpty()) names += "Cast & crew"
        val sig = names.joinToString("|") + "|" + meta.videos.size + "|" + people().size
        if (sig == tabsSig || panel.hasFocus()) return
        tabsSig = sig
        tabRow.removeAllViews(); panel.removeAllViews()
        if (names.isEmpty()) return
        tab = tab.coerceIn(0, names.size - 1)
        names.forEachIndexed { i, n ->
            val t = Ui.button(this, n) { showTab(names, i) }
            t.setOnFocusChangeListener { v, f -> (v as TextView).setTextColor(if (f) Ui.ON_FOCUS else Ui.TEXT); if (f) showTab(names, i) }
            tabRow.addView(t)
        }
        showTab(names, tab)
    }

    private fun showTab(names: List<String>, i: Int) {
        tab = i
        for (k in 0 until tabRow.childCount) tabRow.getChildAt(k).isSelected = k == i
        panel.removeAllViews()
        panel.addView(if (names[i] == "Episodes") episodesPanel() else castPanel())
    }

    private fun strip(h: Int): RecyclerView = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@DetailsActivity, LinearLayoutManager.HORIZONTAL, false)
        itemAnimator = null; clipToPadding = false
        layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@DetailsActivity, h))
    }

    private fun episodesPanel(): View {
        val c = this
        val col = Ui.vbox(c)
        val seasons = meta.videos.map { it.season }.distinct().filter { it > 0 }.ifEmpty { meta.videos.map { it.season }.distinct() }
        if (seasons.isEmpty()) return col.apply { addView(Ui.text(c, "No episode list yet. It loads from your catalog addon.", 13f, Ui.DIM)) }
        val resume = WatchStore.forMeta(meta.id)
        if (season !in seasons) season = resume?.season?.takeIf { it in seasons } ?: seasons.first()
        val chipsRow = Ui.hbox(c, 8)
        seasons.forEach { s -> chipsRow.addView(Ui.button(c, if (s == 0) "Specials" else "Season $s") { season = s; tabsSig = ""; renderTabs() }.apply { isSelected = s == season }) }
        col.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(chipsRow) })
        val models = meta.videos.filter { it.season == season }.map { v ->
            CardModel(v.title, v.thumbnail, v.overview, if (resume?.videoId == v.id) resume.fraction else 0f, v, badge = episodeBadge(v))
        }
        col.addView(strip(190).apply {
            setPadding(0, Ui.dp(c, 8), 0, 0)
            adapter = CardAdapter(CardKind.EPISODE, models, lite, { }, { m -> (m.payload as? Video)?.let { playVideo(it.id) } }, { m -> (m.payload as? Video)?.let { openSources(it.id) } })
        })
        return col
    }

    /** "EPISODE 6" with its air date: "Nov 12, 2026" once aired, "AIRS Nov 19, 2026" before. */
    private fun episodeBadge(v: Video): String {
        val d = v.airDate() ?: return "EPISODE ${v.episode}"
        val text = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(d)
        return "EPISODE ${v.episode}  \u00b7  " + if (d.time > System.currentTimeMillis()) "AIRS $text" else text
    }

    private fun castPanel(): View = strip(150).apply { adapter = PersonAdapter(people()) }

    private fun openTrailer(id: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id"))) }
        catch (_: Exception) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))) } catch (_: Exception) { Toast.makeText(this, "No app on this box can play trailers.", Toast.LENGTH_LONG).show() } }
    }

    private fun toggleWatched(watched: Boolean) {
        if (watched) WatchStore.remove(meta.id)
        else WatchStore.save(WatchStore.Entry(meta.id, meta.type, meta.name, meta.poster, meta.background, meta.id, -1, -1, null, 1L, 1L, System.currentTimeMillis()))
        render()
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
        showBusy("Finding the best source\u2026")
        var started = false
        var latest: List<StreamItem> = emptyList()
        var statuses: List<StreamResolver.AddonStatus> = emptyList()
        var timerSet = false
        // Do not wait for the slowest addon: pick 2.5 s after the first sources arrive, or when every addon has answered.
        fun pick() {
            if (started) return
            started = true; dismissBusy()
            val max = Prefs.effectiveMaxResolution()
            val ranked = StreamRank.sorted(StreamRank.playable(latest, max), max)
            if (ranked.isEmpty()) noStreams(statuses) else startPlayer(videoId, v, ranked, ranked.first(), auto = true)
        }
        val s = StreamResolver.Session(
            onUpdate = { list, st ->
                latest = list; statuses = st
                if (!started && list.isNotEmpty()) {
                    busy?.setMessage("Found ${list.size} sources\u2026")
                    if (!timerSet) { timerSet = true; Http.postDelayed(2500) { pick() } }
                }
            },
            onDone = { list, st -> latest = list; statuses = st; pick() },
        )
        session = s
        StreamResolver.resolve(meta.type, videoId, StreamResolver.DEFAULT_TIMEOUT_MS, s)
    }

    private fun openSources(videoId: String) {
        startActivity(Intent(this, StreamsActivity::class.java).putExtra("meta", Json.metaToJson(meta)).putExtra("videoId", videoId))
    }

    private fun startPlayer(videoId: String, v: Video?, ranked: List<StreamItem>, pick: StreamItem, auto: Boolean) {
        val resume = WatchStore.forMeta(meta.id)
        PlaySession.apply {
            live = null
            this.meta = this@DetailsActivity.meta; this.videoId = videoId
            season = v?.season ?: -1; episode = v?.episode ?: -1; episodeTitle = v?.title
            this.ranked = ranked; current = pick; autoSelected = auto
            resumeMs = if (resume != null && resume.videoId == videoId && resume.fraction in 0.02f..0.93f) resume.positionMs else 0L
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun noStreams(statuses: List<StreamResolver.AddonStatus>) {
        val why = if (statuses.isEmpty()) "No stream addon is installed for this title. Add one in Settings, then Addons." else statuses.joinToString("\n\n") { "${it.name} ${it.note ?: "gave nothing."}" }
        AlertDialog.Builder(this).setTitle("No sources found")
            .setMessage(why)
            .setPositiveButton("OK", null).show()
    }

    private fun showBusy(msg: String) {
        dismissBusy()
        busy = AlertDialog.Builder(this).setMessage(msg).setCancelable(true).setOnCancelListener { session?.cancelled = true }.show()
    }

    private fun dismissBusy() { busy?.dismiss(); busy = null }

    override fun onDestroy() { session?.cancelled = true; dismissBusy(); super.onDestroy() }

    /** A round photo (looked up by name), the name and the role. */
    private inner class PersonAdapter(private val list: List<Pair<String, String>>) : RecyclerView.Adapter<PersonAdapter.H>() {
        inner class H(val box: LinearLayout, val img: ImageView, val name: TextView, val role: TextView) : RecyclerView.ViewHolder(box)
        override fun getItemCount() = list.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
            val c = parent.context
            val size = Ui.dp(c, 76)
            val box = Ui.vbox(c).apply {
                gravity = Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8))
                layoutParams = RecyclerView.LayoutParams(Ui.dp(c, 104), RecyclerView.LayoutParams.WRAP_CONTENT)
                isFocusable = true; isClickable = true; foreground = Ui.focusBg(c, Ui.R_PANEL, Ui.FOCUS_FILL)
            }
            val img = ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.CARD) }; clipToOutline = true
            }
            val name = Ui.text(c, "", 12f, Ui.TEXT, true, 2).apply { gravity = Gravity.CENTER }
            val role = Ui.text(c, "", 11f, Ui.DIM, false, 1).apply { gravity = Gravity.CENTER }
            box.addView(img, LinearLayout.LayoutParams(size, size)); box.addView(name, Ui.matchWrap().apply { topMargin = Ui.dp(c, 6) }); box.addView(role, Ui.matchWrap())
            return H(box, img, name, role)
        }
        override fun onBindViewHolder(h: H, position: Int) {
            val (n, r) = list[position]
            h.name.text = n; h.role.text = r
            h.img.setTag(R.id.tag_person, n); h.img.setImageDrawable(null)
            val size = Ui.dp(h.box.context, 76)
            People.photo(n) { url -> if (h.img.getTag(R.id.tag_person) == n) ImageLoader.load(h.img, url, size, size) }
        }
    }
}
