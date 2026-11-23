package tv.myliststv.launcher.data

import org.json.JSONObject

/**
 * Tells My Lists where this box stopped, for a signed-in account only: part way through goes to /progress (the website's
 * Continue Watching), 90% or more goes to /history (watched). Nothing is sent when signed out. Fire and forget on a background
 * thread; a failure is ignored, the box keeps its own history either way.
 */
object Progress {
    private const val ORIGIN = "https://mylistsaddon.com"
    private var lastSent = 0L

    /** The request for this play, or null when it should not be sent (no account, no IMDb id, a start, or live TV). */
    fun request(link: String, e: WatchStore.Entry): Pair<String, String>? {
        if (link.isEmpty() || !Regex("tt\\d+").matches(e.metaId) || e.durationMs <= 0) return null
        val series = e.type == "series"
        val o = JSONObject().put("targetId", Pairing.targetOf(link)).put("type", if (series) "series" else "movie").put("imdbId", e.metaId)
        if (series) o.put("season", e.season).put("episode", e.episode)
        if (e.fraction >= 0.9f) return "$ORIGIN/api/nuvio-tv/history" to o.put("completed", true).put("watchedAt", e.updated).toString()
        if (e.fraction < 0.02f) return null
        o.put("name", if (series) e.episodeTitle ?: e.name else e.name).put("showTitle", e.name).put("poster", e.poster ?: "")
            .put("positionMs", e.positionMs).put("durationMs", e.durationMs)
        return "$ORIGIN/api/nuvio-tv/progress" to o.toString()
    }

    /** The account's Continue Watching as a launcher entry. A next-up episode has no length (durationMs 0). */
    fun entryFrom(o: JSONObject): WatchStore.Entry? {
        val id = o.optString("id").takeIf { it.startsWith("tt") } ?: return null
        val series = o.optString("type") == "series"; val s = o.optInt("season"); val e = o.optInt("episode")
        val poster = o.optString("poster").ifEmpty { null }
        return WatchStore.Entry(id, if (series) "series" else "movie", o.optString("name").ifEmpty { id }, poster, o.optString("thumbnail").ifEmpty { poster },
            if (series && e > 0) "$id:$s:$e" else id, if (series && e > 0) s else -1, if (series && e > 0) e else -1, o.optString("episodeName").ifEmpty { null },
            o.optLong("positionMs"), o.optLong("durationMs"), o.optLong("watchedAt"))
    }

    /** Brings the account's Continue Watching in (the website's and the other TVs'), keeping whichever entry for a title is newer. [changed] runs on the main thread. */
    fun pull(changed: () -> Unit) {
        if (Prefs.accountLink.isEmpty()) return
        Http.background {
            try {
                val (code, text) = Http.send("POST", "$ORIGIN/api/nuvio-tv/progress", JSONObject().put("targetId", Pairing.targetOf(Prefs.accountLink)).put("action", "get").toString())
                if (code !in 200..299) return@background
                val o = JSONObject(text); o.optString("username").takeIf { it.isNotEmpty() }?.let { Prefs.accountName = it }
                val a = o.optJSONArray("items") ?: return@background
                var any = false
                for (i in 0 until a.length()) {
                    val e = entryFrom(a.getJSONObject(i)) ?: continue
                    val mine = WatchStore.forMeta(e.metaId)
                    if (mine == null || e.updated > mine.updated) { WatchStore.save(e); any = true }
                }
                if (any) Http.post(changed)
            } catch (_: Exception) {}
        }
    }

    /** Sends at most once a minute unless [force] (leaving the player, or the end of the title). */
    fun report(e: WatchStore.Entry, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSent < 60_000) return
        val (url, body) = request(Prefs.accountLink, e) ?: return
        lastSent = now
        Http.background { try { Http.send("POST", url, body) } catch (_: Exception) {} }
    }
}
