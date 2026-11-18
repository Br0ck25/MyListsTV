package tv.myliststv.launcher.data

import org.json.JSONObject

/** Intro, recap and credits times for a series episode, from IntroDB (introdb.app, free, no key, keyed by IMDb id). Series only: IntroDB has no movie data. */
object Segments {
    data class Seg(val kind: String, val startMs: Long, val endMs: Long)

    /** "tt0944947:1:2" gives show, season and episode; anything else (a movie, a channel) gives null. */
    fun parseVideoId(id: String): Triple<String, Int, Int>? {
        val p = id.split(':')
        if (p.size != 3 || !p[0].startsWith("tt")) return null
        return Triple(p[0], p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
    }

    fun url(id: String): String? = parseVideoId(id)?.let { (tt, s, e) -> "https://api.introdb.app/segments?imdb_id=$tt&season=$s&episode=$e" }

    fun parse(text: String): List<Seg> {
        val o = JSONObject(text)
        return listOf("intro" to "intro", "recap" to "recap", "outro" to "credits").mapNotNull { (key, kind) ->
            o.optJSONObject(key)?.let { s ->
                val start = s.optLong("start_ms", -1).coerceAtLeast(0)
                val end = s.optLong("end_ms", -1)
                if (end > start) Seg(kind, start, end) else null
            }
        }
    }

    /** The segment [posMs] is inside, if any. A segment is skippable from its start until a second before its end. */
    fun at(segs: List<Seg>, posMs: Long): Seg? = segs.firstOrNull { posMs >= it.startMs && posMs < it.endMs - 1000 }

    /** Cached for a week: these times almost never change. [cb] runs on the main thread, possibly twice (cache, then fresh). */
    fun load(videoId: String, cb: (List<Seg>) -> Unit) {
        val u = url(videoId) ?: return
        Http.get(u, 7L * 24 * 3600_000) { body, _, _ -> if (body != null) try { cb(parse(body)) } catch (_: Exception) {} }
    }
}
