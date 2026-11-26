package tv.myliststv.launcher.data

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.view.WindowManager
import java.util.Locale

/** What this box and its screen can show, read once. Pure data so the ranking can be tested with any combination. */
data class DeviceCaps(
    val hevc: Boolean = true,
    val av1: Boolean = false,
    val dolbyVision: Boolean = false,
    val hdr10: Boolean = false,
    /** The screen the box is plugged into reports HDR (or the viewer said it does). */
    val displayHdr: Boolean = false,
    val displayDolbyVision: Boolean = false,
    val max2160: Boolean = true,
) {
    companion object {
        @Volatile private var cached: DeviceCaps? = null

        /** Reads the decoders and the screen. The screen can change (another TV), so only the decoders are kept. */
        fun read(c: Context): DeviceCaps {
            val decoders = cached ?: decoders().also { cached = it }
            val hdrTypes = hdrTypes(c)
            val shows = when (PlayerPrefs.displayHdr) { "HDR" -> true; "SDR" -> false; else -> hdrTypes.isNotEmpty() }
            return decoders.copy(
                displayHdr = shows,
                displayDolbyVision = PlayerPrefs.displayHdr == "HDR" || 1 in hdrTypes,
                hdr10 = decoders.hdr10 && shows,
            )
        }

        private fun decoders(): DeviceCaps {
            val list = try { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } } catch (_: Throwable) { return DeviceCaps() }
            fun has(mime: String) = list.any { i -> i.supportedTypes.any { it.equals(mime, true) } }
            val hevcProfiles = try {
                list.filter { it.supportedTypes.any { t -> t.equals("video/hevc", true) } }
                    .flatMap { it.getCapabilitiesForType("video/hevc").profileLevels.toList() }.map { it.profile }
            } catch (_: Throwable) { emptyList() }
            val uhd = try {
                list.filter { it.supportedTypes.any { t -> t.equals("video/hevc", true) || t.equals("video/avc", true) } }
                    .any { i -> i.supportedTypes.filter { it.equals("video/hevc", true) }.any { i.getCapabilitiesForType(it).videoCapabilities?.isSizeSupported(3840, 2160) == true } }
            } catch (_: Throwable) { true }
            return DeviceCaps(
                hevc = has("video/hevc"), av1 = has("video/av01"), dolbyVision = has("video/dolby-vision"),
                hdr10 = 4096 in hevcProfiles,   // HEVC Main10 HDR10
                max2160 = uhd,
            )
        }

        /** The HDR kinds the screen says it plays (Display.HdrCapabilities: 1 Dolby Vision, 2 HDR10, 3 HLG, 4 HDR10+). Empty on a plain screen. */
        @Suppress("DEPRECATION")
        private fun hdrTypes(c: Context): List<Int> = try {
            val d: Display = (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            if (Build.VERSION.SDK_INT >= 24) d.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty() else emptyList()
        } catch (_: Throwable) { emptyList() }
    }
}

/** What a stream's own text says about how it is encoded. Addon text is loose, so this only reads the common spellings. */
data class StreamTraits(val dolbyVision: Boolean, val hdr10: Boolean, val av1: Boolean, val hevc: Boolean, val remux: Boolean, val hdr: Boolean) {
    companion object {
        fun of(text: String): StreamTraits {
            val t = " " + text.lowercase(Locale.ROOT).replace(Regex("""[._\-\[\]()]"""), " ") + " "
            val dv = " dv " in t || "dolby vision" in t || "dolbyvision" in t || " dovi " in t
            val hdr10 = "hdr10" in t || " hdr " in t
            return StreamTraits(
                dolbyVision = dv, hdr10 = hdr10, hdr = dv || hdr10 || "hlg" in t,
                av1 = " av1 " in t, hevc = " hevc " in t || " x265 " in t || " h265 " in t || " h 265 " in t,
                remux = "remux" in t,
            )
        }
    }
}

/** Ranks streams by whether this box and screen can actually show them, so a file that cannot play is not the first pick. */
object DeviceRank {
    /**
     * 0 = fine; larger = worse. Dolby Vision on a box with no Dolby Vision decoder (most DV files are profile 5, which looks green and
     * purple without it unless the file also carries an HDR10 layer, which we cannot tell from the name), AV1 with no AV1 decoder, and
     * HDR on a screen without HDR are pushed down, not removed: the viewer can still pick them by hand.
     */
    fun penalty(t: StreamTraits, caps: DeviceCaps): Int {
        var p = 0
        if (t.av1 && !caps.av1) p += 4
        if (t.dolbyVision && !caps.dolbyVision) p += if (t.hdr10) 1 else 3
        if (t.hdr && !caps.displayHdr) p += 1
        if (t.hevc && !caps.hevc) p += 4
        return p
    }

    /** [sorted] for this box and screen, or the list as it is when the viewer turned the setting off. */
    fun forThisBox(ranked: List<StreamItem>): List<StreamItem> =
        if (PlayerPrefs.rankByDevice) sorted(ranked, DeviceCaps.read(tv.myliststv.launcher.App.instance)) else ranked

    /** A penalty this high means the file will probably not play right here. */
    const val WONT_PLAY = 3

    fun penaltyOf(s: StreamItem, caps: DeviceCaps) = penalty(StreamTraits.of("${s.name} ${s.title} ${s.filename.orEmpty()}"), caps)

    /**
     * Stable: only files that probably will not play here move down; everything else keeps the order the addons and [StreamRank.sorted]
     * gave (cached first, the addon's own order), so a mild difference such as HDR on a plain screen never beats a cached file.
     */
    fun sorted(ranked: List<StreamItem>, caps: DeviceCaps): List<StreamItem> =
        ranked.withIndex().sortedWith(compareBy({ if (penaltyOf(it.value, caps) >= WONT_PLAY) 1 else 0 }, { it.index })).map { it.value }
}
