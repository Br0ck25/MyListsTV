package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import tv.myliststv.launcher.App

/**
 * What the My Lists account has already watched, read once in a while from /api/nuvio-tv/watched so titles and episodes finished on
 * the website or another TV carry their check mark here too. Kept on the box per profile; nothing is sent. Signed out, it is empty.
 * Shape of the answer: { movies: ["tt1", ...], shows: [{ id: "tt2", complete: true, episodes: ["1:1", "1:2"] }] }.
 */
object AccountWatched {
    private const val ORIGIN = "https://mylistsaddon.com"
    private class Show(val complete: Boolean, val episodes: Set<String>)
    private var cachedFor = ""
    private var movies: Set<String> = emptySet()
    private var shows: Map<String, Show> = emptyMap()

    private fun sp() = App.instance.getSharedPreferences("accwatched_${Profiles.activeId}", android.content.Context.MODE_PRIVATE)

    private fun load() {
        val key = Profiles.activeId
        if (cachedFor == key) return
        cachedFor = key
        parse(sp().getString("data", "") ?: "")
    }

    /** Reads an answer into memory. Anything unexpected leaves an empty record rather than failing. */
    internal fun parse(text: String): Boolean {
        movies = emptySet(); shows = emptyMap()
        if (text.isEmpty()) return false
        return try {
            val o = JSONObject(text)
            movies = o.optJSONArray("movies")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.startsWith("tt") }.toSet() } ?: emptySet()
            val m = HashMap<String, Show>()
            val a = o.optJSONArray("shows") ?: JSONArray()
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                val id = s.optString("id").takeIf { it.startsWith("tt") } ?: continue
                val eps = s.optJSONArray("episodes")?.let { e -> (0 until e.length()).map { e.optString(it) }.toSet() } ?: emptySet()
                m[id] = Show(s.optBoolean("complete"), eps)
            }
            shows = m; true
        } catch (_: Exception) { movies = emptySet(); shows = emptyMap(); false }
    }

    fun movieDone(id: String): Boolean { load(); return id in movies }
    fun showDone(id: String): Boolean { load(); return shows[id]?.complete == true }

    /** [videoId] is "tt123:1:2". */
    fun episodeDone(metaId: String, videoId: String): Boolean {
        load()
        val s = shows[metaId] ?: return false
        if (s.complete) return true
        val p = videoId.split(':')
        return p.size >= 3 && "${p[1]}:${p[2]}" in s.episodes
    }

    /** After a title is marked by hand, the box's copy of the account's list follows at once (the account is told too), so the check mark does not wait for the next fetch. */
    fun mark(metaId: String, movie: Boolean, watched: Boolean) {
        load()
        if (movie) movies = if (watched) movies + metaId else movies - metaId
        else shows = shows.toMutableMap().also { if (watched) it[metaId] = Show(true, it[metaId]?.episodes ?: emptySet()) else it.remove(metaId) }
        val o = JSONObject().put("ok", true).put("movies", JSONArray(movies.toList()))
        val a = JSONArray(); shows.forEach { (id, s) -> a.put(JSONObject().put("id", id).put("complete", s.complete).put("episodes", JSONArray(s.episodes.toList()))) }
        sp().edit().putString("data", o.put("shows", a).toString()).apply()
    }

    /** Fetches the account's list. [changed] runs on the main thread only when it differs from what the box already had. */
    fun pull(changed: () -> Unit) {
        val link = Prefs.accountLink
        if (link.isEmpty()) return
        Http.background {
            try {
                val (code, text) = Http.send("POST", "$ORIGIN/api/nuvio-tv/watched", JSONObject().put("targetId", Pairing.targetOf(link)).put("action", "get").toString())
                if (code !in 200..299 || !JSONObject(text).optBoolean("ok", true)) return@background
                val before = sp().getString("data", "")
                if (text == before) return@background
                sp().edit().putString("data", text).apply()
                cachedFor = ""
                Http.post(changed)
            } catch (_: Exception) {}
        }
    }
}
