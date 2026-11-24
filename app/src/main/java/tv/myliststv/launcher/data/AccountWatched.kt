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

    // Things taken off by hand. The account may not have applied the removal yet (or never does), and without this the check mark would come
    // straight back with the next fetch. A mark is kept for two days, and dropped as soon as the account no longer lists the thing.
    private const val KEEP_MS = 2 * 24 * 3600_000L
    private fun removals(): JSONObject = try { JSONObject(sp().getString("removed", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    private fun setRemoval(key: String, on: Boolean) {
        val r = removals(); if (on) r.put(key, System.currentTimeMillis()) else r.remove(key)
        sp().edit().putString("removed", r.toString()).apply()
    }

    private fun applyRemovals() {
        val r = removals(); val now = System.currentTimeMillis(); var changed = false
        r.keys().asSequence().toList().forEach { k ->
            val p = k.split('|')
            val listed = when (p[0]) { "m" -> p[1] in movies; "s" -> shows.containsKey(p[1]); else -> shows[p[1]]?.episodes?.contains(p[2]) == true }
            if (!listed || now - r.optLong(k) > KEEP_MS) { r.remove(k); changed = true; return@forEach }
            when (p[0]) {
                "m" -> movies = movies - p[1]
                "s" -> shows = shows - p[1]
                else -> shows[p[1]]?.let { s -> shows = shows + (p[1] to Show(false, s.episodes - p[2])) }
            }
        }
        if (changed) sp().edit().putString("removed", r.toString()).apply()
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
            shows = m; applyRemovals(); true
        } catch (_: Exception) { movies = emptySet(); shows = emptyMap(); false }
    }

    fun movieDone(id: String): Boolean { load(); return id in movies }
    fun showDone(id: String): Boolean { load(); return shows[id]?.complete == true }

    /**
     * [videoId] is "tt123:1:2". Only an episode the account lists counts. A show the account calls complete does not tick every episode:
     * that would also tick the ones that have not aired yet.
     */
    fun episodeDone(metaId: String, videoId: String): Boolean {
        load()
        val s = shows[metaId] ?: return false
        val p = videoId.split(':')
        return p.size >= 3 && "${p[1]}:${p[2]}" in s.episodes
    }

    /** An episode marked by hand: the box's copy follows at once. Taking one off also means the show is no longer complete. */
    fun markEpisode(metaId: String, videoId: String, watched: Boolean) {
        val p = videoId.split(':'); if (p.size < 3) return
        load()
        val key = "${p[1]}:${p[2]}"
        setRemoval("e|$metaId|$key", !watched)
        val old = shows[metaId]
        val eps = (old?.episodes ?: emptySet()).let { if (watched) it + key else it - key }
        shows = shows.toMutableMap().also { if (eps.isEmpty() && old?.complete != true && !watched) it.remove(metaId) else it[metaId] = Show(if (watched) old?.complete == true else false, eps) }
        save()
    }

    private fun save() {
        val a = JSONArray(); shows.forEach { (id, s) -> a.put(JSONObject().put("id", id).put("complete", s.complete).put("episodes", JSONArray(s.episodes.toList()))) }
        sp().edit().putString("data", JSONObject().put("ok", true).put("movies", JSONArray(movies.toList())).put("shows", a).toString()).apply()
    }

    /** After a title is marked by hand, the box's copy of the account's list follows at once (the account is told too), so the check mark does not wait for the next fetch. */
    fun mark(metaId: String, movie: Boolean, watched: Boolean) {
        load()
        setRemoval((if (movie) "m|" else "s|") + metaId, !watched)
        if (movie) movies = if (watched) movies + metaId else movies - metaId
        else shows = shows.toMutableMap().also { if (watched) it[metaId] = Show(true, it[metaId]?.episodes ?: emptySet()) else it.remove(metaId) }
        save()
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
