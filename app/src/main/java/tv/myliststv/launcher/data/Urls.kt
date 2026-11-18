package tv.myliststv.launcher.data

import java.net.URLEncoder

/** Builds addon resource URLs. Kept pure so tests can check them. */
object Urls {
    private fun seg(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%3A", ":")

    fun catalog(base: String, type: String, id: String, skip: Int = 0, search: String? = null): String {
        val extra = ArrayList<String>()
        if (!search.isNullOrEmpty()) extra += "search=" + URLEncoder.encode(search, "UTF-8").replace("+", "%20")
        if (skip > 0) extra += "skip=$skip"
        val tail = if (extra.isEmpty()) "" else "/" + extra.joinToString("&")
        return "$base/catalog/${seg(type)}/${seg(id)}$tail.json"
    }

    fun meta(base: String, type: String, id: String) = "$base/meta/${seg(type)}/${seg(id)}.json"

    fun stream(base: String, type: String, id: String) = "$base/stream/${seg(type)}/${seg(id)}.json"

    fun subtitles(base: String, type: String, id: String) = "$base/subtitles/${seg(type)}/${seg(id)}.json"
}
