package tv.myliststv.launcher.data

import org.json.JSONObject

/**
 * Collections from My Lists: groups of tiles (Streaming Services, Genres, Studios, or the account's own folders) that Home shows as
 * rows. Each tile is a folder with a cover picture and the catalogs inside it. The list comes from mylistsaddon.com for the signed-in
 * account (or the default set when nobody is signed in), so what is set up on the website is what shows here.
 */
object Collections {
    private const val ORIGIN = "https://mylistsaddon.com"
    private const val TTL = 30L * 60_000

    /** One list inside a folder: which addon has it, its catalog id, its name and whether it holds movies or series. */
    data class Source(val addonId: String, val catalogId: String, val name: String, val type: String)

    /** A tile. [cover] is the picture attached to it on the website; [hideTitle] is the website's "no name on the tile" setting. */
    data class Folder(val id: String, val title: String, val cover: String?, val hideTitle: Boolean, val sources: List<Source>) {
        /** The website's generated pictures already have the name drawn on them, so the tile does not print it again. */
        val nameIsOnCover: Boolean get() = cover?.contains("/api/channel-poster") == true
    }

    /** [own] is the website's mark for a collection the account made itself, as against the built-in set every account is given. */
    data class Collection(val id: String, val title: String, val folders: List<Folder>, val own: Boolean = false)

    /** The row titles last loaded, so the Arrange screen can list them. */
    @Volatile var knownTitles: List<String> = emptyList()
        private set

    /** The folder the page being opened should show. */
    @Volatile var opened: Folder? = null

    /** The account's collections when signed in, else the default set. */
    fun target(): String = Prefs.accountLink.takeIf { it.isNotEmpty() }?.let { Pairing.targetOf(it) }?.takeIf { it.isNotEmpty() } ?: "default"

    fun url(): String = "$ORIGIN/api/nuvio-tv/collections/${target()}"

    /** The addon-protocol-shaped answer in, the visible collections out. Anything hidden or empty is left out. */
    fun parse(body: String): List<Collection> = try {
        val a = JSONObject(body).optJSONArray("collections")
        (0 until (a?.length() ?: 0)).mapNotNull { i ->
            val c = a!!.optJSONObject(i) ?: return@mapNotNull null
            if (c.optBoolean("hidden") || !c.optBoolean("enabled", true)) return@mapNotNull null
            val fs = c.optJSONArray("folders")
            val folders = (0 until (fs?.length() ?: 0)).mapNotNull { j ->
                val f = fs!!.optJSONObject(j) ?: return@mapNotNull null
                if (f.optBoolean("hidden") || !f.optBoolean("enabled", true)) return@mapNotNull null
                val ss = f.optJSONArray("catalogSources")
                val sources = (0 until (ss?.length() ?: 0)).mapNotNull { k ->
                    val s = ss!!.optJSONObject(k) ?: return@mapNotNull null
                    val id = s.optString("catalogId"); if (id.isEmpty()) return@mapNotNull null
                    Source(s.optString("addonId"), id, s.optString("name").ifEmpty { s.optString("title") }, s.optString("type", "movie"))
                }
                val title = f.optString("title").ifEmpty { f.optString("name") }
                if (title.isEmpty() || sources.isEmpty()) return@mapNotNull null
                Folder(f.optString("id").ifEmpty { title }, title, cover(f.optString("coverImageUrl")), f.optBoolean("hideTitle"), sources)
            }
            val title = c.optString("title").ifEmpty { c.optString("name") }
            if (title.isEmpty() || folders.isEmpty()) null else Collection(c.optString("id").ifEmpty { title }, title, folders, c.optBoolean("own"))
        }
    } catch (_: Exception) { emptyList() }

    /** A tile is wide, so a generated tall picture is asked for in the wide shape; a picture the owner attached is used as it is. */
    fun cover(url: String?): String? {
        val u = url?.takeIf { it.isNotBlank() } ?: return null
        val full = if (u.startsWith("/")) ORIGIN + u else u
        return if (full.contains("/api/channel-poster") && !full.contains("format=")) full + (if (full.contains("?")) "&" else "?") + "format=landscape" else full
    }

    /** Only what the signed-in account made itself (the website marks those). Everything else is the built-in set every account is given. Signed out there is nothing of one's own, so nothing shows. */
    fun own(all: List<Collection>, signedIn: Boolean): List<Collection> = if (signedIn) all.filter { it.own } else emptyList()

    /** [cb] may run twice: once from the cache, once with fresh data. */
    fun load(cb: (List<Collection>) -> Unit) {
        Http.get(url(), TTL) { body, _, _ ->
            val list = body?.let { own(parse(it), Prefs.accountLink.isNotEmpty()) } ?: return@get
            knownTitles = list.map { it.title }
            cb(list)
        }
    }

    /** The addon that holds a folder's list: the one with that id if installed, else My Lists (the default or the account's link). */
    fun addonFor(s: Source): Addon? {
        val on = Catalogs.enabled()
        return on.firstOrNull { it.manifest.id == s.addonId } ?: on.firstOrNull { it.manifest.id.startsWith("app.my-list") } ?: on.firstOrNull { it.name.startsWith("My Lists") }
    }

    /** Where a source's catalog is fetched from: its addon, or the default My Lists link when that addon is not installed. */
    fun baseFor(s: Source): String = addonFor(s)?.baseUrl ?: "$ORIGIN/nuvio-default"

    /** The row's name on a folder's page: the list's name, with Movies or Series when the same name is there for both. */
    fun rowName(s: Source, all: List<Source>): String {
        val both = all.count { it.name == s.name } > 1
        return if (both) "${s.name} · ${if (s.type == "series") "Series" else "Movies"}" else s.name
    }
}
