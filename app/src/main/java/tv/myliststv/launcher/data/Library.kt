package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import tv.myliststv.launcher.App

/** "Library": titles the viewer saved with + on a details page. One list per profile, newest first, kept on the box and in backups. */
object Library {
    private fun sp(profile: String = Profiles.activeId) = App.instance.getSharedPreferences("library_$profile", android.content.Context.MODE_PRIVATE)

    private fun read(profile: String): List<Meta> = try {
        val a = JSONArray(sp(profile).getString("items", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Meta(it.getString("id"), it.getString("type"), it.getString("name"), it.optString("poster").ifEmpty { null }, it.optString("background").ifEmpty { null }) }
    } catch (_: Exception) { emptyList() }

    private fun write(profile: String, list: List<Meta>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("name", it.name).put("poster", it.poster ?: "").put("background", it.background ?: "")) }
        sp(profile).edit().putString("items", a.toString()).apply()
    }

    // ---- sync with the account's Watchlist: each profile that is signed in syncs its own list with its own account ----
    private const val ORIGIN = "https://mylistsaddon.com"
    private fun syncOn() = Prefs.accountLink.isNotEmpty()
    private fun post(o: JSONObject): Pair<Int, String> = Http.send("POST", "$ORIGIN/api/nuvio-tv/library", o.put("targetId", Pairing.targetOf(Prefs.accountLink)).toString())
    private fun items(l: List<Meta>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("name", it.name).put("poster", it.poster ?: "")) } }

    /** Sends the list to the account. Fire and forget; if it fails the list is marked unsent and goes with the next change or check. */
    fun push() {
        if (!syncOn()) return
        val list = all()
        sp().edit().putBoolean("dirty", true).apply()
        Http.background {
            try {
                val (code, body) = post(JSONObject().put("action", "set").put("items", items(list)))
                if (code in 200..299) sp().edit().putLong("syncedAt", JSONObject(body).optLong("updatedAt")).putBoolean("dirty", false).apply()
            } catch (_: Exception) {}
        }
    }

    /** Brings the account's Watchlist in. The first time, the two lists are joined; after that the newer side wins. [changed] runs on the main thread if the list changed. */
    fun pull(changed: () -> Unit) {
        if (!syncOn()) return
        if (sp().getBoolean("dirty", false)) { push(); return }
        Http.background {
            try {
                val (code, body) = post(JSONObject().put("action", "get"))
                if (code !in 200..299) return@background
                val o = JSONObject(body); o.optString("username").takeIf { it.isNotEmpty() }?.let { Prefs.accountName = it }; val at = o.optLong("updatedAt"); val remote = o.optJSONArray("items") ?: JSONArray()
                val theirs = (0 until remote.length()).mapNotNull { remote.optJSONObject(it) }.mapNotNull { r ->
                    r.optString("id").takeIf { it.startsWith("tt") }?.let { id -> Meta(id, if (r.optString("type") == "series") "series" else "movie", r.optString("name").ifEmpty { r.optString("title") }.ifEmpty { id }, r.optString("poster").ifEmpty { null }) }
                }
                val first = sp().getLong("syncedAt", 0L) == 0L
                if (first) { val mine = all(); write(Profiles.activeId, mine + theirs.filter { t -> mine.none { it.id == t.id } }); if (theirs.size != all().size || at == 0L) push() else sp().edit().putLong("syncedAt", at).apply() }
                else if (at > sp().getLong("syncedAt", 0L)) write(Profiles.activeId, theirs).also { sp().edit().putLong("syncedAt", at).apply() }
                else return@background
                Http.post(changed)
            } catch (_: Exception) {}
        }
    }

    fun all(): List<Meta> = read(Profiles.activeId)
    fun has(id: String) = all().any { it.id == id }

    /** Adds the title, or takes it out if it is already there. Returns true when it is in the list afterwards. */
    fun toggle(m: Meta): Boolean {
        val l = all()
        val inList = l.any { it.id == m.id }
        write(Profiles.activeId, if (inList) l.filter { it.id != m.id } else listOf(m) + l)
        push()
        return !inList
    }

    fun remove(id: String) { write(Profiles.activeId, all().filter { it.id != id }); push() }

    /** This profile's list, for a backup. */
    fun export(): JSONArray = JSONArray(sp().getString("items", "[]"))

    /** Restores this profile's list from a backup (an array; the first version of backups held an object with every profile's list). */
    fun import(o: Any?) {
        val a = when (o) { is JSONArray -> o; is JSONObject -> o.optJSONArray(Profiles.activeId); else -> null } ?: return
        sp().edit().putString("items", a.toString()).apply()
    }
}
