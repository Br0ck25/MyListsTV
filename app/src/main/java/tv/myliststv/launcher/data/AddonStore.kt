package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The user's installed addons, kept as a list of manifest URLs. Manifests themselves live in the HTTP cache. */
object AddonStore {
    /** The guest default: the public My Lists catalog (trending, popular, box office, search). */
    const val DEFAULT_URL = "https://mylistsaddon.com/nuvio-default/manifest.json"

    data class Saved(val url: String, val enabled: Boolean)

    private lateinit var sp: SharedPreferences
    @Volatile var loaded: List<Addon> = emptyList()
        private set
    /** Set when addons changed behind the home screen's back (a restored backup), so it reloads when it comes back. */
    @Volatile var needsRefresh = false

    fun init(c: Context) {
        sp = c.getSharedPreferences("addons", Context.MODE_PRIVATE)
        if (!sp.contains("list")) write(listOf(Saved(DEFAULT_URL, true))) else if (Secure.needsWrap(sp.getString("list", null))) write(saved())    // wraps an older plain list
    }

    fun saved(): List<Saved> {
        // If a wrapped list cannot be opened (its key is gone), fall back to the default rather than show nothing.
        val raw = sp.getString("list", null)?.let { Secure.unwrap(it) } ?: return listOf(Saved(DEFAULT_URL, true))
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Saved(it.optString("url"), it.optBoolean("on", true)) } }
                .filter { it.url.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    private fun write(list: List<Saved>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("url", it.url).put("on", it.enabled)) }
        sp.edit().putString("list", Secure.wrap(a.toString())).apply()
    }

    /** Accepts stremio:// links and bare folders. Returns null if it cannot be an addon URL. */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("stremio://")) s = "https://" + s.removePrefix("stremio://")
        if (!s.startsWith("http://") && !s.startsWith("https://")) return null
        if (!s.endsWith("/manifest.json")) s = s.trimEnd('/') + "/manifest.json"
        return s
    }

    fun restore(list: List<Saved>) { if (list.isNotEmpty()) write(list) }

    /** Back to just the default My Lists addon. */
    fun reset() { write(listOf(Saved(DEFAULT_URL, true))) }
    fun add(url: String) { val l = saved(); if (l.none { it.url == url }) write(l + Saved(url, true)) }
    fun remove(url: String) { write(saved().filter { it.url != url }) }
    fun setEnabled(url: String, on: Boolean) { write(saved().map { if (it.url == url) it.copy(enabled = on) else it }) }
    fun move(url: String, delta: Int) {
        val l = saved().toMutableList(); val i = l.indexOfFirst { it.url == url }
        val j = i + delta
        if (i < 0 || j < 0 || j >= l.size) return
        val x = l.removeAt(i); l.add(j, x); write(l)
    }

    /** Fetches every saved manifest (from cache first), then calls [cb] once on the main thread with those that loaded. */
    fun load(cb: (List<Addon>) -> Unit) {
        val list = saved()
        if (list.isEmpty()) { loaded = emptyList(); cb(emptyList()); return }
        val results = arrayOfNulls<Addon>(list.size)
        var pending = list.size
        list.forEachIndexed { idx, s ->
            var done = false
            Http.get(s.url, 6L * 3600_000) { body, _, _ ->
                if (done) return@get
                done = true
                if (body != null) {
                    try { results[idx] = Addon(s.url, Json.parseManifest(body), s.enabled) } catch (_: Exception) {}
                }
                if (--pending == 0) {
                    loaded = results.filterNotNull()
                    cb(loaded)
                }
            }
        }
    }

    /** Fetches a manifest to check it before installing. */
    fun probe(url: String, cb: (Manifest?, String?) -> Unit) {
        Http.background {
            try {
                val m = Json.parseManifest(Http.fetchBlocking(url, 8000, 10000))
                if (m.resources.isEmpty() && m.catalogs.isEmpty()) Http.post { cb(null, "This is not an addon manifest.") }
                else Http.post { cb(m, null) }
            } catch (e: Exception) {
                Http.post { cb(null, "Could not load it: ${e.message ?: "unknown error"}") }
            }
        }
    }
}
