package tv.myliststv.launcher.data

import org.json.JSONObject
import java.util.Locale

data class Sub(val url: String, val lang: String)

/** Subtitles from subtitle addons (the Stremio "subtitles" resource). */
object Subs {
    fun parse(text: String): List<Sub> {
        val a = JSONObject(text).optJSONArray("subtitles") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .filter { it.optString("url").startsWith("http") }.map { Sub(it.optString("url"), it.optString("lang")) }
    }

    /** Keeps subtitles in the wanted language ("en" matches "en" and "eng"), at most [max]. */
    fun pick(all: List<Sub>, lang: String, max: Int = 3): List<Sub> {
        if (lang == "off" || lang.isEmpty()) return emptyList()
        val iso3 = try { Locale(lang).isO3Language } catch (_: Exception) { lang }
        return all.filter { val l = it.lang.lowercase(); l == lang || l == iso3 || l.startsWith("$lang-") }.take(max)
    }

    fun providers(type: String, videoId: String) = AddonStore.loaded.filter { a ->
        a.enabled && a.manifest.resources["subtitles"]?.let { r ->
            (r.types.isEmpty() || type in r.types) && (r.idPrefixes.isEmpty() || r.idPrefixes.any { videoId.startsWith(it) })
        } == true
    }

    /** Asks every subtitle addon (3 s each, in parallel). [cb] runs once on the main thread. */
    fun fetch(type: String, videoId: String, lang: String, cb: (List<Sub>) -> Unit) {
        val addons = if (type == "movie" || type == "series") providers(type, videoId) else emptyList()
        if (addons.isEmpty() || lang == "off") { cb(emptyList()); return }
        val found = ArrayList<Sub>(); var pending = addons.size
        for (a in addons) Http.background {
            val got = try { parse(Http.fetchBlocking(Urls.subtitles(a.baseUrl, type, videoId), 3000, 3000)) } catch (_: Exception) { emptyList() }
            val done = synchronized(found) { found += got; --pending == 0 }
            if (done) { val picked = pick(found, lang); Http.post { cb(picked) } }
        }
    }
}
