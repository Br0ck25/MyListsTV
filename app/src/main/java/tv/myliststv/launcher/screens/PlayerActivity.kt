package tv.myliststv.launcher.screens

import tv.myliststv.launcher.R
import tv.myliststv.launcher.services.*

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

    private companion object { const val REQ_EXTERNAL = 41 }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: FrameLayout
    private lateinit var slate: LinearLayout
    private lateinit var slateText: TextView
    private lateinit var slateBg: ImageView
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
    private lateinit var playBtn: ImageView
    private lateinit var topInfo: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var endsText: TextView

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
    private val stats = PlayerStats()
    private lateinit var statsText: TextView
    private lateinit var marks: SeekMarks
    private lateinit var seekBubble: TextView
    private lateinit var resumeBox: LinearLayout
    private var lastError = ""
    private var delayMs = 0L
    private var delayAtOpen = 0L
    private var resumeTick: Runnable? = null
    private var externalFrom = 0L
    private var zoom = Zoom()
    private var zoomFocus = 0

    private val hideControls = Runnable { if (!panelOpen() && player.isPlaying && !controls.hasFocus()) bottom.visibility = View.GONE }
    private val startupTimeout = Runnable { onStartupTimeout() }
    private val tick = object : Runnable { override fun run() { updateProgress(); if (tickRunning) handler.postDelayed(this, 500) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After the app was restarted by Android there is no session to play: go back instead of crashing.
        if (PlaySession.meta == null) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PlaySession.resolving) { buildUi(); buildPlayer(); showSlate("Finding the best source…"); resolveFirst(); return }
        current = PlaySession.current ?: run { finish(); return }
        buildUi()
        buildPlayer()
        fallback = SourceFallback(PlaySession.ranked, Prefs.fallbackTries)
        fallback!!.markTried(current)
        offerResume(PlaySession.resumeMs) { pos -> begin(current, pos, "Starting stream via ${current.addonName}…") }
    }

    /** Left part way: "Resume 12:34" or "Start over", picking Resume by itself after eight seconds. Short or no progress just starts. */
    private fun offerResume(at: Long, go: (Long) -> Unit) {
        if (!PlayerPrefs.askResume || at < 120_000L) { go(at); return }
        var left = 8
        val resume = Ui.button(this, "") { chooseResume(go, at) }
        val over = Ui.button(this, "Start over") { chooseResume(go, 0L) }
        resumeBox.removeAllViews()
        resumeBox.addView(Ui.text(this, PlaySession.meta!!.name, 26f, Ui.TEXT, true))
        resumeBox.addView(Ui.text(this, "You stopped at ${fmt(at)}.", 16f, Ui.TEXT2), Ui.wrap().apply { topMargin = Ui.dp(this@PlayerActivity, 6); bottomMargin = Ui.dp(this@PlayerActivity, 18) })
        val row = Ui.hbox(this, 10); row.addView(resume); row.addView(over); resumeBox.addView(row)
        resumeBox.visibility = View.VISIBLE
        val t = object : Runnable { override fun run() {
            if (left <= 0) { chooseResume(go, at); return }
            resume.text = "Resume at ${fmt(at)}  ($left)"; left--; handler.postDelayed(this, 1000)
        } }
        resumeTick = t; t.run(); resume.requestFocus()
    }

    private fun chooseResume(go: (Long) -> Unit, pos: Long) {
        resumeTick?.let { handler.removeCallbacks(it) }; resumeTick = null
        resumeBox.visibility = View.GONE
        PlaySession.resumeMs = pos
        go(pos)
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
        track = FrameLayout(c).apply { background = Ui.rounded(c, Ui.TRACK_PLAYER, Ui.R_PILL); clipToOutline = true }
        fill = View(c).apply { setBackgroundColor(Ui.ACCENT) }
        track.addView(fill, FrameLayout.LayoutParams(0, -1))
        marks = SeekMarks(c); track.addView(marks, FrameLayout.LayoutParams(-1, -1))
        bottom.addView(track, LinearLayout.LayoutParams(-1, Ui.dp(c, 8)).apply { topMargin = Ui.dp(c, 14) })
        timeText = Ui.text(c, "0:00", 15f, Ui.TEXT2)
        // Nuvio's bar: the round play button, then one icon for each job, and the time at the far right.
        controls = Ui.hbox(c, 12).apply { gravity = Gravity.CENTER_VERTICAL }
        val big = Ui.dp(c, 54); val small = Ui.dp(c, 44)
        playBtn = ImageView(c).apply {
            setImageResource(R.drawable.ic_pause); setColorFilter(Ui.BG)
            val p = Ui.dp(c, 12); setPadding(p, p, p, p)
            background = Ui.rounded(c, Ui.TEXT, Ui.R_PILL); foreground = Ui.focusBg(c, Ui.R_PILL, Ui.CLEAR)
            isFocusable = true; isClickable = true; setOnClickListener { togglePlay() }
        }
        controls.addView(playBtn, LinearLayout.LayoutParams(big, big))
        fun icon(res: Int, desc: String, run: () -> Unit) = controls.addView(Ui.iconButton(c, res, desc, run), LinearLayout.LayoutParams(small, small))
        val series = PlaySession.meta!!.type == "series"
        if (series) icon(R.drawable.ic_next, "Next episode") { playNextEpisode(manual = true) }
        icon(R.drawable.ic_cc, "Subtitles") { openTrackPanel(C.TRACK_TYPE_TEXT) }
        icon(R.drawable.ic_speed, "Playback speed") { openSpeed() }
        icon(R.drawable.ic_audio, "Audio") { openTrackPanel(C.TRACK_TYPE_AUDIO) }
        icon(R.drawable.ic_source, "Sources") { openSources() }
        if (series) icon(R.drawable.ic_episodes, "Episodes") { openEpisodes() }
        icon(R.drawable.ic_zoom, "Zoom") { openZoom() }
        icon(R.drawable.ic_info, "Stream info") { openInfo() }
        controls.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(timeText)
        bottom.addView(controls, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        // The time and when it will end, at the top right, with the bar.
        clockText = Ui.text(c, "", 22f, Ui.TEXT, false).apply { gravity = Gravity.END }
        endsText = Ui.text(c, "", 14f, Ui.TEXT2, false).apply { gravity = Gravity.END }
        topInfo = Ui.vbox(c).apply { addView(clockText); addView(endsText, Ui.wrap().apply { topMargin = Ui.dp(c, 2); gravity = Gravity.END }) }
        root.addView(topInfo, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = Ui.dp(c, 28); rightMargin = Ui.dp(c, 48) })

        banner = Ui.text(c, "", 15f, Ui.TEXT, true).apply {
            setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 10)); visibility = View.GONE
            background = Ui.rounded(c, Ui.ELEVATED, Ui.R_PILL, Ui.BRAND_LINE)
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(c, 28) })
        // Skip intro / recap / credits: shown only while inside one of those; OK skips it.
        skipBtn = Ui.button(c, "", primary = true) { skipTarget()?.let { doSkip(it) }; hideBarNow() }.apply { visibility = View.GONE; Ui.size(this, 16f) }
        root.addView(skipBtn, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = Ui.dp(c, 48); leftMargin = Ui.dp(c, 48) })
        bottom.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeSkip(); topInfo.visibility = bottom.visibility }
        // Live numbers over the picture (settings or the Stream info panel), and the time a seek lands on.
        statsText = Ui.text(c, "", 12f, Ui.TEXT, false).apply {
            setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); background = Ui.rounded(c, Ui.SCRIM_SLATE, Ui.R_PANEL)
            visibility = if (PlayerPrefs.stats) View.VISIBLE else View.GONE
        }
        root.addView(statsText, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = Ui.dp(c, 28); leftMargin = Ui.dp(c, 48) })
        seekBubble = Ui.text(c, "", 20f, Ui.TEXT, true).apply {
            setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 10)); visibility = View.GONE
            background = Ui.rounded(c, Ui.ELEVATED, Ui.R_PILL, Ui.BRAND_LINE)
        }
        root.addView(seekBubble, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Ui.dp(c, 150) })

        slateBg = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        root.addView(slateBg, FrameLayout.LayoutParams(-1, -1))
        slate = Ui.vbox(c).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.OVERLAY); isClickable = true }
        slate.addView(ProgressBar(c), LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)))
        slateText = Ui.text(c, "", 20f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }
        slate.addView(slateText, Ui.wrap().apply { topMargin = Ui.dp(c, 14) })
        root.addView(slate, FrameLayout.LayoutParams(-1, -1))

        panel = Ui.vbox(c).apply {
            background = Ui.rounded(c, Ui.GLASS, Ui.R_DIALOG); visibility = View.GONE
            setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 12))
        }
        panelTitle = Ui.text(c, "", 22f, Ui.TEXT, true)
        panel.addView(panelTitle)
        panelChips = Ui.hbox(c, 6)
        val chipScroll = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(panelChips) }
        panel.addView(chipScroll, Ui.matchWrap().apply { topMargin = Ui.dp(c, 8) })
        panelList = Ui.vbox(c)
        panel.addView(ScrollView(c).apply { addView(panelList) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Ui.dp(c, 8) })
        root.addView(panel, FrameLayout.LayoutParams(Ui.dp(c, 360), -1, Gravity.END).apply { setMargins(0, Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16)) })

        errorBox = Ui.vbox(c).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.OVERLAY); visibility = View.GONE; setPadding(Ui.dp(c, 80), 0, Ui.dp(c, 80), 0) }
        errorBox.addView(Ui.text(c, "Playback error", 26f, Ui.TEXT, true))
        errorText = Ui.text(c, "", 16f, Ui.TEXT2).apply { gravity = Gravity.CENTER }
        errorBox.addView(errorText, Ui.wrap().apply { topMargin = Ui.dp(c, 8); bottomMargin = Ui.dp(c, 16) })
        val eb = Ui.hbox(c, 10)
        eb.addView(Ui.button(c, "Choose a source") { hideError(); openSources() })
        eb.addView(Ui.button(c, "Retry") { hideError(); startStream(current, player.currentPosition.coerceAtLeast(0), "Retrying…") })
        eb.addView(Ui.button(c, "Play in another app") { openExternal() })
        eb.addView(Ui.button(c, "Go back") { finish() })
        eb.addView(Ui.button(c, "Report problem") { hideError(); openReport() })
        errorBox.addView(eb)
        root.addView(errorBox, FrameLayout.LayoutParams(-1, -1))
        resumeBox = Ui.vbox(c).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.OVERLAY); visibility = View.GONE; isClickable = true }
        root.addView(resumeBox, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.requestFocus()
        refreshTitle()
    }

    /** Finds the streams for the title the details page started, picks the best (2.5 s after the first arrive, or when every addon has answered) and plays it. */
    private fun resolveFirst() {
        val videoId = PlaySession.videoId
        var started = false
        var latest: List<StreamItem> = emptyList()
        var statuses: List<StreamResolver.AddonStatus> = emptyList()
        var timerSet = false
        fun pick() {
            if (started || isFinishing) return
            started = true
            val max = Prefs.effectiveMaxResolution()
            val ranked = DeviceRank.forThisBox(StreamRank.sorted(StreamRank.playable(latest, max), max))
            PlaySession.resolving = false
            if (ranked.isEmpty()) { PlaySession.noStreams = statuses; finish(); return }
            PlaySession.ranked = ranked; PlaySession.current = ranked.first(); PlaySession.autoSelected = true
            current = ranked.first(); autoSelected = true
            fallback = SourceFallback(ranked, Prefs.fallbackTries); fallback!!.markTried(current)
            offerResume(PlaySession.resumeMs) { pos -> begin(current, pos, "Starting stream via ${current.addonName}…") }
        }
        val sess = StreamResolver.Session(
            onUpdate = { list, st ->
                latest = list; statuses = st
                if (!started && list.isNotEmpty()) {
                    slateText.text = "Found ${list.size} sources…"
                    if (!timerSet) { timerSet = true; Http.postDelayed(2500) { pick() } }
                }
            },
            onDone = { list, st -> latest = list; statuses = st; pick() },
        )
        resolveSession = sess
        StreamResolver.resolve(StreamResolver.typeFor(PlaySession.meta!!.type, PlaySession.meta!!.id, videoId), videoId, StreamResolver.DEFAULT_TIMEOUT_MS, sess)
    }

    private fun refreshTitle() {
        if (!::current.isInitialized) return
        val m = PlaySession.meta!!
        titleText.text = m.name
        val parts = ArrayList<String>()
        if (PlaySession.season >= 0) parts += "S${PlaySession.season} · E${PlaySession.episode}" + (PlaySession.episodeTitle?.let { "  $it" } ?: "")
        if (current.resolution > 0) parts += "${current.resolution}p"
        if (current.hdr) parts += "HDR"
        parts += "via ${current.addonName}"
        subText.text = parts.joinToString("   ·   ")
    }

    // ---------------------------------------------------------------- player

    private fun buildPlayer() {
        player = ExoPlayer.Builder(this, PlayerEngine.renderers(this))
            .setLoadControl(PlayerEngine.loadControl())
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        val (rememberedAudio, rememberedSub) = Prefs.recallTracks(PlaySession.meta?.id)
        val lang = rememberedSub ?: Prefs.subtitleLang
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(rememberedAudio ?: java.util.Locale.getDefault().language)
            .apply { if (lang == "off") setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) else setPreferredTextLanguage(lang) }
            .build()
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { onFirstFrame() }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
                if (state == Player.STATE_READY && !gotFirstFrame && !playerHasVideo()) onFirstFrame()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                if (!isPlaying) showControls() else scheduleHide()
            }
            override fun onPlayerError(error: PlaybackException) { onError(error) }
        })
        player.setVideoChangeFrameRateStrategy(PlayerEngine.frameRateStrategy())
        player.addAnalyticsListener(stats)
        playerView.subtitleView?.let { PlayerEngine.styleSubtitles(it) }
        PlayerEngine.applyColorMode(this)
        playerView.player = player
    }

    private fun playerHasVideo(): Boolean = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }

    /** Starts a stream for a new video: asks subtitle addons first (3 s at most), then plays. Later restarts of the same video reuse the answer. */
    private fun begin(s: StreamItem, pos: Long, msg: String) {
        val vid = PlaySession.videoId
        if (subsFor == vid) { startStream(s, pos, msg); return }
        subsFor = vid; segs = emptyList(); showSlate("Finding subtitles…")
        Subs.fetch(StreamResolver.typeFor(PlaySession.meta!!.type, PlaySession.meta!!.id, vid), vid, Prefs.subtitleLang) { found -> if (!isFinishing) { subs = found; startStream(s, pos, msg) } }
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
        stats.reset(); lastError = ""
        delayMs = PlayerPrefs.subDelay(PlaySession.meta?.id)
        withSubtitles(subs, delayMs) { list ->
            if (isFinishing || current !== s) return@withSubtitles
            val item = MediaItem.Builder().setUri(Uri.parse(s.url)).setSubtitleConfigurations(list.mapIndexed { i, x ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(x.url)).setMimeType(subMime(x.url)).setLanguage(x.lang)
                    .setLabel(x.lang.uppercase() + " (addon)").setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0).build() }).build()
            player.setMediaSource(PlayerEngine.mediaSourceFactory(this, s.headers).createMediaSource(item), positionMs)
            player.playWhenReady = true
            player.prepare()
        }
        handler.removeCallbacks(startupTimeout)
        if (autoSelected && Prefs.fallbackOn) handler.postDelayed(startupTimeout, Prefs.startupTimeoutSec * 1000L)
        if (!tickRunning) { tickRunning = true; handler.post(tick) }
    }

    /** The addon subtitles moved by [delay] ms (saved files, so the player reads them like any others); the original links if that fails. */
    private fun withSubtitles(list: List<Sub>, delay: Long, then: (List<Sub>) -> Unit) {
        if (delay == 0L || list.isEmpty()) { then(list); return }
        val dir = java.io.File(cacheDir, "subs").apply { mkdirs() }
        Http.background {
            val moved = list.mapIndexed { i, x ->
                try {
                    val ext = x.url.substringBefore('?').substringAfterLast('.', "srt").take(4)
                    val f = java.io.File(dir, "s${i}_${x.url.hashCode().toUInt()}_${delay}.$ext")
                    if (!f.exists()) f.writeText(SubtitleTools.shift(Http.fetchBlocking(x.url, 8_000, 12_000), delay, x.url))
                    Sub(Uri.fromFile(f).toString(), x.lang)
                } catch (_: Exception) { x }
            }
            Http.post { then(moved) }
        }
    }

    private fun onFirstFrame() {
        if (gotFirstFrame) return
        // A "file" of a few seconds is a stream addon's error clip (expired link, limit reached), not the title: try the next source.
        val clipMs = player.duration
        if (autoSelected && clipMs in 1 until MIN_REAL_MS && PlaySession.meta?.type != "channel") { failed("This source is only ${clipMs / 1000} seconds long, which is an error clip and not the title.", null, PlayErrors.Kind.FORMAT); return }
        gotFirstFrame = true
        handler.removeCallbacks(startupTimeout)
        PlayerEngine.matchFrameRate(this, player.videoFormat?.frameRate ?: 0f)?.let { bannerShow(it) }
        zoom = PlayerPrefs.zoom(PlaySession.meta?.id); applyZoom()
        val vid = PlaySession.videoId
        if (Prefs.skipIntro) Segments.load(vid) { if (vid == PlaySession.videoId) segs = it }
        hideSlate()
        scheduleHide()
        if (fallback != null && fallback!!.attempts > 0 && autoSelected) bannerShow("The first source failed. Playing the next best match.")
    }

    private fun onStartupTimeout() {
        if (gotFirstFrame || isFinishing) return
        failed(PlayErrors.describe(null, PlayErrors.Kind.NETWORK).let { "This source did not start within ${Prefs.startupTimeoutSec} seconds." }, null, PlayErrors.Kind.NETWORK)
    }

    private fun onError(e: PlaybackException) {
        lastError = "${e.errorCodeName}: ${e.message.orEmpty()}" + (e.cause?.let { " / ${it.javaClass.simpleName}: ${it.message.orEmpty()}" } ?: "")
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
        StreamResolver.resolve(StreamResolver.typeFor(PlaySession.meta!!.type, PlaySession.meta!!.id, PlaySession.videoId), PlaySession.videoId, 8000, s)
    }

    private fun onEnded() {
        saveProgress(force = true, ended = true)
        if (PlaySession.meta!!.type == "series" && Prefs.autoplayNext) upNext() else finish()
    }

    private var countdown: Runnable? = null

    /** When an episode ends: "Next episode in 5" with OK to start now and Back to stop. Only if there is a next episode. */
    private fun upNext() {
        val m = PlaySession.meta!!
        val idx = m.videos.indexOfFirst { it.id == PlaySession.videoId }
        val next = if (idx >= 0) m.videos.drop(idx + 1).firstOrNull { it.season > 0 || PlaySession.season == 0 } else null
        if (next == null) { finish(); return }
        var left = 5
        val tick = object : Runnable { override fun run() {
            if (left == 0) { countdown = null; playNextEpisode(manual = false); return }
            showSlate("Next: S${next.season} \u00b7 E${next.episode}  ${next.title}\nStarting in $left\nOK plays it now. Back stops.")
            left--; handler.postDelayed(this, 1000)
        } }
        countdown = tick; tick.run()
    }

    private fun cancelCountdown(playNow: Boolean) {
        countdown?.let { handler.removeCallbacks(it) }; countdown = null
        if (playNow) playNextEpisode(manual = false) else { hideSlate(); finish() }
    }

    private fun playNextEpisode(manual: Boolean) {
        val m0 = PlaySession.meta!!
        val idx0 = m0.videos.indexOfFirst { it.id == PlaySession.videoId }
        val next0 = if (idx0 >= 0) m0.videos.drop(idx0 + 1).firstOrNull { it.season > 0 || PlaySession.season == 0 } else null
        if (next0 == null) { if (manual) bannerShow("That was the last episode.") else finish(); return }
        playEpisode(next0)
    }

    /** Plays [next] with the best source, preferring the one that played the last episode (same binge group, else the same addon). */
    private fun playEpisode(next: Video) {
        val m = PlaySession.meta!!
        saveProgress(force = true)
        showSlate("Next: S${next.season} · E${next.episode}  ${next.title}\nFinding the best source…")
        val prevGroup = current.bingeGroup; val prevAddon = current.addonUrl
        val s = StreamResolver.Session(onUpdate = { _, _ -> }, onDone = { list, _ ->
            if (isFinishing) return@Session
            val max = Prefs.effectiveMaxResolution()
            val ranked = DeviceRank.forThisBox(StreamRank.sorted(StreamRank.playable(list, max), max))
            val pick = ranked.firstOrNull { prevGroup != null && it.bingeGroup == prevGroup } ?: ranked.firstOrNull { it.addonUrl == prevAddon } ?: ranked.firstOrNull()
            if (pick == null) { hideSlate(); showError("No sources were found for the next episode."); return@Session }
            PlaySession.videoId = next.id; PlaySession.season = next.season; PlaySession.episode = next.episode; PlaySession.episodeTitle = next.title
            PlaySession.ranked = ranked; PlaySession.current = pick; PlaySession.autoSelected = true; PlaySession.resumeMs = 0
            fallback = SourceFallback(ranked, Prefs.fallbackTries); fallback!!.markTried(pick)
            autoSelected = true; triedRefresh.clear()
            begin(pick, 0, "Starting stream via ${pick.addonName}…")
        })
        resolveSession = s
        StreamResolver.resolve(StreamResolver.typeFor(m.type, m.id, next.id), next.id, StreamResolver.DEFAULT_TIMEOUT_MS, s)
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
        val moved = panelKind == "Subtitle delay" && delayMs != delayAtOpen
        panel.visibility = View.GONE
        if (moved) { PlayerPrefs.setSubDelay(PlaySession.meta?.id, delayMs); startStream(current, player.currentPosition.coerceAtLeast(0), "Moving subtitles ${SubtitleTools.label(delayMs)}…") }
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
            val ranked = DeviceRank.forThisBox(StreamRank.sorted(list, max))
            if (ranked.isNotEmpty()) PlaySession.ranked = ranked
            bannerShow("Reloaded: ${ranked.size} sources from ${st.count { it.ok }} addons")
            if (panelOpen() && panelKind == "Sources") renderSources()
        })
        resolveSession = s
        StreamResolver.resolve(StreamResolver.typeFor(PlaySession.meta!!.type, PlaySession.meta!!.id, PlaySession.videoId), PlaySession.videoId, StreamResolver.DEFAULT_TIMEOUT_MS, s)
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
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                Prefs.rememberTrack(PlaySession.meta?.id, false, "off"); closePanel() })
        }
        if (type == C.TRACK_TYPE_TEXT) {
            panelList.addView(item("Delay", "For subtitles from addons; the ones inside the file cannot be moved.", SubtitleTools.label(delayMs), Ui.DIM) { openSubDelay() })
            panelList.addView(item("Size and background", null, "${PlayerPrefs.subSize}%  ·  ${PlayerPrefs.subBackground}", Ui.DIM) { openSubStyle() })
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
                    player.trackSelectionParameters = b.build()
                    f.language?.let { Prefs.rememberTrack(PlaySession.meta?.id, type == C.TRACK_TYPE_AUDIO, it) }
                    closePanel()
                })
            }
        }
        panelList.getChildAt(0)?.requestFocus()
    }

    /** Every episode of the show, by season; OK plays that one (with the best source), the one playing now has a tick. */
    private fun openEpisodes() {
        val m = PlaySession.meta!!
        openPanel("Episodes")
        val seasons = m.videos.map { it.season }.distinct().sorted()
        var shown = if (PlaySession.season in seasons) PlaySession.season else seasons.firstOrNull() ?: 0
        fun fillList() {
            panelChips.removeAllViews(); panelList.removeAllViews()
            seasons.forEach { s -> panelChips.addView(chip(if (s == 0) "Specials" else "Season $s") { shown = s; fillList() }.also { it.isSelected = s == shown }) }
            m.videos.filter { it.season == shown }.forEach { v ->
                val now = v.id == PlaySession.videoId
                panelList.addView(item("E${v.episode}  ${v.title}", null, if (now) "Playing" else null) { closePanel(); if (!now) playEpisode(v) })
            }
            (0 until panelList.childCount).map { panelList.getChildAt(it) }.firstOrNull()?.requestFocus()
        }
        fillList()
    }

    /** Draws the picture wider or taller than the fit, and moves it; the subtitles are kept the right shape and on the screen. */
    private fun applyZoom() {
        val sx = zoom.w / 100f; val sy = zoom.h / 100f
        val tx = zoom.x / 100f * root.width; val ty = zoom.y / 100f * root.height
        playerView.pivotX = root.width / 2f; playerView.pivotY = root.height / 2f
        playerView.scaleX = sx; playerView.scaleY = sy; playerView.translationX = tx; playerView.translationY = ty
        playerView.subtitleView?.let { sv ->
            sv.pivotX = root.width / 2f; sv.pivotY = root.height / 2f
            sv.scaleX = 1f / sx; sv.scaleY = 1f / sy; sv.translationX = -tx / sx; sv.translationY = -ty / sy
        }
    }

    private fun saveZoom() { zoom = zoom.clamped(); PlayerPrefs.setZoom(PlaySession.meta?.id, zoom); applyZoom() }

    private fun openZoom() { openPanel("Zoom"); zoomFocus = 0; renderZoom() }

    private fun signed(v: Int) = if (v > 0) "+$v%" else "$v%"

    private fun renderZoom() {
        panelList.removeAllViews()
        panelList.addView(Ui.text(this, "Left and right change the value. OK puts it back to normal. Kept for this title.", 13f, Ui.DIM), Ui.wrap().apply { bottomMargin = Ui.dp(this@PlayerActivity, 8) })
        listOf("Width" to "${zoom.w}%", "Height" to "${zoom.h}%", "Position left / right" to signed(zoom.x), "Position up / down" to signed(zoom.y)).forEachIndexed { i, (label, value) ->
            panelList.addView(item(label, null, value, Ui.DIM) {
                zoom = when (i) { 0 -> zoom.copy(w = 100); 1 -> zoom.copy(h = 100); 2 -> zoom.copy(x = 0); else -> zoom.copy(y = 0) }; zoomFocus = i; saveZoom(); renderZoom() })
        }
        panelList.addView(item("Fill the screen", "The same on both sides, so the black bars go.", null) { fillZoom() })
        panelList.addView(item("Reset all", null, null) { zoom = Zoom(); zoomFocus = 0; saveZoom(); renderZoom() })
        panelList.getChildAt(1 + zoomFocus.coerceIn(0, 5))?.requestFocus()
    }

    private fun adjustZoom(dir: Int, repeat: Int) {
        val row = (0 until panelList.childCount).indexOfFirst { panelList.getChildAt(it).hasFocus() } - 1
        if (row !in 0..3) return
        val step = (if (repeat < 5) 1 else if (repeat < 15) 2 else 5) * dir
        zoom = when (row) { 0 -> zoom.copy(w = zoom.w + step); 1 -> zoom.copy(h = zoom.h + step); 2 -> zoom.copy(x = zoom.x + step); else -> zoom.copy(y = zoom.y + step) }
        zoomFocus = row; saveZoom(); renderZoom()
    }

    private fun fillZoom() {
        val f = player.videoFormat
        val video = if (f != null && f.height > 0) f.width * f.pixelWidthHeightRatio / f.height else 0f
        zoom = Zoom.fill(video, root.width.toFloat() / root.height.coerceAtLeast(1)); zoomFocus = 4; saveZoom(); renderZoom()
    }

    private fun openSubDelay() {
        openPanel("Subtitle delay")
        delayAtOpen = delayMs
        fun step(label: String, by: Long) = panelList.addView(item(label, null, null) { delayMs = (delayMs + by).coerceIn(-30_000, 30_000); openSubDelayRefresh() })
        panelList.addView(Ui.text(this, "Now: ${SubtitleTools.label(delayMs)}. Later shows subtitles later. Close this to apply.", 13f, Ui.DIM), Ui.wrap().apply { bottomMargin = Ui.dp(this@PlayerActivity, 8) })
        step("Later  +0.1 s", 100); step("Later  +0.5 s", 500); step("Later  +1 s", 1000)
        step("Earlier  -0.1 s", -100); step("Earlier  -0.5 s", -500); step("Earlier  -1 s", -1000)
        panelList.addView(item("Reset", null, null) { delayMs = 0; openSubDelayRefresh() })
        panelList.getChildAt(1)?.requestFocus()
    }

    private fun openSubDelayRefresh() { val keep = delayAtOpen; openSubDelay(); delayAtOpen = keep }

    private fun openSubStyle() {
        openPanel("Subtitle look")
        val sizes = listOf(80, 100, 125, 150, 200); val backs = listOf("Shadow", "Dim", "Solid")
        panelList.addView(item("Size", null, "${PlayerPrefs.subSize}%", Ui.DIM) {
            PlayerPrefs.subSize = sizes[(sizes.indexOf(PlayerPrefs.subSize).coerceAtLeast(0) + 1) % sizes.size]; playerView.subtitleView?.let { PlayerEngine.styleSubtitles(it) }; openSubStyle() })
        panelList.addView(item("Background", null, PlayerPrefs.subBackground, Ui.DIM) {
            PlayerPrefs.subBackground = backs[(backs.indexOf(PlayerPrefs.subBackground).coerceAtLeast(0) + 1) % backs.size]; playerView.subtitleView?.let { PlayerEngine.styleSubtitles(it) }; openSubStyle() })
        panelList.getChildAt(0)?.requestFocus()
    }

    /** One press of the Subtitles or Audio key on the remote: the next track (subtitles: ... then off), with its name on screen. */
    private fun cycleTrack(type: Int) {
        val all = player.currentTracks.groups.filter { it.type == type && it.length > 0 }.flatMap { g -> (0 until g.length).map { g to it } }
        if (all.isEmpty()) { bannerShow(if (type == C.TRACK_TYPE_TEXT) "No subtitles" else "No other audio"); return }
        val off = type == C.TRACK_TYPE_TEXT && player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val cur = if (off) -1 else all.indexOfFirst { (g, i) -> g.isTrackSelected(i) }
        val next = cur + 1
        if (type == C.TRACK_TYPE_TEXT && next >= all.size) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            Prefs.rememberTrack(PlaySession.meta?.id, false, "off"); bannerShow("Subtitles off"); return
        }
        val (g, i) = all[next % all.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        val f = g.getTrackFormat(i)
        f.language?.let { Prefs.rememberTrack(PlaySession.meta?.id, type == C.TRACK_TYPE_AUDIO, it) }
        val name = f.language?.let { java.util.Locale(it).displayLanguage } ?: "Track ${next % all.size + 1}"
        bannerShow((if (type == C.TRACK_TYPE_TEXT) "Subtitles: " else "Audio: ") + name + (f.label?.let { " · $it" } ?: ""))
    }

    private fun toggleStats() {
        PlayerPrefs.stats = !PlayerPrefs.stats
        statsText.visibility = if (PlayerPrefs.stats) View.VISIBLE else View.GONE
        bannerShow(if (PlayerPrefs.stats) "Playback stats on" else "Playback stats off")
    }

    /** The text to send when something would not play: this box, the app, the source (its link with the secrets cut out), the error and the live numbers. */
    private fun reportText(): String {
        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        val caps = DeviceCaps.read(this)
        return PlayerReport.build(listOf(
            "App" to "My Lists TV $v", "Device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}",
            "Decoders" to "HEVC ${caps.hevc}, AV1 ${caps.av1}, Dolby Vision ${caps.dolbyVision}, HDR screen ${caps.displayHdr}",
            "Title" to (PlaySession.meta?.name.orEmpty() + if (PlaySession.season >= 0) " S${PlaySession.season}E${PlaySession.episode}" else ""),
            "Source" to "${current.addonName} ${if (current.resolution > 0) "${current.resolution}p" else ""}".trim(),
            "File" to (current.filename ?: ""), "Link" to PlayerReport.redact(current.url),
            "Error" to lastError, "Position" to fmt(player.currentPosition.coerceAtLeast(0)),
        ) + stats.lines(player))
    }

    private fun openReport() {
        openPanel("Report a problem")
        val text = reportText()
        panelList.addView(Ui.text(this, "Nothing here names you or your account. Copy it into a message, or find the saved file with a file manager.", 13f, Ui.DIM), Ui.wrap().apply { bottomMargin = Ui.dp(this@PlayerActivity, 8) })
        panelList.addView(item("Copy to clipboard", null, null) {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Playback report", text)); bannerShow("Copied") })
        panelList.addView(item("Save to a file", null, null) {
            val f = java.io.File(getExternalFilesDir(null) ?: filesDir, "playback-report.txt"); try { f.writeText(text); bannerShow("Saved: ${f.absolutePath}") } catch (_: Exception) { bannerShow("Could not save the file") } })
        text.lines().forEach { panelList.addView(Ui.text(this, it, 11f, Ui.TEXT2, false, 3)) }
        panelList.getChildAt(1)?.requestFocus()
    }

    private fun openSpeed() {
        openPanel("Playback speed")
        speeds.forEachIndexed { i, sp ->
            panelList.addView(item("${sp}x", null, if (i == speedIdx) "Selected" else null) {
                speedIdx = i; player.setPlaybackSpeed(sp); closePanel(); bannerShow(if (sp == 1f) "Normal speed" else "Speed ${sp}x") })
        }
        panelList.getChildAt(speedIdx)?.requestFocus()
    }

    private fun openInfo() {
        openPanel("Stream info")
        val f = player.videoFormat
        listOf("Source" to current.addonName, "File" to (current.filename ?: "-"), "Quality" to (if (current.resolution > 0) "${current.resolution}p" else "unknown"),
            "Size" to (if (current.sizeBytes > 0) "%.1f GB".format(current.sizeBytes / 1_073_741_824.0) else "-"),
            "Video" to (f?.let { "${it.width}x${it.height} ${it.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""}" } ?: "-"),
            "Player" to "Media3 (hardware, software sound decoder when needed)").plus(stats.lines(player).filter { it.first in setOf("Video decoder", "Audio", "Audio decoder", "Dropped frames", "Network", "Buffered") }).forEach { (k, v) ->
            panelList.addView(Ui.text(this, k, 11f, Ui.DIM)); panelList.addView(Ui.text(this, v, 14f, Ui.TEXT, true, 2), Ui.wrap().apply { bottomMargin = Ui.dp(this@PlayerActivity, 6) })
        }
        panelList.addView(item("Live stats on the picture", null, if (PlayerPrefs.stats) "On" else "Off", Ui.DIM) { toggleStats(); closePanel() })
        panelList.addView(item("Report a problem", null, null) { openReport() })
    }

    /** Hands the stream to another video app at this place, and when it comes back picks up where that app stopped. */
    private fun openExternal() {
        externalFrom = player.currentPosition.coerceAtLeast(0)
        player.pause()
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(ExternalPlayer.intent(current.url, PlaySession.meta?.name.orEmpty(), externalFrom), "Play with"), REQ_EXTERNAL)
        } catch (e: Exception) { bannerShow("No other video app found.") }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXTERNAL) return
        val ex = data?.extras?.let { b -> b.keySet().associateWith { k -> b.get(k) } } ?: emptyMap()
        val at = ExternalPlayer.stoppedAt(ex) ?: return
        val m = PlaySession.meta ?: return
        val dur = (ex["duration"] as? Number)?.toLong()?.takeIf { it > 0 } ?: player.duration.takeIf { it > 0 } ?: return
        WatchStore.save(WatchStore.Entry(m.id, m.type, m.name, m.poster, m.background, PlaySession.videoId, PlaySession.season, PlaySession.episode, PlaySession.episodeTitle, at, dur, System.currentTimeMillis()))
        if (at.toFloat() / dur >= 0.9f) Watched.set(m.id, PlaySession.videoId.ifEmpty { m.id }, true)
        finish()
    }

    // ---------------------------------------------------------------- helpers

    /** The loading screen shows the title's own backdrop behind a dark fade. */
    private fun showSlate(msg: String) {
        slateText.text = msg; slate.visibility = View.VISIBLE; bottom.visibility = View.GONE
        val art = PlaySession.meta?.let { it.background ?: it.poster }
        if (art != null && slateBg.visibility != View.VISIBLE) { slateBg.visibility = View.VISIBLE; ImageLoader.load(slateBg, art, Ui.dp(this, 960), Ui.dp(this, 540), fade = !Prefs.lite()) }
        slate.setBackgroundColor(if (art != null) Ui.SCRIM_SLATE else Ui.OVERLAY)
    }
    private fun hideSlate() { slate.visibility = View.GONE; slateBg.visibility = View.GONE }
    private fun showError(msg: String) { hideSlate(); errorText.text = msg; errorBox.visibility = View.VISIBLE; errorBox.getChildAt(2)?.let { (it as LinearLayout).getChildAt(0).requestFocus() } }
    private fun hideError() { errorBox.visibility = View.GONE }

    private fun bannerShow(text: String) {
        banner.text = text; banner.visibility = View.VISIBLE
        handler.removeCallbacks(bannerHide); handler.postDelayed(bannerHide, 3600)
    }
    private val bannerHide = Runnable { banner.visibility = View.GONE }

    private fun showControls() { if (slate.visibility != View.VISIBLE) bottom.visibility = View.VISIBLE; scheduleHide() }
    private fun hideBarNow() { handler.removeCallbacks(hideControls); bottom.visibility = View.GONE; root.requestFocus() }
    private fun scheduleHide() { handler.removeCallbacks(hideControls); handler.postDelayed(hideControls, 5000) }

    private fun fmt(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

    private fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    /** The segment under the playhead, or null; also what the skip button says and does. */
    private fun skipTarget(): Segments.Seg? = if (!Prefs.skipIntro || panelOpen() || errorOpen() || slate.visibility == View.VISIBLE) null else Segments.at(segs, player.currentPosition)

    /** The skip button sits just above the control bar while it shows, and low in the corner while it is hidden. */
    private fun placeSkip() {
        val lp = skipBtn.layoutParams as FrameLayout.LayoutParams
        val want = if (bottom.visibility == View.VISIBLE && bottom.height > 0) bottom.height - titleText.top + Ui.dp(this, 12) else Ui.dp(this, 48)
        if (lp.bottomMargin != want) { lp.bottomMargin = want; skipBtn.layoutParams = lp }
    }

    private fun updateSkip() {
        val s = skipTarget()
        placeSkip()
        val was = skipBtn.visibility == View.VISIBLE
        skipBtn.visibility = if (s == null) View.GONE else View.VISIBLE
        if (s != null) {
            skipBtn.text = when (s.kind) { "intro" -> "Skip intro"; "recap" -> "Skip recap"; else -> if (PlaySession.meta?.type == "series" && Prefs.autoplayNext) "Next episode" else "Skip credits" }
            // It appears already highlighted, so OK skips it. The control bar stays out of the way unless the viewer opens it.
            if (!was && !controls.hasFocus() && !panelOpen()) skipBtn.requestFocus()
        } else if (was && skipBtn.hasFocus()) root.requestFocus()
    }

    private fun doSkip(s: Segments.Seg) {
        if (s.kind == "credits" && PlaySession.meta?.type == "series" && Prefs.autoplayNext) playNextEpisode(manual = false) else seekBy(s.endMs - player.currentPosition)
    }

    private fun updateProgress() {
        val now = System.currentTimeMillis(); val fmtT = android.text.format.DateFormat.getTimeFormat(this)
        clockText.text = fmtT.format(java.util.Date(now))
        player.duration.takeIf { it > 0 }?.let { endsText.text = "Ends at " + fmtT.format(java.util.Date(now + ((it - player.currentPosition) / player.playbackParameters.speed).toLong())) }
        updateSkip()
        val dur = player.duration
        val pos = player.currentPosition
        if (PlayerPrefs.stats && statsText.visibility == View.VISIBLE) statsText.text = stats.overlay(player)
        if (dur > 0) marks.marks = segs.filter { it.startMs > 0 }.map { it.startMs.toFloat() / dur }
        if (dur > 0) {
            (fill.layoutParams as FrameLayout.LayoutParams).width = (track.width * (pos.toFloat() / dur)).toInt().coerceIn(0, track.width)
            fill.requestLayout()
            timeText.text = "${fmt(pos)} / ${fmt(dur)}"
        } else timeText.text = fmt(pos)
        saveProgress(force = false)
    }

    private fun saveProgress(force: Boolean, ended: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 15_000) return
        if (!gotFirstFrame && !ended) return
        val dur = player.duration
        if (dur <= 0) return
        lastSave = now
        val m = PlaySession.meta!!
        val pos = if (ended) dur else player.currentPosition
        val entry = WatchStore.Entry(m.id, m.type, m.name, m.poster, m.background, PlaySession.videoId, PlaySession.season, PlaySession.episode,
            PlaySession.episodeTitle, pos, dur, now)
        WatchStore.save(entry)
        if ((m.type == "series" || m.type == "movie") && entry.fraction >= 0.9f) Watched.set(m.id, PlaySession.videoId.ifEmpty { m.id }, true)
        Progress.report(entry, force)
    }

    // ---------------------------------------------------------------- keys and lifecycle

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (PlaySession.resolving) return super.dispatchKeyEvent(e)
        if (countdown != null && e.action == KeyEvent.ACTION_DOWN) {
            if (e.keyCode == KeyEvent.KEYCODE_BACK) { cancelCountdown(false); return true }
            if (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER) { cancelCountdown(true); return true }
        }
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_MENU -> { if (!errorOpen()) openSources(); return true }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_PROG_YELLOW -> { if (!errorOpen() && resumeBox.visibility != View.VISIBLE) cycleTrack(C.TRACK_TYPE_TEXT); return true }
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_PROG_BLUE -> { if (!errorOpen() && resumeBox.visibility != View.VISIBLE) cycleTrack(C.TRACK_TYPE_AUDIO); return true }
                KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_PROG_GREEN -> { toggleStats(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000); return true }
            }
            if (panelOpen() && panelKind == "Zoom" && (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) { adjustZoom(if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1, e.repeatCount); return true }
            if (panelOpen() || errorOpen() || slate.visibility == View.VISIBLE) return super.dispatchKeyEvent(e)
            val inControls = controls.hasFocus()
            if (!inControls) {
                // OK on the skip button skips, and nothing else happens: no control bar.
                if (skipBtn.visibility == View.VISIBLE && (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER)) { skipTarget()?.let { doSkip(it) }; hideBarNow(); return true }
                if (e.keyCode != KeyEvent.KEYCODE_BACK) showControls()   // Back closes the bar, so it must not open it first
                when (e.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { seekStep(e, -1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { seekStep(e, 1); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { skipTarget()?.let { doSkip(it) } ?: togglePlay(); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { bottom.visibility = View.VISIBLE; controls.getChildAt(0)?.requestFocus(); return true }
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
        seekBubble.text = (if (deltaMs >= 0) "+" else "-") + (kotlin.math.abs(deltaMs) / 1000) + " s   →   " + fmt(t) + if (dur > 0) " / ${fmt(dur)}" else ""
        seekBubble.visibility = View.VISIBLE; handler.removeCallbacks(hideBubble); handler.postDelayed(hideBubble, 1400)
    }
    private val hideBubble = Runnable { seekBubble.visibility = View.GONE }

    @Deprecated("Back closes the panel, then the controls, then the player.")
    override fun onBackPressed() {
        when {
            PlaySession.resolving -> { resolveSession?.cancelled = true; PlaySession.resolving = false; finish() }
            resumeBox.visibility == View.VISIBLE -> finish()
            panelOpen() -> closePanel()
            errorOpen() -> finish()
            bottom.visibility == View.VISIBLE -> hideBarNow()   // the bar first; Back again leaves
            else -> { saveProgress(force = true); finish() }
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress(force = true)
        WatchNext.publish(this)
        player.pause()
    }

    override fun onDestroy() {
        tickRunning = false
        handler.removeCallbacksAndMessages(null)
        resolveSession?.cancelled = true
        PlayerEngine.restoreDisplay(this)
        player.removeAnalyticsListener(stats)
        player.release()
        super.onDestroy()
    }
}
