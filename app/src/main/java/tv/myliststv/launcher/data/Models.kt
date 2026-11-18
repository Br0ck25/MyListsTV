package tv.myliststv.launcher.data

/** A catalog or search item, also the detail record (meta). Fields mirror the Stremio addon protocol. */
data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val certification: String? = null,
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    /** YouTube video ids. */
    val trailers: List<String> = emptyList(),
    val country: String? = null,
    val awards: String? = null,
    val genres: List<String> = emptyList(),
    val videos: List<Video> = emptyList(),
)

/** An episode of a series. [id] is the stream id, such as tt1234567:1:2. */
data class Video(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val thumbnail: String? = null,
    val overview: String? = null,
)

data class CatalogDef(
    val type: String,
    val id: String,
    val name: String,
    /** True when the catalog needs a search term (it is a search catalog, not a browse row). */
    val searchRequired: Boolean,
    val supportsSearch: Boolean,
)

data class Manifest(
    val id: String,
    val name: String,
    val version: String,
    val logo: String?,
    val types: List<String>,
    val idPrefixes: List<String>,
    /** resource name -> (types, idPrefixes) as the addon declared them. Empty lists mean "all". */
    val resources: Map<String, ResourceDef>,
    val catalogs: List<CatalogDef>,
) {
    fun provides(resource: String) = resources.containsKey(resource)
}

data class ResourceDef(val types: List<String>, val idPrefixes: List<String>)

/** One installed addon. */
data class Addon(
    val manifestUrl: String,
    val manifest: Manifest,
    val enabled: Boolean = true,
) {
    /** The folder the manifest lives in, which every resource path hangs off. */
    val baseUrl: String get() = manifestUrl.removeSuffix("/manifest.json").trimEnd('/')
    val name: String get() = manifest.name
}

/** One playable stream option from an addon. */
data class StreamItem(
    val addonName: String,
    val addonUrl: String,
    val url: String,
    val name: String,
    val title: String,
    val filename: String?,
    val infoHash: String?,
    /** 0 when unknown. */
    val resolution: Int,
    val sizeBytes: Long,
    val cached: Boolean,
    val hdr: Boolean,
    val bingeGroup: String?,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Identity for "already tried": the same file from the same addon counts once. */
    val key: String get() = "$addonUrl|${filename ?: url}"
}
