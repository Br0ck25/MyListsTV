package tv.myliststv.launcher.data

import org.json.JSONObject
import java.net.URLEncoder

/** Cast photos. Stremio metadata carries names only, so each photo is looked up by name through My Lists (which asks TMDB). */
object People {
    private const val SEARCH = "https://mylistsaddon.com/api/person-search?q="

    /** The profile photo of the first match, or null. */
    fun parsePhoto(text: String): String? = try {
        JSONObject(text).optJSONArray("results")?.optJSONObject(0)?.optString("poster")?.ifEmpty { null }
    } catch (_: Exception) { null }

    /** Cached for a month: a person's photo does not change. [cb] runs on the main thread, possibly twice (cache, then fresh). */
    fun photo(name: String, cb: (String?) -> Unit) =
        Http.get(SEARCH + URLEncoder.encode(name, "UTF-8"), 30L * 24 * 3600_000) { body, _, _ -> if (body != null) cb(parsePhoto(body)) }
}
