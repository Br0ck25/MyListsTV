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

    /** [cb] may run twice: once from the cache, once with fresh data. */
    fun load(addon: Addon, cat: CatalogDef, cb: (List<Meta>?, Boolean) -> Unit) {
        val url = Urls.catalog(addon.baseUrl, cat.type, cat.id)
        Http.get(url, CATALOG_TTL) { body, fromCache, _ ->
            cb(body?.let { try { Json.parseMetas(it) } catch (_: Exception) { null } }, fromCache)
        }
    }

    /** Searches every addon that supports it. Answers (cached ones can arrive twice) are merged and de-duplicated, delivered once after a short wait. */
    fun search(query: String, cb: (List<Meta>) -> Unit) {
        val all = ArrayList<Meta>()
        for (a in enabled()) for (c in a.manifest.catalogs) if (c.supportsSearch && (c.type == "movie" || c.type == "series")) {
            Http.get(Urls.catalog(a.baseUrl, c.type, c.id, search = query), 10 * 60_000) { body, _, _ ->
                if (body != null) try { synchronized(all) { all += Json.parseMetas(body) } } catch (_: Exception) {}
            }
        }
        Http.background {
            Thread.sleep(2200)
            val seen = HashSet<String>()
            val merged = synchronized(all) { all.filter { seen.add(it.type + ":" + it.id) } }
            Http.post { cb(merged) }
        }
    }

    fun meta(type: String, id: String, fallback: Meta?, cb: (Meta?) -> Unit) {
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
            else if (!delivered) { delivered = true; cb(fallback) }
        }
    }

    /** Detail data wins, but anything missing there (a poster, say) is kept from the catalog item. */
    fun mergeMeta(detail: Meta, base: Meta?): Meta =
        if (base == null) detail else detail.copy(
            poster = detail.poster ?: base.poster,
            background = detail.background ?: base.background,
            description = detail.description ?: base.description,
            year = detail.year ?: base.year,
            rating = detail.rating ?: base.rating,
            genres = detail.genres.ifEmpty { base.genres },
        )
}
