package tv.myliststv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.*

/**
 * Full-screen player. ExoPlayer only, hardware decoding, one bottom bar.
 * Bad sources: the app picks the next best one by itself (fallback), and the Sources panel switches by hand.
 */
@UnstableApi
class PlayerActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: FrameLayout
    private lateinit var slate: LinearLayout
    private lateinit var slateText: TextView
    private lateinit var bottom: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var subText: TextView
    private lateinit var fill: View
    private lateinit var track: FrameLayout
    private lateinit var timeText: TextView
    private lateinit var controls: LinearLayout
    private lateinit var banner: TextView
    private lateinit var panel: LinearLayout
    private lateinit var panelTitle: TextView
    private lateinit var panelChips: LinearLayout
    private lateinit var panelList: LinearLayout
    private lateinit var errorBox: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var playBtn: TextView
    private lateinit var speedBtn: TextView

    private var fallback: SourceFallback? = null
    private lateinit var current: StreamItem
    private var autoSelected = PlaySession.autoSelected
    private var gotFirstFrame = false
    private var triedRefresh = HashSet<String>()
    private var panelKind = ""
    private var sourceFilter: String? = null
    private var lastSave = 0L
    private var tickRunning = false
    private var resolveSession: StreamResolver.Session? = null
    private var speedIdx = 1
    private var subs: List<Sub> = emptyList()
    private var subsFor: String? = null
    private var segs: List<Segments.Seg> = emptyList()
    private lateinit var skipBtn: TextView
    private val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    private val live get() = PlaySession.live != null

    private val hideControls = Runnable { if (!panelOpen() && player.isPlaying && !controls.hasFocus()) bottom.visibility = View.GONE }
    private val startupTimeout = Runnable { onStartupTimeout() }
    private val tick = object : Runnable { override fun run() { updateProgress(); if (tickRunning) handler.postDelayed(this, 500) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After the app was restarted by Android there is no session to play: go back instead of crashing.
        current = PlaySession.current ?: run { finish(); return }
        if (PlaySession.meta == null) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        buildPlayer()
        fallback = SourceFallback(PlaySession.ranked, Prefs.fallbackTries)
        fallback!!.markTried(current)
        begin(current, PlaySession.resumeMs, "Starting stream via ${current.addonName}…")
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val c = this
        root = FrameLayout(c).apply { setBackgroundColor(Ui.BG); isFocusable = true }
        playerView = PlayerView(c).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Ui.BG)
            setKeepContentOnPlayerReset(false)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        bottom = Ui.vbox(c).apply {
            background = Ui.scrimBottom()
            setPadding(Ui.dp(c, 48), Ui.dp(c, 60), Ui.dp(c, 48), Ui.dp(c, 24))
        }
        titleText = Ui.text(c, "", 26f, Ui.TEXT, true, 1)
        subText = Ui.text(c, "", 13f, Ui.TEXT2, false, 1)
        bottom.addView(titleText); bottom.addView(subText, Ui.wrap().apply { topMargin = Ui.dp(c, 4) })
        track = FrameLayout(c).apply { setBackgroundColor(Ui.TRACK) }
        fill = View(c).apply { setBackgroundColor(Ui.ACCENT) }
        track.addView(fill, FrameLayout.LayoutParams(0, -1))
        bottom.addView(track, LinearLayout.LayoutParams(-1, Ui.dp(c, 5)).apply { topMargin = Ui.dp(c, 14) })
        timeText = Ui.text(c, "0:00", 12f, Ui.DIM)
        bottom.addView(timeText, Ui.wrap().apply { topMargin = Ui.dp(c, 6) })
        controls = Ui.hbox(c, 8)
        playBtn = Ui.button(c, "Pause") { togglePlay() }
        controls.addView(playBtn)
        if (PlaySession.meta!!.type == "series") controls.addView(Ui.button(c, "Next episode") { playNextEpisode(manual = true) })
        controls.addView(Ui.button(c, "Subtitles") { openTrackPanel(C.TRACK_TYPE_TEXT) })
        controls.addView(Ui.button(c, "Audio") { openTrackPanel(C.TRACK_TYPE_AUDIO) })
        speedBtn = Ui.button(c, "Speed 1x") { openSpeed() }
        if (!live) { controls.addView(Ui.button(c, "Sources") { openSources() }); controls.addView(speedBtn) }
        controls.addView(Ui.button(c, "Info") { openInfo() })
        bottom.addView(controls, Ui.wrap().apply { topMargin = Ui.dp(c, 10) })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        banner = Ui.text(c, "", 15f, Ui.TEXT, true).apply {
            setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 10)); visibility = View.GONE
            background = Ui.rounded(c, Ui.ELEVATED, Ui.R_PILL, Ui.BRAND_LINE)
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(c, 28) })
        // Skip intro / recap / credits: shown only while inside one of those; OK skips it.
        skipBtn = Ui.text(c, "", 16f, Ui.TEXT, true).apply {
            setPadding(Ui.dp(c, 22), Ui.dp(c, 11), Ui.dp(c, 22), Ui.dp(c, 11)); visibility = View.GONE
            background = Ui.rounded(c, Ui.ELEVATED, Ui.R_PILL)
        }
        root.addView(skipBtn, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = Ui.dp(c, 150); rightMargin = Ui.dp(c, 48) })

        slate = Ui.vbox(c).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.OVERLAY); isClickable = true }
        slate.addView(ProgressBar(c), LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        slateText = Ui.text(c, "", 20f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }
        slate.addView(slateText, Ui.wrap().apply { topMargin = Ui.dp(c, 14) })
        root.addView(slate, FrameLayout.LayoutParams(-1, -1))

        panel = Ui.vbox(c).apply {
            setBackgroundColor(Ui.ELEVATED); visibility = View.GONE
            setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 12))
        }
        panelTitle = Ui.text(c, "", 22f, Ui.TEXT, true)
        panel.addView(panelTitle)
        panelChips = Ui.hbox(c, 6)
        val chipScroll = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(panelChips) }
        panel.addView(chipScroll, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
        panelList = Ui.vbox(c)
        panel.addView(ScrollView(c).apply { addView(panelList) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 8) })
        root.addView(panel, FrameLayout.LayoutParams(Ui.dp(c, 340), -1, Gravity.END))

        errorBox = Ui.vbox(c).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.OVERLAY); visibility = View.GONE; setPadding(Ui.dp(c, 80), 0, Ui.dp(c, 80), 0) }
        errorBox.addView(Ui.text(c, "Playback error", 26f, Ui.TEXT, true))
        errorText = Ui.text(c, "", 16f, Ui.TEXT2).apply { gravity = Gravity.CENTER }
        errorBox.addView(errorText, Ui.wrap().apply { topMargin = Ui.dp(c, 8); bottomMargin = Ui.dp(c, 16) })
        val eb = Ui.hbox(c, 10)
        eb.addView(Ui.button(c, "Choose a source") { hideError(); openSources() })
        eb.addView(Ui.button(c, "Retry") { hideError(); startStream(current, player.currentPosition.coerceAtLeast(0), "Retrying…") })
        eb.addView(Ui.button(c, "Open in another app") { openExternal() })
        eb.addView(Ui.button(c, "Go back") { finish() })
        errorBox.addView(eb)
        root.addView(errorBox, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.requestFocus()
        refreshTitle()
    }

    private fun refreshTitle() {
        val m = PlaySession.meta!!
        titleText.text = m.name
        if (live) {
            val (now, next) = Epg.nowNext(PlaySession.live!![PlaySession.liveIndex].tvgId)
            titleText.text = "CH ${PlaySession.liveIndex + 1} \u00b7 ${m.name}"
            subText.text = if (now != null) "Now: ${now.title}" + (next?.let { "   \u00b7   Next: ${it.title}" } ?: "") else "${current.title}   \u00b7   Up and down change channel"
            return
        }
        val parts = ArrayList<String>()
        if (PlaySession.season >= 0) parts += "S${PlaySession.season} · E${PlaySession.episode}" + (PlaySession.episodeTitle?.let { "  $it" } ?: "")
        if (current.resolution > 0) parts += "${current.resolution}p"
        if (current.hdr) parts += "HDR"
        parts += "via ${current.addonName}"
        subText.text = parts.joinToString("   ·   ")
    }

    // ---------------------------------------------------------------- player

    private fun buildPlayer() {
        val lite = Prefs.lite()
        val bufferBytes = (if (lite) 32 else 50) * 1024 * 1024
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .setTargetBufferBytes(bufferBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setLoadControl(load)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        val lang = Prefs.subtitleLang
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(java.util.Locale.getDefault().language)
            .apply { if (lang == "off") setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) else setPreferredTextLanguage(lang) }
            .build()
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { onFirstFrame() }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
                if (state == Player.STATE_READY && !gotFirstFrame && !playerHasVideo()) onFirstFrame()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "Pause" else "Play"
                if (!isPlaying) showControls() else scheduleHide()
            }
            override fun onPlayerError(error: PlaybackException) { onError(error) }
        })
        playerView.player = player
    }

    private fun playerHasVideo(): Boolean = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }

    /** Starts a stream for a new video: asks subtitle addons first (3 s at most), then plays. Later restarts of the same video reuse the answer. */
    private fun begin(s: StreamItem, pos: Long, msg: String) {
        val vid = PlaySession.videoId
        if (live || subsFor == vid) { startStream(s, pos, msg); return }
        subsFor = vid; segs = emptyList(); showSlate("Finding subtitles…")
        Subs.fetch(PlaySession.meta!!.type, vid, Prefs.subtitleLang) { found -> if (!isFinishing) { subs = found; startStream(s, pos, msg) } }
    }

    private fun subMime(url: String) = when {
        url.substringBefore('?').endsWith(".vtt") -> MimeTypes.TEXT_VTT
        url.substringBefore('?').let { it.endsWith(".ass") || it.endsWith(".ssa") } -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    private fun startStream(s: StreamItem, positionMs: Long, message: String) {
        current = s
        gotFirstFrame = false
        hideError()
        showSlate(message)
        refreshTitle()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MyListsTV/0.1")
            .setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(s.headers)
        val item = MediaItem.Builder().setUri(Uri.parse(s.url)).setSubtitleConfigurations(subs.mapIndexed { i, x ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(x.url)).setMimeType(subMime(x.url)).setLanguage(x.lang)
                .setLabel(x.lang.uppercase() + " (addon)").setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0).build() }).build()
        player.setMediaSource(DefaultMediaSourceFactory(this).setDataSourceFactory(http).createMediaSource(item), positionMs)
        player.playWhenReady = true
        player.prepare()
        handler.removeCallbacks(startupTimeout)
        if (autoSelected && Prefs.fallbackOn) handler.postDelayed(startupTimeout, Prefs.startupTimeoutSec * 1000L)
        if (!tickRunning) { tickRunning = true; handler.post(tick) }
    }

    private fun onFirstFrame() {
        if (gotFirstFrame) return
        gotFirstFrame = true
        handler.removeCallbacks(startupTimeout)
        val vid = PlaySession.videoId
        if (Prefs.skipIntro && !live) Segments.load(vid) { if (vid == PlaySession.videoId) segs = it }
        hideSlate()
        scheduleHide()
        if (fallback != null && fallback!!.attempts > 0 && autoSelected) bannerShow("The first source failed. Playing the next best match.")
    }

    private fun onStartupTimeout() {
        if (gotFirstFrame || isFinishing) return
        failed(PlayErrors.describe(null, PlayErrors.Kind.NETWORK).let { "This source did not start within ${Prefs.startupTimeoutSec} seconds." }, null, PlayErrors.Kind.NETWORK)
    }

    private fun onError(e: PlaybackException) {
        val http = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
        val kind = when (e.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlayErrors.Kind.DECODER
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlayErrors.Kind.FORMAT
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlayErrors.Kind.NETWORK
            else -> PlayErrors.Kind.OTHER
        }
        failed(PlayErrors.describe(http, kind), http, kind)
    }

    /** One place for every failure: refresh an expired link, else try the next source, else show the error screen. */
    private fun failed(reason: String, http: Int?, kind: PlayErrors.Kind) {
        handler.removeCallbacks(startupTimeout)
        val position = player.currentPosition.coerceAtLeast(0)
        // A link that played and then died, or an expired link, is fetched again from its addon once.
        if ((gotFirstFrame || http == 403 || http == 404 || http == 410) && triedRefresh.add(current.key)) { refreshLink(position); return }
        if (autoSelected && Prefs.fallbackOn && !gotFirstFrame && PlayErrors.worthTryingAnotherSource(http, kind)) {
            val fb = fallback!!
            val next = fb.next(current)
            if (next != null) {
                autoSelected = true
                startStream(next, position, "This source did not start. Trying the next best source (${fb.attempts} of ${fb.maxExtraTries})")
                return
            }
        }
        showError(reason)
    }

    /** Fetches the same source again from its addon (it may hand out a fresh link) and resumes where it stopped. */
    private fun refreshLink(positionMs: Long) {
        showSlate("The link may have expired. Fetching it again…")
        val failedKey = current.key
        val s = StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { list, _ ->
            val fresh = list.firstOrNull { it.key == failedKey && it.url != current.url } ?: list.firstOrNull { it.addonUrl == current.addonUrl && it.filename == current.filename && it.filename != null && it.url != current.url }
            if (fresh != null && !isFinishing) startStream(fresh, positionMs, "Resuming…")
            else if (!isFinishing) {
                hideSlate()
                // Nothing fresh: continue with the normal path as if the link had failed outright.
                if (autoSelected && Prefs.fallbackOn && !gotFirstFrame) {
                    val next = fallback!!.next(current)
                    if (next != null) { startStream(next, positionMs, "Trying the next best source (${fallback!!.attempts} of ${fallback!!.maxExtraTries})"); return@Session }
                }
                showError(PlayErrors.describe(410, PlayErrors.Kind.NETWORK))
            }
        })
        resolveSession = s
        StreamResolver.resolve(PlaySession.meta!!.type, PlaySession.videoId, 8000, s)
    }

    /** Live TV: switch to the next or previous channel in the playlist. */
    private fun zap(dir: Int) {
        val list = PlaySession.live ?: return
        val i = (PlaySession.liveIndex + dir + list.size) % list.size
        val c = list[i]
        PlaySession.liveIndex = i
        PlaySession.meta = Meta("live", "tv", c.name)
        autoSelected = false
        startStream(StreamItem("IPTV", "iptv", c.url, c.name, c.group, null, null, 0, 0, false, false, null), 0, "Tuning ${c.name}\u2026")
    }

    private fun onEnded() {
        if (live) { finish(); return }
        saveProgress(force = true, ended = true)
        if (PlaySession.meta!!.type == "series" && Prefs.autoplayNext) playNextEpisode(manual = false) else finish()
    }

    private fun playNextEpisode(manual: Boolean) {
        val m = PlaySession.meta!!
        val idx = m.videos.indexOfFirst { it.id == PlaySession.videoId }
        val next = if (idx >= 0) m.videos.drop(idx + 1).firstOrNull { it.season > 0 || PlaySession.season == 0 } else null
        if (next == null) { if (manual) bannerShow("That was the last episode.") else finish(); return }
        saveProgress(force = true)
        showSlate("Next: S${next.season} · E${next.episode}  ${next.title}\nFinding the best source…")
        val prevGroup = current.bingeGroup; val prevAddon = current.addonUrl
        val s = StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { list, _ ->
            if (isFinishing) return@Session
            val max = Prefs.effectiveMaxResolution()
            val ranked = StreamRank.sorted(StreamRank.playable(list, max), max)
            val pick = ranked.firstOrNull { prevGroup != null && it.bingeGroup == prevGroup } ?: ranked.firstOrNull { it.addonUrl == prevAddon } ?: ranked.firstOrNull()
            if (pick == null) { hideSlate(); showError("No sources were found for the next episode."); return@Session }
            PlaySession.videoId = next.id; PlaySession.season = next.season; PlaySession.episode = next.episode; PlaySession.episodeTitle = next.title
            PlaySession.ranked = ranked; PlaySession.current = pick; PlaySession.autoSelected = true; PlaySession.resumeMs = 0
            fallback = SourceFallback(ranked, Prefs.fallbackTries); fallback!!.markTried(pick)
            autoSelected = true; triedRefresh.clear()
            begin(pick, 0, "Starting stream via ${pick.addonName}…")
        })
        resolveSession = s
        StreamResolver.resolve(m.type, next.id, StreamResolver.DEFAULT_TIMEOUT_MS, s)
    }

    // ---------------------------------------------------------------- panels

    private fun panelOpen() = panel.visibility == View.VISIBLE
    private fun errorOpen() = errorBox.visibility == View.VISIBLE

    private fun openPanel(title: String) {
        panelKind = title
        panelTitle.text = title
        panelChips.removeAllViews(); panelList.removeAllViews()
        panel.visibility = View.VISIBLE
        showControls(); handler.removeCallbacks(hideControls)
    }

    private fun closePanel() {
        panel.visibility = View.GONE
        controls.getChildAt(0)?.requestFocus()
        scheduleHide()
    }

    private fun item(label: String, sub: String?, tag: String?, tagColor: Int = Ui.GOOD, onClick: () -> Unit): View {
        val c = this
        val r = Ui.vbox(c).apply {
            setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8)); Ui.rowStyle(c, this)
            isFocusable = true; isClickable = true; setOnClickListener { onClick() }
        }
        val line = Ui.hbox(c, 8)
        line.addView(Ui.text(c, label, 14f, Ui.TEXT, true, 1), LinearLayout.LayoutParams(0, -2, 1f))
        if (tag != null) line.addView(Ui.text(c, tag, 11f, tagColor, true))
        r.addView(line)
        if (sub != null) r.addView(Ui.text(c, sub, 11f, Ui.DIM, false, 1))
        r.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(c, 5) }
        return r
    }

    private fun openSources() {
        openPanel("Sources")
        sourceFilter = null
        renderSources()
        panelList.getChildAt(0)?.requestFocus()
    }

    private fun renderSources() {
        panelChips.removeAllViews(); panelList.removeAllViews()
        val all = PlaySession.ranked
        val addons = all.map { it.addonName }.distinct()
        panelChips.addView(chip("All (${all.size})") { sourceFilter = null; renderSources(); panelChips.getChildAt(0)?.requestFocus() })
        addons.forEach { a -> panelChips.addView(chip(a) { sourceFilter = a; renderSources(); (0 until panelChips.childCount).firstOrNull { (panelChips.getChildAt(it) as TextView).text == a }?.let { panelChips.getChildAt(it).requestFocus() } }) }
        panelChips.addView(chip("Reload") { reloadSources() })
        val max = Prefs.effectiveMaxResolution()
        val shown = all.filter { sourceFilter == null || it.addonName == sourceFilter }
        if (shown.isEmpty()) panelList.addView(Ui.text(this, "No sources.", 13f, Ui.DIM))
        shown.forEach { s ->
            val playing = s.key == current.key
            val failed = fallback?.wasTried(s) == true && !playing
            val tag = when { playing -> "Playing"; failed -> "Failed"; s.cached -> "Cached"; else -> null }
            val col = when { playing -> Ui.ACCENT; failed -> Ui.BAD; else -> Ui.GOOD }
            val label = (if (s.resolution > 0) "${s.resolution}p  " else "") + s.name.replace("\n", " ")
            val sub = s.addonName + if (s.sizeBytes > 0) "  ·  " + (if (s.sizeBytes >= 1_073_741_824L) "%.1f GB".format(s.sizeBytes / 1_073_741_824.0) else "${s.sizeBytes / 1_048_576} MB") + (if (s.resolution > max && max > 0) "  ·  may not play" else "") else ""
            panelList.addView(item(label, sub, tag, col) { if (!playing) switchTo(s) })
        }
    }

    private fun chip(label: String, onClick: () -> Unit): TextView = Ui.button(this, label) { onClick() }.apply { Ui.size(this, 12f); setPadding(Ui.dp(this@PlayerActivity, 12), Ui.dp(this@PlayerActivity, 6), Ui.dp(this@PlayerActivity, 12), Ui.dp(this@PlayerActivity, 6)) }

    private fun reloadSources() {
        bannerShow("Looking for sources…")
        val s = StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { list, st ->
            if (isFinishing) return@Session
            val max = Prefs.effectiveMaxResolution()
            val ranked = StreamRank.sorted(list, max)
            if (ranked.isNotEmpty()) PlaySession.ranked = ranked
            bannerShow("Reloaded: ${ranked.size} sources from ${st.count { it.ok }} addons")
            if (panelOpen() && panelKind == "Sources") renderSources()
        })
        resolveSession = s
        StreamResolver.resolve(PlaySession.meta!!.type, PlaySession.videoId, StreamResolver.DEFAULT_TIMEOUT_MS, s)
    }

    /** Manual switch: keeps the viewer's place, and the app stops choosing for them. */
    private fun switchTo(s: StreamItem) {
        val pos = player.currentPosition.coerceAtLeast(0)
        panel.visibility = View.GONE
        autoSelected = false
        triedRefresh.clear()
        val place = fmt(pos)
        startStream(s, pos, "Switching source, keeping your place at $place")
        bannerShow("Now playing ${if (s.resolution > 0) "${s.resolution}p " else ""}via ${s.addonName}")
    }

    private fun openTrackPanel(type: Int) {
        openPanel(if (type == C.TRACK_TYPE_TEXT) "Subtitles" else "Audio")
        val groups = player.currentTracks.groups.filter { it.type == type && it.length > 0 }
        if (type == C.TRACK_TYPE_TEXT) {
            val off = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            panelList.addView(item("Off", null, if (off) "Selected" else null) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); closePanel() })
        }
        if (groups.isEmpty()) panelList.addView(Ui.text(this, if (type == C.TRACK_TYPE_TEXT) "No subtitles in this file." else "No other audio tracks.", 13f, Ui.DIM))
        groups.forEachIndexed { gi, g ->
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val lang = f.language?.let { java.util.Locale(it).displayLanguage } ?: "Track ${gi + 1}"
                val extra = listOfNotNull(f.label, if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) "${f.channelCount}ch" else null, f.sampleMimeType?.substringAfter('/')?.uppercase()).joinToString(" · ")
                val sel = g.isTrackSelected(ti) && !(type == C.TRACK_TYPE_TEXT && player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
                panelList.addView(item(lang, extra.ifEmpty { null }, if (sel) "Selected" else null) {
                    val b = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, ti))
                    player.trackSelectionParameters = b.build(); closePanel()
                })
            }
        }
        panelList.getChildAt(0)?.requestFocus()
    }

    private fun openSpeed() {
        openPanel("Playback speed")
        speeds.forEachIndexed { i, sp ->
            panelList.addView(item("${sp}x", null, if (i == speedIdx) "Selected" else null) {
                speedIdx = i; player.setPlaybackSpeed(sp); speedBtn.text = "Speed ${sp}x"; closePanel() })
        }
        panelList.getChildAt(speedIdx)?.requestFocus()
    }

    private fun openInfo() {
        openPanel("Stream info")
        val f = player.videoFormat
        listOf("Source" to current.addonName, "File" to (current.filename ?: "-"), "Quality" to (if (current.resolution > 0) "${current.resolution}p" else "unknown"),
            "Size" to (if (current.sizeBytes > 0) "%.1f GB".format(current.sizeBytes / 1_073_741_824.0) else "-"),
            "Video" to (f?.let { "${it.width}x${it.height} ${it.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""}" } ?: "-"),
            "Player" to "Media3 hardware decoder", "Buffered" to "${player.totalBufferedDuration / 1000}s ahead").forEach { (k, v) ->
            panelList.addView(Ui.text(this, k, 11f, Ui.DIM)); panelList.addView(Ui.text(this, v, 14f, Ui.TEXT, true, 2), Ui.wrap().apply { bottomMargin = Ui.dp(this@PlayerActivity, 6) })
        }
    }

    private fun openExternal() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(current.url), "video/*"), "Open with"))
        } catch (e: Exception) { bannerShow("No other video app found.") }
    }

    // ---------------------------------------------------------------- helpers

    private fun showSlate(msg: String) { slateText.text = msg; slate.visibility = View.VISIBLE; bottom.visibility = View.GONE }
    private fun hideSlate() { slate.visibility = View.GONE }
    private fun showError(msg: String) { hideSlate(); errorText.text = msg; errorBox.visibility = View.VISIBLE; errorBox.getChildAt(2)?.let { (it as LinearLayout).getChildAt(0).requestFocus() } }
    private fun hideError() { errorBox.visibility = View.GONE }

    private fun bannerShow(text: String) {
        banner.text = text; banner.visibility = View.VISIBLE
        handler.removeCallbacks(bannerHide); handler.postDelayed(bannerHide, 3600)
    }
    private val bannerHide = Runnable { banner.visibility = View.GONE }

    private fun showControls() { if (slate.visibility != View.VISIBLE) bottom.visibility = View.VISIBLE; scheduleHide() }
    private fun scheduleHide() { handler.removeCallbacks(hideControls); handler.postDelayed(hideControls, 5000) }

    private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    private fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    /** The segment under the playhead, or null; also what the skip button says and does. */
    private fun skipTarget(): Segments.Seg? = if (live || !Prefs.skipIntro || panelOpen() || errorOpen() || slate.visibility == View.VISIBLE) null else Segments.at(segs, player.currentPosition)

    private fun updateSkip() {
        val s = skipTarget()
        skipBtn.visibility = if (s == null) View.GONE else View.VISIBLE
        if (s != null) skipBtn.text = when (s.kind) { "intro" -> "Skip intro"; "recap" -> "Skip recap"; else -> if (PlaySession.meta?.type == "series" && Prefs.autoplayNext) "Next episode" else "Skip credits" } + "   \u00b7   OK"
    }

    private fun doSkip(s: Segments.Seg) {
        if (s.kind == "credits" && PlaySession.meta?.type == "series" && Prefs.autoplayNext) playNextEpisode(manual = false) else seekBy(s.endMs - player.currentPosition)
    }

    private fun updateProgress() {
        updateSkip()
        val dur = player.duration
        val pos = player.currentPosition
        if (dur > 0) {
            (fill.layoutParams as FrameLayout.LayoutParams).width = (track.width * (pos.toFloat() / dur)).toInt().coerceIn(0, track.width)
            fill.requestLayout()
            timeText.text = "${fmt(pos)} / ${fmt(dur)}"
        } else timeText.text = fmt(pos)
        saveProgress(force = false)
    }

    private fun saveProgress(force: Boolean, ended: Boolean = false) {
        val now = System.currentTimeMillis()
        if (live || (!force && now - lastSave < 15_000)) return
        if (!gotFirstFrame && !ended) return
        val dur = player.duration
        if (dur <= 0) return
        lastSave = now
        val m = PlaySession.meta!!
        val pos = if (ended) dur else player.currentPosition
        WatchStore.save(WatchStore.Entry(m.id, m.type, m.name, m.poster, m.background, PlaySession.videoId, PlaySession.season, PlaySession.episode,
            PlaySession.episodeTitle, pos, dur, now))
    }

    // ---------------------------------------------------------------- keys and lifecycle

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_MENU -> { if (!errorOpen() && !live) openSources(); return true }
                KeyEvent.KEYCODE_CHANNEL_UP -> { if (live) zap(1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (live) zap(-1); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000); return true }
            }
            if (panelOpen() || errorOpen() || slate.visibility == View.VISIBLE) return super.dispatchKeyEvent(e)
            val inControls = controls.hasFocus()
            if (!inControls) {
                showControls()
                when (e.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { if (!live) seekStep(e, -1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!live) seekStep(e, 1); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { skipTarget()?.let { doSkip(it) } ?: togglePlay(); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if (live) zap(-1) else { bottom.visibility = View.VISIBLE; controls.getChildAt(0)?.requestFocus() }; return true }
                    KeyEvent.KEYCODE_DPAD_UP -> { if (live) zap(1); return true }
                }
            } else if (e.keyCode == KeyEvent.KEYCODE_DPAD_UP) { root.requestFocus(); scheduleHide(); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    /** Holding the key seeks faster: 10 s, then 20 s, then 40 s per step. */
    private fun seekStep(e: KeyEvent, dir: Int) {
        val step = when { e.repeatCount < 5 -> 10_000L; e.repeatCount < 15 -> 20_000L; else -> 40_000L }
        seekBy(dir * step)
    }

    private fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val t = (player.currentPosition + deltaMs).coerceIn(0, if (dur > 0) dur - 1000 else Long.MAX_VALUE)
        player.seekTo(t); updateProgress()
    }

    @Deprecated("Back closes the panel, then the controls, then the player.")
    override fun onBackPressed() {
        when {
            panelOpen() -> closePanel()
            errorOpen() -> finish()
            controls.hasFocus() -> { root.requestFocus(); scheduleHide() }
            else -> { saveProgress(force = true); finish() }
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress(force = true)
        if (!live) WatchNext.publish(this)
        player.pause()
    }

    override fun onDestroy() {
        tickRunning = false
        handler.removeCallbacksAndMessages(null)
        resolveSession?.cancelled = true
        player.release()
        super.onDestroy()
    }
}
