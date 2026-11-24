package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import tv.myliststv.launcher.App

/**
 * New-episode alerts for series in the Library, shown in the bell. There is no background service: the launcher looks when it
 * opens (at most every 6 hours), asks the catalog addon for each series' episodes and compares the newest aired one with the
 * last one it saw. The first look only notes where each show is, so adding a show never fires a pile of alerts.
 */
object Alerts {
    data class Item(val id: String, val metaId: String, val type: String, val title: String, val text: String)

    private fun sp() = App.instance.getSharedPreferences("alerts_${Profiles.activeId}", android.content.Context.MODE_PRIVATE)

    fun all(): List<Item> = try {
        val a = JSONArray(sp().getString("items", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Item(it.getString("id"), it.getString("metaId"), it.getString("type"), it.getString("title"), it.getString("text")) }
    } catch (_: Exception) { emptyList() }

    private fun write(l: List<Item>) {
        val a = JSONArray(); l.take(50).forEach { a.put(JSONObject().put("id", it.id).put("metaId", it.metaId).put("type", it.type).put("title", it.title).put("text", it.text)) }
        sp().edit().putString("items", a.toString()).apply()
    }

    private fun followed(): JSONObject = try { JSONObject(sp().getString("followed", "{}")) } catch (_: Exception) { JSONObject() }
    private fun muted(): Set<String> = sp().getStringSet("muted", emptySet()) ?: emptySet()

    /** Does this show raise alerts: one the viewer turned on, or one in the Library that they have not turned off. */
    fun isOn(metaId: String) = followed().has(metaId) || (Library.has(metaId) && metaId !in muted())

    /** Turns alerts for one show on or off (the bell button on its page). Turning on takes a first look at once, so only episodes after today's newest raise an alert. */
    fun setOn(meta: Meta, on: Boolean) {
        val f = followed(); val m = muted().toMutableSet()
        if (on) { f.put(meta.id, meta.name); m -= meta.id } else { f.remove(meta.id); m += meta.id }
        sp().edit().putString("followed", f.toString()).putStringSet("muted", m).apply()
        if (on) check(force = true) { }
    }

    /** The shows to look at: the Library's series minus the ones turned off, plus the ones turned on that are not in the Library. */
    fun watchList(): List<Meta> {
        val lib = Library.all().filter { it.type == "series" && it.id !in muted() }
        val f = followed()
        return (lib + f.keys().asSequence().filter { id -> lib.none { it.id == id } }.map { Meta(it, "series", f.optString(it)) }).take(30).toList()
    }

    fun remove(id: String) = write(all().filter { it.id != id })
    fun clear() = write(emptyList())

    /** The newest episode that has already aired, as season * 10000 + episode, or 0. */
    fun newestAired(videos: List<Video>, now: Long = System.currentTimeMillis()): Int =
        videos.filter { it.season > 0 && (it.airDate()?.time ?: Long.MAX_VALUE) <= now }.maxOfOrNull { it.season * 10000 + it.episode } ?: 0

    /** Compares one show with what was seen last; returns a new alert or null. [seen] is the stored value (0 = never looked). */
    fun compare(meta: Meta, seen: Int, newest: Int): Item? =
        if (seen == 0 || newest <= seen) null else Item("${meta.id}:$newest", meta.id, meta.type, meta.name, "S${newest / 10000} · E${newest % 10000} is out")

    /** Looks at the Library's series now and then. [done] runs on the main thread when an alert was added. */
    fun check(force: Boolean = false, done: () -> Unit) {
        val last = sp().getLong("checkedAt", 0L); val now = System.currentTimeMillis()
        if (!force && now - last < 6 * 3600_000L) return
        sp().edit().putLong("checkedAt", now).apply()
        watchList().forEach { show ->
            Catalogs.meta("series", show.id, show) { m ->
                val newest = newestAired(m?.videos ?: return@meta)
                val key = "seen_${show.id}"; val seen = sp().getInt(key, 0)
                if (newest > seen) sp().edit().putInt(key, newest).apply()
                compare(show, seen, newest)?.let { a -> if (all().none { it.id == a.id }) { write(listOf(a) + all()); done() } }
            }
        }
    }
}
