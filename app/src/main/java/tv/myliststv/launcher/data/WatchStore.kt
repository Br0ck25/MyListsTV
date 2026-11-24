package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local watch progress behind Continue Watching. Small and capped so it never grows. */
/** Anything shorter than this is not a title (a stream addon's error clip is 10 to 30 seconds): it is never kept as progress and never counts as watched. */
const val MIN_REAL_MS = 120_000L

object WatchStore {
    private lateinit var sp: SharedPreferences
    private const val MAX = 40

    data class Entry(
        val metaId: String,
        val type: String,
        val name: String,
        val poster: String?,
        val background: String?,
        val videoId: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
        val positionMs: Long,
        val durationMs: Long,
        val updated: Long,
    ) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    /** Each profile has its own history. Call again after switching profile. */
    fun init(c: Context) { sp = c.getSharedPreferences(if (Profiles.activeId == "p1") "watch" else "watch_${Profiles.activeId}", Context.MODE_PRIVATE) }

    fun all(): List<Entry> {
        val raw = sp.getString("items", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::fromJson) }.sortedByDescending { it.updated }
        } catch (_: Exception) { emptyList() }
    }

    /** Entries worth showing: started, not finished. */
    /** Entries worth showing: started and not finished, or next-up episodes from the account (no length yet). */
    fun continueWatching(): List<Entry> = all().filter { it.durationMs == 0L || it.durationMs >= MIN_REAL_MS }.map(::tidy).filter { it.fraction in 0.02f..0.93f || it.durationMs == 0L }

    /** Under 2% in (the point where Resume starts to apply): nothing worth resuming, so it counts as next up. */
    private fun tidy(e: Entry) = if (e.durationMs > 1L && e.fraction < 0.02f) e.copy(positionMs = 0L, durationMs = 0L) else e

    fun forMeta(metaId: String): Entry? = all().firstOrNull { it.metaId == metaId }

    /**
     * The newest thing watched comes first. A title just opened (under 2% in) is the latest thing watched but has no progress worth
     * resuming, so it is kept as "next up" at the top; storing it as it is would hide it until it passed 2% and drop its old entry.
     */
    fun save(e: Entry) {
        if (e.durationMs in 2 until MIN_REAL_MS) return   // an error clip, not the title
        val entry = tidy(e)
        val list = all().filter { it.metaId != e.metaId }.toMutableList()
        list.add(0, entry)
        val a = JSONArray()
        list.take(MAX).forEach { a.put(toJson(it)) }
        sp.edit().putString("items", a.toString()).apply()
    }

    fun remove(metaId: String) {
        val a = JSONArray()
        all().filter { it.metaId != metaId }.forEach { a.put(toJson(it)) }
        sp.edit().putString("items", a.toString()).apply()
    }

    private fun toJson(e: Entry) = JSONObject().apply {
        put("metaId", e.metaId); put("type", e.type); put("name", e.name)
        put("poster", e.poster ?: ""); put("background", e.background ?: "")
        put("videoId", e.videoId); put("season", e.season); put("episode", e.episode)
        put("episodeTitle", e.episodeTitle ?: ""); put("pos", e.positionMs); put("dur", e.durationMs); put("updated", e.updated)
    }

    private fun fromJson(o: JSONObject) = Entry(
        metaId = o.optString("metaId"), type = o.optString("type"), name = o.optString("name"),
        poster = o.optString("poster").ifEmpty { null }, background = o.optString("background").ifEmpty { null },
        videoId = o.optString("videoId"), season = o.optInt("season", -1), episode = o.optInt("episode", -1),
        episodeTitle = o.optString("episodeTitle").ifEmpty { null },
        positionMs = o.optLong("pos"), durationMs = o.optLong("dur"), updated = o.optLong("updated"),
    )
}
