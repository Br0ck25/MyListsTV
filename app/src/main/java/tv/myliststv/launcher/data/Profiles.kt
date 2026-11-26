package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Viewer profiles. Each has its own watch history; a kids profile hides adult titles. (No PIN lock by the owner's choice.) */
object Profiles {
    /** [avatar] is an id from ui/Avatars (empty: the profile shows its first letter). */
    data class P(val id: String, val name: String, val kids: Boolean, val avatar: String = "")

    private lateinit var sp: SharedPreferences
    /** The "who is watching" question is asked once per app start. */
    var askedThisRun = false

    fun init(c: Context) {
        sp = c.getSharedPreferences("profiles", Context.MODE_PRIVATE)
        if (!sp.contains("list")) write(listOf(P("p1", "Main", false)))
    }

    fun all(): List<P> = try {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> P(o.getString("id"), o.getString("name"), o.optBoolean("kids"), o.optString("avatar")) } }
    } catch (_: Exception) { listOf(P("p1", "Main", false)) }

    private fun write(l: List<P>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kids", it.kids).put("avatar", it.avatar)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    var activeId: String
        get() = if (::sp.isInitialized) sp.getString("active", "p1") ?: "p1" else "p1"
        set(v) = sp.edit().putString("active", v).apply()

    /** The storage key for something each profile keeps for itself (its account, addons, backup code). The first profile keeps the plain key. */
    fun key(base: String, id: String = activeId) = if (id == "p1") base else "${base}_$id"

    /** The profile whose addons [id] uses, or null. Only one level: a profile that itself shares cannot be shared from. */
    fun addonSource(id: String = activeId): String? =
        if (!::sp.isInitialized) null else sp.getString("share_$id", null)?.takeIf { s -> s != id && all().any { it.id == s } && sp.getString("share_$s", null) == null }

    fun shareAddons(id: String, with: String?) { sp.edit().apply { if (with == null) remove("share_$id") else putString("share_$id", with) }.apply() }

    /** Makes [id] the profile in use. Its history, Library, addons and account come with it, so Home reloads. */
    fun switchTo(c: Context, id: String) { activeId = id; WatchStore.init(c); AddonStore.needsRefresh = true }

    fun active(): P = all().firstOrNull { it.id == activeId } ?: all().first()

    fun rename(id: String, name: String) { val n = name.trim(); if (n.isNotEmpty()) write(all().map { if (it.id == id) it.copy(name = n) else it }) }

    /** Adds a profile and returns its id. */
    fun add(name: String, kids: Boolean, avatar: String = ""): String {
        val id = "p" + System.currentTimeMillis()
        write(all() + P(id, name.trim().ifEmpty { "Profile" }, kids, avatar)); return id
    }

    /** Changes a profile's name, kids setting and picture in one go (the edit screen). A blank name keeps the old one. */
    fun update(id: String, name: String, kids: Boolean, avatar: String) {
        write(all().map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, kids = kids, avatar = avatar) else it })
    }

    /** The last profile cannot be removed. */
    fun remove(id: String) {
        val l = all(); if (l.size <= 1) return
        write(l.filter { it.id != id }); forget(id); sp.edit().remove("share_$id").apply()
        if (activeId == id) activeId = all().first().id
    }

    /** Deletes what a removed profile kept: history, Library, watched ticks, alerts, addons, account and backup code. */
    private fun forget(id: String) {
        val app = tv.myliststv.launcher.App.instance
        listOf("watch_$id", "library_$id", "watched_$id", "alerts_$id").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
        AddonStore.forget(id); Prefs.forget(id)
    }

    /** Replaces the whole list (used when restoring a backup). */
    fun restore(l: List<P>, active: String) { if (l.isNotEmpty()) { write(l); activeId = active.takeIf { a -> l.any { it.id == a } } ?: l.first().id } }

    private val blockedGenres = setOf("horror", "thriller", "crime", "war")
    private val blockedRatings = setOf("R", "NC-17", "TV-MA", "18", "X")

    fun allows(m: Meta): Boolean = !active().kids || safeForKids(m)

    fun safeForKids(m: Meta) = m.genres.none { it.lowercase() in blockedGenres } && (m.certification?.uppercase() !in blockedRatings)
}
