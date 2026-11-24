package tv.myliststv.launcher.data

/** Catalog, meta and search lookups across installed addons. */
object Catalogs {
    private const val CATALOG_TTL = 30L * 60_000
    private const val META_TTL = 24L * 3600_000

    fun enabled(): List<Addon> = AddonStore.loaded.filter { it.enabled }

    /** Browse rows for Home, Movies and Series: catalogs that do not need a search term. */
    fun browseRows(type: String?, limit: Int): List<Pair<Addon, CatalogDef>> {
        val out = ArrayList<Pair<Addon, CatalogDef>>()
        for (a in enabled()) for (c in a.manifest.catalogs) {
            if (c.searchRequired) continue
            if (type != null && c.type != type) continue
            if (c.type != "movie" && c.type != "series") continue
            out += a to c
            if (out.size >= limit) return out
        }
        return out
    }

    /** Title of a browse row; also the key the owner's order and hidden lists use. */
    fun rowTitle(type: String?, cat: CatalogDef): String {
        val label = if (cat.type == "movie") "Movies" else "Series"
        return if (type == null && !cat.name.contains(label, true)) "${cat.name} \u00b7 $label" else cat.name
    }

    /** [cb] may run twice: once from the cache, once with fresh data. */
    fun load(addon: Addon, cat: CatalogDef, cb: (List<Meta>?, Boolean) -> Unit) {
        val url = Urls.catalog(addon.baseUrl, cat.type, cat.id)
        Http.get(url, CATALOG_TTL) { body, fromCache, _ ->
            cb(body?.let { try { Json.parseMetas(it) } catch (_: Exception) { null } }, fromCache)
        }
    }

    /** Searches every addon that supports it. [cb] runs on the main thread each time an answer arrives, with everything found so far (de-duplicated), so a slow addon never hides the quick ones. */
    fun search(query: String, cb: (List<Meta>) -> Unit) {
        val all = ArrayList<Meta>()
        val targets = enabled().flatMap { a -> a.manifest.catalogs.filter { it.supportsSearch && (it.type == "movie" || it.type == "series") }.map { a to it } }
        if (targets.isEmpty()) { cb(emptyList()); return }
        for ((a, c) in targets) Http.get(Urls.catalog(a.baseUrl, c.type, c.id, search = query), 10 * 60_000) { body, _, _ ->
            if (body == null) return@get
            val found = try { Json.parseMetas(body) } catch (_: Exception) { return@get }
            val seen = HashSet<String>()
            val merged = synchronized(all) { all += found; all.filter { seen.add(it.type + ":" + it.id) } }
            cb(merged)
        }
    }

    /** The standard Stremio metadata addon. Used only to fill in what the catalog addon lacks (logo, country, awards, writers, episodes). */
    private const val CINEMETA = "https://v3-cinemeta.strem.io"

    /** [cb] may run more than once: first with what the catalog addon knows, then again with extra details merged in. */
    fun meta(type: String, id: String, fallback: Meta?, cb: (Meta?) -> Unit) {
        var best: Meta? = fallback
        primaryMeta(type, id, fallback) { m -> best = m; cb(m) }
        if (Prefs.extraDetails && id.startsWith("tt") && (type == "movie" || type == "series"))
            Http.get(Urls.meta(CINEMETA, type, id), META_TTL) { body, _, _ ->
                val extra = body?.let { try { Json.parseMeta(it) } catch (_: Exception) { null } } ?: return@get
                best = fillGaps(best ?: extra, extra); cb(best)
            }
    }

    private fun primaryMeta(type: String, id: String, fallback: Meta?, cb: (Meta?) -> Unit) {
        val providers = enabled().filter { a ->
            val r = a.manifest.resources["meta"] ?: return@filter false
            (r.types.isEmpty() || type in r.types) && (r.idPrefixes.isEmpty() || r.idPrefixes.any { id.startsWith(it) })
        }
        if (providers.isEmpty()) { cb(fallback); return }
        var delivered = false
        Http.get(Urls.meta(providers[0].baseUrl, type, id), META_TTL) { body, _, _ ->
            if (delivered && body == null) return@get
            val m = body?.let { try { Json.parseMeta(it) } catch (_: Exception) { null } }
            if (m != null) { delivered = true; cb(mergeMeta(m, fallback)) }
            else if (!delivered) { delivered = true; channelByName(providers[0].baseUrl, type, id, fallback, cb) }
        }
    }

    /**
     * A My Lists channel's tile in a catalog can carry a different id from the one the addon answers details for, so its details come back
     * empty. The account's sync list names the channel with the id the addon does answer to: look it up by name and ask again.
     */
    private fun channelByName(base: String, type: String, id: String, fallback: Meta?, cb: (Meta?) -> Unit) {
        val link = Prefs.accountLink
        if (!id.startsWith("channel_") || fallback == null || link.isEmpty()) { cb(fallback); return }
        Http.get(ChannelIds.syncUrl(link), 10 * 60_000L) { text, _, _ ->
            val alt = text?.let { ChannelIds.byName(it, fallback.name) }
            if (alt == null || alt == id) { cb(fallback); return@get }
            Http.get(Urls.meta(base, type, alt), META_TTL) { body, _, _ ->
                body?.let { try { Json.parseMeta(it) } catch (_: Exception) { null } }?.let { cb(mergeMeta(it, fallback)) }
            }
        }
    }

    /** Keeps everything [base] has and takes from [extra] only what [base] lacks. */
    fun fillGaps(base: Meta, extra: Meta): Meta = base.copy(
        poster = base.poster ?: extra.poster, background = base.background ?: extra.background, logo = base.logo ?: extra.logo,
        description = base.description ?: extra.description, year = base.year ?: extra.year, rating = base.rating ?: extra.rating,
        runtime = base.runtime ?: extra.runtime, certification = base.certification ?: extra.certification,
        genres = base.genres.ifEmpty { extra.genres }, cast = base.cast.ifEmpty { extra.cast }, directors = base.directors.ifEmpty { extra.directors },
        writers = base.writers.ifEmpty { extra.writers }, trailers = base.trailers.ifEmpty { extra.trailers },
        country = base.country ?: extra.country, awards = base.awards ?: extra.awards, videos = base.videos.ifEmpty { extra.videos }, status = base.status ?: extra.status,
    )

    /** Detail data wins, but anything missing there (a poster, say) is kept from the catalog item. */
    fun mergeMeta(detail: Meta, base: Meta?): Meta = if (base == null) detail else fillGaps(detail, base)
}
