package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local watch progress behind Continue watching. Small and capped so it never grows. */
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
    fun continueWatching(): List<Entry> = all().filter { it.fraction in 0.02f..0.93f }

    fun forMeta(metaId: String): Entry? = all().firstOrNull { it.metaId == metaId }

    fun save(e: Entry) {
        val list = all().filter { it.metaId != e.metaId }.toMutableList()
        list.add(0, e)
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
