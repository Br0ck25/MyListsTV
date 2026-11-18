package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Viewer profiles. Each has its own watch history; a kids profile hides adult titles. (No PIN lock by the owner's choice.) */
object Profiles {
    data class P(val id: String, val name: String, val kids: Boolean)

    private lateinit var sp: SharedPreferences
    /** The "who is watching" question is asked once per app start. */
    var askedThisRun = false

    fun init(c: Context) {
        sp = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)
        if (!sp.contains("list")) write(listOf(P("p1", "Main", false)))
    }

    fun all(): List<P> = try {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> P(o.getString("id"), o.getString("name"), o.optBoolean("kids")) } }
    } catch (_: Exception) { listOf(P("p1", "Main", false)) }

    private fun write(l: List<P>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    var activeId: String
        get() = if (::sp.isInitialized) sp.getString("active", "p1") ?: "p1" else "p1"
        set(v) = sp.edit().putString("active", v).apply()

    /** The storage key for something each profile keeps for itself (its account, addons, playlists, backup code). The first profile keeps the plain key. */
    fun key(base: String, id: String = activeId) = if (id == "p1") base else "${base}_$id"

    /** Makes [id] the profile in use. Its history, Library, addons, playlists and account come with it, so Home reloads. */
    fun switchTo(c: Context, id: String) { activeId = id; WatchStore.init(c); AddonStore.needsRefresh = true }

    fun active(): P = all().firstOrNull { it.id == activeId } ?: all().first()

    fun add(name: String, kids: Boolean) { write(all() + P("p" + System.currentTimeMillis(), name.trim().ifEmpty { "Profile" }, kids)) }

    /** The last profile cannot be removed. */
    fun remove(id: String) {
        val l = all(); if (l.size <= 1) return
        write(l.filter { it.id != id }); forget(id)
        if (activeId == id) activeId = all().first().id
    }

    /** Deletes what a removed profile kept: history, Library, watched ticks, alerts, addons, playlists, account and backup code. */
    private fun forget(id: String) {
        val app = tv.myliststv.launcher.App.instance
        listOf("watch_$id", "library_$id", "watched_$id", "alerts_$id").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
        AddonStore.forget(id); LiveStore.forget(id); Prefs.forget(id)
    }

    /** Replaces the whole list (used when restoring a backup). */
    fun restore(l: List<P>, active: String) { if (l.isNotEmpty()) { write(l); activeId = active.takeIf { a -> l.any { it.id == a } } ?: l.first().id } }

    private val blockedGenres = setOf("horror", "thriller", "crime", "war")
    private val blockedRatings = setOf("R", "NC-17", "TV-MA", "18", "X")

    fun allows(m: Meta): Boolean = !active().kids || safeForKids(m)

    fun safeForKids(m: Meta) = m.genres.none { it.lowercase() in blockedGenres } && (m.certification?.uppercase() !in blockedRatings)
}
