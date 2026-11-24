package tv.myliststv.launcher.data

import java.util.Locale

/** Reading quality out of addon stream text, ranking, and the next-source choice for fallback. */
object StreamRank {

    private val sizeRe = Regex("""(\d+(?:[.,]\d+)?)\s?(GB|GiB|MB|MiB)""", RegexOption.IGNORE_CASE)

    fun resolution(text: String): Int {
        val t = text.lowercase(Locale.ROOT)
        return when {
            "2160p" in t || "4k" in t || "uhd" in t -> 2160
            "1440p" in t -> 1440
            "1080p" in t || "1080i" in t || "fhd" in t -> 1080
            "720p" in t || "hd720" in t -> 720
            "576p" in t -> 576
            "480p" in t || "dvd" in t -> 480
            else -> 0
        }
    }

    fun sizeBytes(text: String): Long {
        val m = sizeRe.find(text) ?: return 0L
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val unit = m.groupValues[2].uppercase(Locale.ROOT)
        val mult = if (unit.startsWith("G")) 1_073_741_824.0 else 1_048_576.0
        return (n * mult).toLong()
    }

    fun hasHdr(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return "hdr" in t || "dolby vision" in t || " dv " in t || "hdr10" in t
    }

    /** Debrid addons mark cached files in several ways, for example [RD+], [TB+], a lightning bolt, or "cached". */
    fun looksCached(name: String, title: String): Boolean {
        val t = "$name $title"
        return t.contains("+]") || t.contains("⚡") || t.contains("cached", ignoreCase = true)
    }

    /**
     * The order sources are tried in. Like Nuvio, it trusts each addon's own order (a stream addon already sorts its answers, and the
     * addons are in the order you set): cached files first, resolutions above [maxResolution] last (kept, so they can still be picked
     * by hand), everything else as the addons gave it. It does not prefer the biggest file: a huge remux is the one that stalls a box.
     */
    fun sorted(streams: List<StreamItem>, maxResolution: Int): List<StreamItem> {
        val cap = if (maxResolution <= 0) Int.MAX_VALUE else maxResolution
        return streams.withIndex().sortedWith(
            compareBy<IndexedValue<StreamItem>> { if (it.value.resolution > cap) 1 else 0 }
                .thenByDescending { it.value.cached }
                .thenBy { it.index }
        ).map { it.value }
    }

    /** Streams this device is asked to play first: unknown resolution is treated as playable. */
    fun playable(streams: List<StreamItem>, maxResolution: Int): List<StreamItem> {
        if (maxResolution <= 0) return streams
        val within = streams.filter { it.resolution <= maxResolution }
        return if (within.isEmpty()) streams else within
    }
}

/**
 * Chooses the next source when one fails to start. Never repeats a source already tried,
 * and treats the same file from the same addon as one source.
 */
class SourceFallback(private val ranked: List<StreamItem>, val maxExtraTries: Int) {
    private val tried = LinkedHashSet<String>()
    var attempts = 0
        private set

    fun markTried(s: StreamItem) { tried += s.key }
    fun wasTried(s: StreamItem) = s.key in tried

    /** The next stream to try after [failed], or null when none is left or the limit is reached. */
    fun next(failed: StreamItem): StreamItem? {
        tried += failed.key
        if (attempts >= maxExtraTries) return null
        val n = ranked.firstOrNull { it.key !in tried } ?: return null
        attempts++
        tried += n.key
        return n
    }
}
