package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import tv.myliststv.launcher.data.PlayerPrefs
import tv.myliststv.launcher.data.Prefs
import kotlin.math.abs
import kotlin.math.roundToInt

/** The parts of the player that read the viewer's settings: decoders, buffer, network, subtitle look, and the display mode. */
@OptIn(UnstableApi::class)
object PlayerEngine {

    /** Sound: passthrough as the TV allows (default) or always decoded here; the software (FFmpeg) decoder covers DTS, TrueHD and the like. */
    private class Renderers(c: Context, private val passthrough: Boolean) : DefaultRenderersFactory(c) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
            if (passthrough) super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
            else DefaultAudioSink.Builder(context)
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)   // PCM only: nothing is sent to the TV as Dolby or DTS
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
    }

    fun renderers(c: Context): DefaultRenderersFactory = Renderers(c, PlayerPrefs.passthrough)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(
            if (PlayerPrefs.softwareAudio == "First") DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        )

    /** How much video to keep ahead: (milliseconds, bytes). A weak box keeps the small buffer whatever is chosen (a big one would run it out of memory). */
    fun bufferPlan(sec: Int, lite: Boolean): Pair<Int, Int> = when {
        lite -> 50_000 to 32 * 1024 * 1024
        sec >= 180 -> 180_000 to 150 * 1024 * 1024
        sec >= 100 -> 100_000 to 96 * 1024 * 1024
        else -> 50_000 to 50 * 1024 * 1024
    }

    fun loadControl(): DefaultLoadControl {
        val (ms, bytes) = bufferPlan(PlayerPrefs.bufferSec, Prefs.lite())
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, ms, 1_500, 3_000)
            .setTargetBufferBytes(bytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }

    /**
     * Files and streams over HTTP (and the subtitle files saved on the box). Range requests and redirects are handled by the data source;
     * a dropped connection is retried eight times (the default is three) before the player reports the failure.
     */
    fun mediaSourceFactory(c: Context, headers: Map<String, String>): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MyListsTV/0.1")
            .setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(c, http), DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(8))
    }

    /** Subtitle size and background from the settings. */
    fun styleSubtitles(v: SubtitleView) {
        v.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * PlayerPrefs.subSize / 100f)
        val (bg, edge) = when (PlayerPrefs.subBackground) {
            "Solid" -> Ui.BG to CaptionStyleCompat.EDGE_TYPE_NONE
            "Dim" -> Ui.SCRIM_SLATE to CaptionStyleCompat.EDGE_TYPE_NONE
            else -> Ui.CLEAR to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        }
        v.setStyle(CaptionStyleCompat(Ui.TEXT, bg, Ui.CLEAR, edge, Ui.BG, null))
        v.setBottomPaddingFraction(0.08f)
    }

    // ------------------------------------------------------------ display

    class Mode(val id: Int, val w: Int, val h: Int, val rate: Float)

    /**
     * The display mode whose refresh rate is a whole multiple of the film's frame rate (24 fps on 24 or 48 Hz, 23.976 on 23.976 Hz, 25 on 50 Hz),
     * at the size the screen is already using, or null when the current one already fits or none does. The lowest multiple wins.
     */
    fun chooseMode(modes: List<Mode>, current: Mode, fps: Float): Mode? {
        if (fps < 10f) return null
        fun fit(m: Mode): Int? { val k = (m.rate / fps).roundToInt(); return if (k >= 1 && abs(m.rate - fps * k) / m.rate < 0.01f) k else null }
        if (fit(current) != null) return null
        return modes.filter { it.w == current.w && it.h == current.h }
            .mapNotNull { m -> fit(m)?.let { k -> Triple(m, k, abs(m.rate - fps * k)) } }
            .minWithOrNull(compareBy({ it.second }, { it.third }))?.first
    }

    /** Switches the screen to a mode matching [fps] when the setting is "Always". Returns what changed (for the banner) or null. */
    @Suppress("DEPRECATION")
    fun matchFrameRate(a: Activity, fps: Float): String? {
        if (PlayerPrefs.frameRate != "Always" || Build.VERSION.SDK_INT < 23 || fps <= 0f) return null
        val d = a.windowManager.defaultDisplay
        val cur = d.mode.let { Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val pick = chooseMode(d.supportedModes.map { Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }, cur, fps) ?: return null
        val lp: WindowManager.LayoutParams = a.window.attributes
        lp.preferredDisplayModeId = pick.id
        a.window.attributes = lp
        return "Screen set to ${"%.2f".format(pick.rate).trimEnd('0').trimEnd('.')} Hz"
    }

    /** Puts the screen back the way it was. */
    fun restoreDisplay(a: Activity) {
        if (Build.VERSION.SDK_INT < 23) return
        val lp = a.window.attributes
        if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; a.window.attributes = lp }
    }

    /** "SDR" asks Android for a standard-range screen, so an HDR film is mapped down by the decoder; "HDR" asks for the wide-range one. */
    fun applyColorMode(a: Activity) {
        if (Build.VERSION.SDK_INT < 26) return
        a.window.colorMode = when (PlayerPrefs.displayHdr) {
            "SDR" -> android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            "HDR" -> android.content.pm.ActivityInfo.COLOR_MODE_HDR
            else -> a.window.colorMode
        }
    }

    /** What the frame-rate setting tells ExoPlayer to do on its own. */
    fun frameRateStrategy(): Int = when (PlayerPrefs.frameRate) {
        "Off", "Always" -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF   // "Always" is done by [matchFrameRate]
        else -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
    }

}
