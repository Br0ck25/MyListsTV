package tv.myliststv.launcher.data

import java.util.Locale

/** Moving a subtitle file's times earlier or later (SubRip, WebVTT and SubStation), as text in, text out. */
object SubtitleTools {
    // 00:01:02,345  or  00:01:02.345  or (WebVTT only) 01:02.345
    private val cueTime = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})([.,])(\d{1,3})""")
    // SubStation: H:MM:SS.cc in the Start and End fields of a Dialogue line.
    private val assLine = Regex("""^(\s*Dialogue:\s*[^,]*,)(\d+:\d{2}:\d{2}\.\d{2}),(\d+:\d{2}:\d{2}\.\d{2})(,.*)$""")

    fun shift(text: String, deltaMs: Long, url: String): String {
        if (deltaMs == 0L) return text
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".ass") || path.endsWith(".ssa") -> shiftAss(text, deltaMs)
            else -> shiftCues(text, deltaMs)
        }
    }

    /** SubRip and WebVTT: only lines with an arrow are timings, so the words themselves are never touched. */
    fun shiftCues(text: String, deltaMs: Long): String = text.lineSequence().joinToString("\n") { line ->
        if ("-->" !in line) line else cueTime.replace(line) { m ->
            val h = m.groupValues[1].toLongOrNull()
            val ms = (h ?: 0) * 3_600_000 + m.groupValues[2].toLong() * 60_000 + m.groupValues[3].toLong() * 1000 + frac(m.groupValues[5])
            val t = (ms + deltaMs).coerceAtLeast(0)
            val hh = t / 3_600_000
            // WebVTT may leave the hours out; keep that form unless the shifted time needs them.
            val hours = if (h != null || hh > 0) "%02d:".format(hh) else ""
            hours + "%02d:%02d%s%03d".format(t / 60_000 % 60, t / 1000 % 60, m.groupValues[4], t % 1000)
        }
    }

    fun shiftAss(text: String, deltaMs: Long): String = text.lineSequence().joinToString("\n") { line ->
        val m = assLine.matchEntire(line.trimEnd('\r')) ?: return@joinToString line
        m.groupValues[1] + assTime(m.groupValues[2], deltaMs) + "," + assTime(m.groupValues[3], deltaMs) + m.groupValues[4]
    }

    private fun frac(s: String) = (s + "00").take(3).toLong()

    private fun assTime(s: String, deltaMs: Long): String {
        val p = s.split(':', '.')
        val cs = p[0].toLong() * 360_000 + p[1].toLong() * 6000 + p[2].toLong() * 100 + p[3].toLong()
        val t = (cs + deltaMs / 10).coerceAtLeast(0)
        return "%d:%02d:%02d.%02d".format(t / 360_000, t / 6000 % 60, t / 100 % 60, t % 100)
    }

    /** The label for the delay: "+0.5 s", "-1.2 s" or "No delay". */
    fun label(ms: Long): String = if (ms == 0L) "No delay" else "%+.1f s".format(Locale.ROOT, ms / 1000.0)
}

/** The text a viewer can send when something would not play: what failed and what this box is, with nothing that identifies the stream's owner. */
object PlayerReport {
    /** A link with its query, credentials and long token-like path parts removed. */
    fun redact(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val scheme = noQuery.substringBefore("://", "")
        val rest = noQuery.substringAfter("://", noQuery)
        val host = rest.substringBefore('/').substringAfter('@')
        val path = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }
            .joinToString("/") { if (it.length > 24 && it.none { c -> c == ' ' || c == '.' }) "…" else it }
        return (if (scheme.isEmpty()) "" else "$scheme://") + host + if (path.isEmpty()) "" else "/$path"
    }

    fun build(lines: List<Pair<String, String>>): String = lines.filter { it.second.isNotBlank() }.joinToString("\n") { "${it.first}: ${it.second}" }
}
