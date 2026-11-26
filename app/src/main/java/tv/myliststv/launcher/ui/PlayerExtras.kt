package tv.myliststv.launcher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.Locale

/** Collects what the player says about itself (decoders, dropped frames, network speed) and writes it as lines for the stats overlay and the report. */
@OptIn(UnstableApi::class)
class PlayerStats : AnalyticsListener {
    var dropped = 0; private set
    var videoDecoder = ""; private set
    var audioDecoder = ""; private set
    private var mbps = 0.0

    fun reset() { dropped = 0; videoDecoder = ""; audioDecoder = ""; mbps = 0.0 }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) { dropped += droppedFrames }
    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) { mbps = bitrateEstimate / 1_000_000.0 }

    private fun codec(mime: String?) = mime?.substringAfter('/')?.uppercase(Locale.ROOT)?.replace("X-", "") ?: "?"
    private fun soft(name: String) = if (name.startsWith("ffmpeg", true) || name.startsWith("c2.android") || name.startsWith("OMX.google")) " (software)" else ""

    fun lines(p: ExoPlayer): List<Pair<String, String>> {
        val v = p.videoFormat; val a = p.audioFormat
        val range = when (v?.colorInfo?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10"; C.COLOR_TRANSFER_HLG -> "HLG"; null -> ""; else -> "SDR" }
        val rate = v?.frameRate?.takeIf { it > 0 }?.let { "%.3f".format(Locale.ROOT, it).trimEnd('0').trimEnd('.') + " fps" }.orEmpty()
        val vbits = v?.bitrate?.takeIf { it > 0 }?.let { "%.1f Mbps".format(Locale.ROOT, it / 1_000_000.0) }.orEmpty()
        return listOf(
            "Video" to (v?.let { listOf("${it.width}x${it.height}", codec(it.sampleMimeType), range, rate, vbits).filter { s -> s.isNotEmpty() }.joinToString(" ") } ?: "-"),
            "Video decoder" to (videoDecoder + soft(videoDecoder)).ifBlank { "-" },
            "Audio" to (a?.let { listOf(codec(it.sampleMimeType), if (it.channelCount > 0) "${it.channelCount}ch" else "", if (it.sampleRate > 0) "${it.sampleRate / 1000} kHz" else "").filter { s -> s.isNotEmpty() }.joinToString(" ") } ?: "-"),
            "Audio decoder" to (audioDecoder + soft(audioDecoder)).ifBlank { "-" },
            "Dropped frames" to dropped.toString(),
            "Buffered" to "${p.totalBufferedDuration / 1000} s ahead",
            "Network" to if (mbps > 0) "%.1f Mbps".format(Locale.ROOT, mbps) else "-",
        )
    }

    /** What the overlay shows: short lines, one fact each. */
    fun overlay(p: ExoPlayer): String = lines(p).joinToString("\n") { "${it.first}: ${it.second}" }
}

/** Small tick marks on the progress bar, where an intro, recap or credits start. */
class SeekMarks(c: Context) : View(c) {
    private val paint = Paint().apply { color = Ui.TEXT }
    private val w = Ui.dp(c, 3).toFloat()
    var marks: List<Float> = emptyList()
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        for (f in marks) { val x = (width * f).coerceIn(0f, width - w); canvas.drawRect(x, 0f, x + w, height.toFloat(), paint) }
    }
}

/** Handing a stream to another video app (VLC, MX Player, Just Player and the like), with the place to start from and, if the app tells us, where it stopped. */
object ExternalPlayer {
    fun intent(url: String, title: String, positionMs: Long): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
        .putExtra("title", title)
        .putExtra("position", positionMs.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())   // MX Player and Just Player: milliseconds
        .putExtra("return_result", true)                                                                    // MX Player: say where it stopped
        .putExtra("from_start", false)                                                                      // VLC: honour the position above

    /** The place the other app stopped at, in ms, from the extras it sent back (they use several names and number types); null when it said nothing. */
    fun stoppedAt(extras: Map<String, Any?>): Long? {
        for (k in listOf("extra_position", "position", "stop_position")) {
            val v = extras[k]
            val n = (v as? Number)?.toLong() ?: continue
            if (n > 0) return n
        }
        return null
    }

}
