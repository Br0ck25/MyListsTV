package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** Parsing for Stremio addon protocol responses. Pure functions so they can be unit tested. */
object Json {

    fun parseManifest(text: String): Manifest {
        val o = JSONObject(text)
        val resources = LinkedHashMap<String, ResourceDef>()
        val ra = o.optJSONArray("resources") ?: JSONArray()
        val topTypes = strings(o.optJSONArray("types"))
        val topPrefixes = strings(o.optJSONArray("idPrefixes"))
        for (i in 0 until ra.length()) {
            val r = ra.get(i)
            if (r is String) resources[r] = ResourceDef(topTypes, topPrefixes)
            else if (r is JSONObject) {
                val name = r.optString("name")
                if (name.isNotEmpty()) resources[name] = ResourceDef(
                    r.optJSONArray("types")?.let { strings(it) } ?: topTypes,
                    r.optJSONArray("idPrefixes")?.let { strings(it) } ?: topPrefixes,
                )
            }
        }
        val catalogs = ArrayList<CatalogDef>()
        val ca = o.optJSONArray("catalogs") ?: JSONArray()
        for (i in 0 until ca.length()) {
            val c = ca.optJSONObject(i) ?: continue
            var searchRequired = false
            var supportsSearch = false
            val extra = c.optJSONArray("extra")
            if (extra != null) for (j in 0 until extra.length()) {
                val e = extra.optJSONObject(j) ?: continue
                if (e.optString("name") == "search") {
                    supportsSearch = true
                    if (e.optBoolean("isRequired")) searchRequired = true
                }
            }
            val req = c.optJSONArray("extraRequired")
            if (req != null) for (j in 0 until req.length()) if (req.optString(j) == "search") searchRequired = true
            catalogs += CatalogDef(
                type = c.optString("type"),
                id = c.optString("id"),
                name = c.optString("name").ifEmpty { c.optString("id") },
                searchRequired = searchRequired,
                supportsSearch = supportsSearch,
            )
        }
        return Manifest(
            id = o.optString("id"),
            name = o.optString("name").ifEmpty { "Addon" },
            version = o.optString("version"),
            logo = o.optString("logo").ifEmpty { null },
            types = topTypes,
            idPrefixes = topPrefixes,
            resources = resources,
            catalogs = catalogs,
        )
    }

    fun parseMetas(text: String): List<Meta> {
        val a = JSONObject(text).optJSONArray("metas") ?: return emptyList()
        val out = ArrayList<Meta>(a.length())
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { metaFrom(it) }?.let { out += it }
        return out
    }

    fun parseMeta(text: String): Meta? = JSONObject(text).optJSONObject("meta")?.let { metaFrom(it) }

    /** Meta travels between screens as JSON in an intent extra. */
    fun metaToJson(m: Meta): String = JSONObject().apply {
        put("id", m.id); put("type", m.type); put("name", m.name)
        put("poster", m.poster ?: ""); put("background", m.background ?: ""); put("logo", m.logo ?: "")
        put("description", m.description ?: ""); put("releaseInfo", m.year ?: ""); put("imdbRating", m.rating ?: "")
        put("runtime", m.runtime ?: ""); put("genres", JSONArray(m.genres))
        put("videos", JSONArray().also { a -> m.videos.forEach { v ->
            a.put(JSONObject().put("id", v.id).put("title", v.title).put("season", v.season).put("episode", v.episode)
                .put("thumbnail", v.thumbnail ?: "").put("overview", v.overview ?: "")) } })
    }.toString()

    fun metaFromJson(text: String): Meta? = try { metaFrom(JSONObject(text)) } catch (_: Exception) { null }

    private fun metaFrom(o: JSONObject): Meta? {
        val id = o.optString("id")
        val name = o.optString("name")
        if (id.isEmpty() || name.isEmpty()) return null
        val videos = ArrayList<Video>()
        val va = o.optJSONArray("videos")
        if (va != null) for (i in 0 until va.length()) {
            val v = va.optJSONObject(i) ?: continue
            val vid = v.optString("id")
            val season = v.optInt("season", -1)
            val episode = if (v.has("episode")) v.optInt("episode", -1) else v.optInt("number", -1)
            if (vid.isEmpty() || season < 0 || episode < 0) continue
            videos += Video(
                id = vid,
                title = v.optString("title").ifEmpty { v.optString("name") }.ifEmpty { "Episode $episode" },
                season = season,
                episode = episode,
                thumbnail = v.optString("thumbnail").ifEmpty { null },
                overview = v.optString("overview").ifEmpty { v.optString("description") }.ifEmpty { null },
            )
        }
        val genres = strings(o.optJSONArray("genres")).ifEmpty { strings(o.optJSONArray("genre")) }
        val rating = o.optString("imdbRating").ifEmpty { o.opt("rating")?.toString().orEmpty() }
        return Meta(
            id = id,
            type = o.optString("type").ifEmpty { "movie" },
            name = name,
            poster = o.optString("poster").ifEmpty { null },
            background = o.optString("background").ifEmpty { null },
            logo = o.optString("logo").ifEmpty { null },
            description = o.optString("description").ifEmpty { null },
            year = o.optString("releaseInfo").ifEmpty { o.optString("year") }.ifEmpty { null },
            rating = rating.ifEmpty { null },
            runtime = o.optString("runtime").ifEmpty { null },
            genres = genres,
            videos = videos.sortedWith(compareBy({ it.season }, { it.episode })),
        )
    }

    /** Streams an addon returned for one title. Only direct URL streams are playable here. */
    fun parseStreams(text: String, addonName: String, addonUrl: String): List<StreamItem> {
        val a = JSONObject(text).optJSONArray("streams") ?: return emptyList()
        val out = ArrayList<StreamItem>()
        for (i in 0 until a.length()) {
            val s = a.optJSONObject(i) ?: continue
            val url = s.optString("url")
            // No on-device torrenting, no external links: only direct (usually debrid) URLs.
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) continue
            val name = s.optString("name")
            val title = s.optString("title").ifEmpty { s.optString("description") }
            val hints = s.optJSONObject("behaviorHints")
            val text = "$name\n$title\n${hints?.optString("filename").orEmpty()}"
            val headers = HashMap<String, String>()
            hints?.optJSONObject("proxyHeaders")?.optJSONObject("request")?.let { h ->
                for (k in h.keys()) headers[k] = h.optString(k)
            }
            out += StreamItem(
                addonName = addonName,
                addonUrl = addonUrl,
                url = url,
                name = name,
                title = title,
                filename = hints?.optString("filename")?.ifEmpty { null },
                infoHash = s.optString("infoHash").ifEmpty { null },
                resolution = StreamRank.resolution(text),
                sizeBytes = hints?.optLong("videoSize", 0L)?.takeIf { it > 0 } ?: StreamRank.sizeBytes(text),
                cached = StreamRank.looksCached(name, title),
                hdr = StreamRank.hasHdr(text),
                bingeGroup = hints?.optString("bingeGroup")?.ifEmpty { null },
                headers = headers,
            )
        }
        return out
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let { out += it }
        return out
    }
}
