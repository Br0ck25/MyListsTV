package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import tv.myliststv.launcher.App

/** Which episodes of each show this profile has finished (90% or more, or marked by hand). One small record per profile, kept on the box. */
object Watched {
    private fun sp() = App.instance.getSharedPreferences("watched_${Profiles.activeId}", android.content.Context.MODE_PRIVATE)

    fun of(metaId: String): Set<String> = try {
        val a = JSONArray(sp().getString(metaId, "[]")); (0 until a.length()).map { a.getString(it) }.toSet()
    } catch (_: Exception) { emptySet() }

    fun has(metaId: String, videoId: String) = videoId in of(metaId) || AccountWatched.episodeDone(metaId, videoId)


    fun titleDone(m: Meta) = if (m.type == "movie") of(m.id).isNotEmpty() || AccountWatched.movieDone(m.id) else "*" in of(m.id) || AccountWatched.showDone(m.id)

    /** A whole title marked by hand: a movie is its own "episode", a show gets the flag "*" (its episodes keep their own check marks). */
    fun setTitle(m: Meta, watched: Boolean) { set(m.id, if (m.type == "movie") m.id else "*", watched); AccountWatched.mark(m.id, m.type == "movie", watched) }

    fun set(metaId: String, videoId: String, watched: Boolean) {
        // An episode (tt:S:E) also updates the box's copy of the account's list, or a check the account gave could never be taken off.
        if (videoId != "*" && videoId.count { it == ':' } >= 2) AccountWatched.markEpisode(metaId, videoId, watched)
        val now = of(metaId).let { if (watched) it + videoId else it - videoId }
        sp().edit().apply { if (now.isEmpty()) remove(metaId) else putString(metaId, JSONArray(now.toList()).toString()) }.apply()
    }
}
