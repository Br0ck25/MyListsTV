package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences

data class Channel(val name: String, val logo: String?, val group: String, val url: String)

/** M3U playlist parsing. Pure, so tests can check it. Ignores everything except the name, logo, group and stream address. */
object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String, max: Int = 6000): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""; var logo: String? = null; var group = "Channels"
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                logo = attrs["tvg-logo"]?.ifEmpty { null }
                group = attrs["group-title"]?.ifEmpty { null } ?: "Channels"
                name = line.substringAfterLast(',').trim().ifEmpty { attrs["tvg-name"] ?: "Channel" }
            } else if (line.isNotEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                out += Channel(name.ifEmpty { "Channel ${out.size + 1}" }, logo, group, line)
                name = ""; logo = null
                if (out.size >= max) break
            }
        }
        return out
    }
}

/** Saved IPTV playlist links. */
object LiveStore {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("live", Context.MODE_PRIVATE) }
    fun playlists(): List<String> = (sp.getString("list", "") ?: "").split('\n').filter { it.isNotBlank() }
    fun add(url: String) { if (url !in playlists()) sp.edit().putString("list", (playlists() + url).joinToString("\n")).apply() }
    fun remove(url: String) { sp.edit().putString("list", (playlists() - url).joinToString("\n")).apply() }

    /** Loads every playlist (cached first) and returns the channels once, grouped. [cb] runs on the main thread. */
    fun load(cb: (List<Channel>) -> Unit) {
        val lists = playlists()
        if (lists.isEmpty()) { cb(emptyList()); return }
        val parts = arrayOfNulls<List<Channel>>(lists.size)
        var pending = lists.size
        lists.forEachIndexed { i, u ->
            var done = false
            Http.get(u, 6L * 3600_000) { body, _, _ ->
                if (done) return@get; done = true
                parts[i] = body?.let { M3u.parse(it) } ?: emptyList()
                if (--pending == 0) cb(parts.filterNotNull().flatten())
            }
        }
    }
}

/** One place that decides what a pasted link is: a Stremio addon or an IPTV playlist. */
object Links {
    fun isAddon(link: String) = link.trim().let { it.startsWith("stremio://") || it.substringBefore('?').endsWith("manifest.json") }

    fun add(link: String, reply: (String) -> Unit) {
        val t = link.trim()
        if (isAddon(t)) {
            val url = AddonStore.normalize(t) ?: return reply("That does not look like an addon link.")
            AddonStore.probe(url) { m, err -> if (m == null) reply(err ?: "Could not install it.") else { AddonStore.add(url); reply("Installed ${m.name}") } }
        } else if (t.startsWith("http://") || t.startsWith("https://")) {
            LiveStore.add(t); reply("Playlist added. Open the Live tab.")
        } else reply("That does not look like an addon or playlist link.")
    }
}
