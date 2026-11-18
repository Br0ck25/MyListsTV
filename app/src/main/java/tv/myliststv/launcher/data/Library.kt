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

    fun all(): List<Meta> = read(Profiles.activeId)
    fun has(id: String) = all().any { it.id == id }

    /** Adds the title, or takes it out if it is already there. Returns true when it is in the list afterwards. */
    fun toggle(m: Meta): Boolean {
        val l = all()
        val inList = l.any { it.id == m.id }
        write(Profiles.activeId, if (inList) l.filter { it.id != m.id } else listOf(m) + l)
        return !inList
    }

    fun remove(id: String) = write(Profiles.activeId, all().filter { it.id != id })

    /** Every profile's list, for a backup. */
    fun export(): JSONObject = JSONObject().also { o -> Profiles.all().forEach { p -> o.put(p.id, JSONArray(sp(p.id).getString("items", "[]"))) } }

    fun import(o: JSONObject?) { o?.keys()?.forEach { id -> sp(id).edit().putString("items", o.getJSONArray(id).toString()).apply() } }
}
