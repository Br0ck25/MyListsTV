package tv.myliststv.launcher.data

import tv.myliststv.launcher.*

import tv.myliststv.launcher.App
import android.content.Context
import android.content.SharedPreferences

data class Channel(val name: String, val logo: String?, val group: String, val url: String, val tvgId: String? = null)

/** M3U playlist parsing. Pure, so tests can check it. Ignores everything except the name, logo, group and stream address. */
object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    /** Guide (XMLTV) links named in the playlist's first line, if any. */
    fun epgUrls(text: String): List<String> =
        Regex("""(?:url-tvg|x-tvg-url)="([^"]+)"""").find(text.lineSequence().firstOrNull { it.startsWith("#EXTM3U") } ?: "")?.groupValues?.get(1)
            ?.split(',')?.map { it.trim() }?.filter { it.startsWith("http") } ?: emptyList()

    fun parse(text: String, max: Int = 6000): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""; var logo: String? = null; var group = "Channels"; var tvgId: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                logo = attrs["tvg-logo"]?.ifEmpty { null }
                tvgId = attrs["tvg-id"]?.ifEmpty { null }
                group = attrs["group-title"]?.ifEmpty { null } ?: "Channels"
                name = line.substringAfterLast(',').trim().ifEmpty { attrs["tvg-name"] ?: "Channel" }
            } else if (line.isNotEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                out += Channel(name.ifEmpty { "Channel ${out.size + 1}" }, logo, group, line, tvgId)
                name = ""; logo = null; tvgId = null
                if (out.size >= max) break
            }
        }
        return out
    }
}

/** Saved IPTV playlist links. */
object LiveStore {
    private lateinit var sp: SharedPreferences
    // Playlist links can carry a login, so the list is kept wrapped by the Keystore (see Secure).
    fun init(c: Context) { sp = c.getSharedPreferences("live", Context.MODE_PRIVATE); if (Secure.needsWrap(sp.getString("list", null))) restore(playlists()) }
    /** Each profile has its own playlists. */
    fun playlists(): List<String> = Secure.unwrap(sp.getString(Profiles.key("list"), "") ?: "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun forget(id: String) { sp.edit().remove(Profiles.key("list", id)).apply() }
    fun add(url: String) { if (url !in playlists()) restore(playlists() + url) }
    fun remove(url: String) { restore(playlists() - url) }
    fun restore(list: List<String>) { sp.edit().putString(Profiles.key("list"), Secure.wrap(list.joinToString("\n"))).apply(); SetupSync.changed() }

    /** Loads every playlist (cached first) and returns the channels once, grouped. [cb] runs on the main thread. */
    fun load(cb: (List<Channel>, List<String>) -> Unit) {
        val lists = playlists()
        if (lists.isEmpty()) { cb(emptyList(), emptyList()); return }
        val parts = arrayOfNulls<List<Channel>>(lists.size)
        val guides = ArrayList<String>()
        var pending = lists.size
        lists.forEachIndexed { i, u ->
            var done = false
            Http.get(u, 6L * 3600_000) { body, _, _ ->
                if (done) return@get; done = true
                parts[i] = body?.let { M3u.parse(it) } ?: emptyList()
                body?.let { guides += M3u.epgUrls(it) }
                if (--pending == 0) cb(parts.filterNotNull().flatten(), guides.distinct())
            }
        }
    }
}

/** One place that decides what a pasted link is: a Stremio addon or an IPTV playlist. */
object Links {
    fun isAddon(link: String) = link.trim().let { it.startsWith("stremio://") || it.substringBefore('?').endsWith("manifest.json") }

    fun add(link: String, reply: (String) -> Unit) {
        val t = link.trim()
        if (Backup.isCode(t)) { Backup.restore(App.instance, t, reply); return }   // a recovery code pasted from the phone page
        if (isAddon(t)) {
            val url = AddonStore.normalize(t) ?: return reply("That does not look like an addon link.")
            AddonStore.probe(url) { m, err -> if (m == null) reply(err ?: "Could not install it.") else { AddonStore.add(url); Backup.auto(); reply("Installed ${m.name}") } }
        } else if (t.startsWith("http://") || t.startsWith("https://")) {
            LiveStore.add(t); Backup.auto(); reply("Playlist added. Open the Live tab.")
        } else reply("That does not look like an addon, playlist link or recovery code.")
    }
}

data class Prog(val start: Long, val stop: Long, val title: String)

/** Programme guide from XMLTV. Streams the file, keeps only the channels we have and the next 12 hours. */
object Epg {
    @Volatile private var guide: Map<String, List<Prog>> = emptyMap()

    fun nowNext(tvgId: String?, t: Long = System.currentTimeMillis()): Pair<Prog?, Prog?> {
        val l = guide[tvgId ?: return null to null] ?: return null to null
        val now = l.firstOrNull { it.start <= t && t < it.stop }
        return now to l.firstOrNull { it.start >= (now?.stop ?: t) }
    }

    internal fun setGuide(g: Map<String, List<Prog>>) { guide = g }

    fun schedule(tvgId: String?): List<Prog> = if (tvgId == null) emptyList() else guide[tvgId] ?: emptyList()

    private fun time(s: String): Long? {
        for (f in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) try {
            val df = java.text.SimpleDateFormat(f, java.util.Locale.US); if (f.length == 14) df.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return df.parse(s.trim())?.time
        } catch (_: Exception) {}
        return null
    }

    fun parse(input: java.io.InputStream, ids: Set<String>, from: Long, until: Long): Map<String, List<Prog>> {
        val p = android.util.Xml.newPullParser(); p.setInput(input, null)
        val out = HashMap<String, MutableList<Prog>>()
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "programme") {
                val ch = p.getAttributeValue(null, "channel"); val st = time(p.getAttributeValue(null, "start") ?: ""); val sp = time(p.getAttributeValue(null, "stop") ?: "")
                var title = ""
                var depth = 1
                while (depth > 0) {
                    ev = p.next()
                    if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) { depth++; if (p.name == "title" && title.isEmpty()) title = p.nextText().also { depth-- } }
                    else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG) depth--
                    else if (ev == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break
                }
                if (ch != null && ch in ids && st != null && sp != null && sp > from && st < until && title.isNotEmpty()) out.getOrPut(ch) { ArrayList() } += Prog(st, sp, title)
            }
            ev = p.next()
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start }.take(6) }
    }

    /** Downloads each guide (gzip is detected), parses it off the main thread, then calls [cb] on the main thread. */
    fun load(urls: List<String>, ids: Set<String>, cb: () -> Unit) {
        if (urls.isEmpty() || ids.isEmpty()) return
        Http.background {
            val merged = HashMap<String, List<Prog>>(); val now = System.currentTimeMillis()
            for (u in urls.take(2)) try {
                val f = Http.file("$u#epg") ?: continue
                if (!f.exists() || now - f.lastModified() > 6L * 3600_000) Http.download(u, f)
                val raw = java.io.BufferedInputStream(f.inputStream())
                raw.mark(2); val gz = raw.read() == 0x1f && raw.read() == 0x8b; raw.reset()
                (if (gz) java.util.zip.GZIPInputStream(raw) else raw).use { merged += parse(it, ids, now, now + 12 * 3600_000L) }
            } catch (_: Throwable) {}
            guide = merged
            Http.post { cb() }
        }
    }
}
