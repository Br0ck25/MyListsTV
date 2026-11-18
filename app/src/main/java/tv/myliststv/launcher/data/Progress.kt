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
        val o = JSONObject().put("targetId", Pairing.userOf(link)).put("type", if (series) "series" else "movie").put("imdbId", e.metaId)
        if (series) o.put("season", e.season).put("episode", e.episode)
        if (e.fraction >= 0.9f) return "$ORIGIN/api/nuvio-tv/history" to o.put("completed", true).put("watchedAt", e.updated).toString()
        if (e.fraction < 0.02f) return null
        o.put("name", if (series) e.episodeTitle ?: e.name else e.name).put("showTitle", e.name).put("poster", e.poster ?: "")
            .put("positionMs", e.positionMs).put("durationMs", e.durationMs)
        return "$ORIGIN/api/nuvio-tv/progress" to o.toString()
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
