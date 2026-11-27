package tv.myliststv.launcher.data

import java.util.Locale

/**
 * Posters and details for files: the title, year and (for shows) season and episode are read from the file name, looked up in Cinemeta (the
 * standard Stremio metadata service), and the best match is kept. A file whose name says nothing useful simply has no picture.
 */
object FileMeta {
    private const val CINEMETA = "https://v3-cinemeta.strem.io"
    private const val TTL = 7L * 24 * 3600_000

    /** What a file name says. [year] and the episode numbers are null when it does not say them. */
    data class Guess(val title: String, val year: Int?, val season: Int?, val episode: Int?) {
        val isEpisode get() = season != null && episode != null
        val key: String get() = "${if (isEpisode) "s" else "m"}|${normalize(title)}|${year ?: 0}"
    }

    /** A file matched to a title. [episodeTitle], [still] and [overview] are for a show's episode. */
    data class Match(val meta: Meta, val episodeTitle: String? = null, val still: String? = null, val overview: String? = null)

    private val junk = Regex("""(?i)\b(480p|576p|720p|1080p|1080i|2160p|4k|uhd|web[ -]?dl|webrip|web|bluray|blu-ray|bdrip|brrip|dvdrip|hdtv|remux|x264|x265|h\.?264|h\.?265|hevc|avc|hdr10?\+?|dv|dolby|atmos|ddp?5\.?1|truehd|dts|aac|proper|repack|extended|unrated|imax)\b.*""")
    private val episodeTag = Regex("""(?i)\bs(\d{1,2})[ ._-]?e(\d{1,3})\b|\b(\d{1,2})x(\d{2,3})\b""")
    private val yearTag = Regex("""(?<![0-9])(19\d{2}|20\d{2})(?![0-9])""")

    fun normalize(s: String): String = s.lowercase(Locale.ROOT).replace("&", " and ").replace(Regex("""[^a-z0-9]+"""), " ").trim()

    /** Reads a file (or folder) name. Null when nothing like a title can be found. */
    fun guess(name: String): Guess? {
        var s = if (VideoFiles.isVideo(name)) name.substringBeforeLast('.') else name
        s = s.replace('_', ' ').replace(Regex("""(?<=[A-Za-z0-9])\.(?=[A-Za-z0-9])"""), " ").replace(Regex("""\[[^\]]*]"""), " ")
        var season: Int? = null; var episode: Int? = null
        val ep = episodeTag.find(s)
        if (ep != null) {
            season = (ep.groupValues[1].ifEmpty { ep.groupValues[3] }).toIntOrNull(); episode = (ep.groupValues[2].ifEmpty { ep.groupValues[4] }).toIntOrNull()
        }
        // The year is the first one that is not the very start of the name ("2012 (2009)" is the film 2012).
        val yr = yearTag.findAll(s).firstOrNull { it.range.first > 0 }
        val cut = listOfNotNull(ep?.range?.first, yr?.range?.first).minOrNull()
        var title = (if (cut != null) s.substring(0, cut) else s.replace(junk, ""))
        title = title.replace(Regex("""[(\[{]\s*$"""), "").replace(Regex("""\s+[-–]\s*$"""), "").replace(Regex("""\s+"""), " ").trim(' ', '-', '.', '(', ')')
        if (title.length < 2) return null
        return Guess(title, if (ep != null) null else yr?.value?.toInt(), season, episode)
    }

    /** The best of [results] for [g]: the same title (and, for a film, the same year give or take one); nothing when none fits, rather than a wrong picture. */
    fun pick(g: Guess, results: List<Meta>): Meta? {
        val want = normalize(g.title)
        val same = results.filter { normalize(it.name) == want }
        val pool = same.ifEmpty { results.filter { val n = normalize(it.name); n.startsWith("$want ") || want.startsWith("$n ") } }
        if (pool.isEmpty()) return null
        val y = g.year
        if (y != null) pool.firstOrNull { it.year?.take(4)?.toIntOrNull()?.let { m -> kotlin.math.abs(m - y) <= 1 } == true }?.let { return it }
        return if (y != null && pool.any { it.year != null }) null else pool.first()
    }

    // ---------------------------------------------------------------- lookups
    private val done = HashMap<String, Match?>()
    private val waiting = ArrayDeque<() -> Unit>()
    private var active = 0
    private const val PARALLEL = 3

    /** What is already known for [name] (a film, or an episode with its details), or null. Never asks the network. */
    @Synchronized fun peek(name: String): Match? = guess(name)?.let { done[it.key] }

    /** Finds the match for [g]. [cb] runs on the main thread, once. Lookups queue up three at a time, so a page of a hundred files is gentle. */
    fun lookup(g: Guess, cb: (Match?) -> Unit) {
        synchronized(this) { if (done.containsKey(g.key)) { val m = done[g.key]; Http.post { cb(m) }; return } }
        synchronized(this) { waiting += { run(g, cb) }; pump() }
    }

    @Synchronized private fun pump() {
        while (active < PARALLEL) { val next = waiting.removeFirstOrNull() ?: return; active++; next() }
    }

    private fun finish(g: Guess, m: Match?, cb: (Match?) -> Unit) {
        synchronized(this) { done[g.key] = m; active--; pump() }
        Http.post { cb(m) }
    }

    private fun run(g: Guess, cb: (Match?) -> Unit) {
        val type = if (g.isEpisode) "series" else "movie"
        var started = false   // the answer can come twice (the saved copy, then a fresh one); the first is enough
        Http.get(Urls.catalog(CINEMETA, type, "top", search = g.title), TTL) { body, _, _ ->
            if (started) return@get
            started = true
            val found = body?.let { try { Json.parseMetas(it) } catch (_: Exception) { null } }.orEmpty()
            val hit = pick(g, found)
            if (hit == null || !g.isEpisode) { finish(g, hit?.let { Match(it) }, cb); return@get }
            // A show: its episode list tells this episode's name, picture and story. Delivered as soon as the episodes are known, or after a few seconds with what there is.
            var delivered = false
            var latest: Meta = hit
            fun deliver() {
                if (delivered) return
                delivered = true
                val v = latest.videos.firstOrNull { it.season == g.season && it.episode == g.episode }
                finish(g, Match(latest, v?.title?.ifEmpty { null }, v?.thumbnail, v?.overview), cb)
            }
            Catalogs.meta("series", hit.id, hit) { full -> if (full != null) latest = full; if (latest.videos.isNotEmpty()) deliver() }
            Http.postDelayed(6000) { deliver() }
        }
    }
}
