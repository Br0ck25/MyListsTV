package tv.myliststv.launcher.data

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * What Search can find among the files: videos on this box and drives (asked of the media index each time) and videos on network shares
 * (found by walking each share once, kept on this box, and walked again when the copy is half a day old or you ask).
 */
object FileIndex {
    private const val MAX_FOLDERS = 2500
    private const val MAX_FILES = 40_000
    private const val MAX_MS = 90_000L
    private const val STALE_MS = 12L * 3600_000

    private var dir: File? = null
    private val walking = HashSet<String>()
    private val cache = HashMap<String, List<FileItem>>()

    fun init(c: Context) { dir = File(c.cacheDir, "fileindex").also { it.mkdirs() } }

    private fun fileFor(s: SmbShare) = File(dir, s.id + ".tsv")

    /** True while a share is being walked, so Search can say its results may be incomplete. */
    @Synchronized fun busy(): Boolean = walking.isNotEmpty()

    /** Walks [s] in the background if there is no copy or the copy is old (or [force]). [done] runs on the main thread when it finishes. */
    fun ensure(s: SmbShare, force: Boolean = false, done: (() -> Unit)? = null) {
        val f = fileFor(s)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < STALE_MS
        synchronized(this) { if (!force && fresh || !walking.add(s.id)) return }
        Http.background {
            try { write(s, walk(s)) } catch (_: Throwable) { } finally { synchronized(this) { walking.remove(s.id); cache.remove(s.id) } }
            if (done != null) Http.post { done() }
        }
    }

    /** Breadth first, so the shallow folders (where the films and shows are) come before anything deep. Stops at a limit, keeping what it has. */
    private fun walk(s: SmbShare): List<FileItem> {
        val out = ArrayList<FileItem>()
        val queue = ArrayDeque<String>().apply { add("") }
        val start = System.currentTimeMillis()
        var folders = 0
        while (queue.isNotEmpty() && folders < MAX_FOLDERS && out.size < MAX_FILES && System.currentTimeMillis() - start < MAX_MS) {
            val d = queue.removeFirst(); folders++
            val here = try { SmbClients.list(s, d) } catch (_: Throwable) { continue }
            for (i in here) {
                if (i.isDir) { if (!skip(i.name)) queue.add(i.path) } else out += i
            }
        }
        return out
    }

    private fun skip(name: String) = name.startsWith("@") || name.startsWith("#") || name.equals("lost+found", true) || name.equals("recycle", true)

    private fun write(s: SmbShare, items: List<FileItem>) {
        fileFor(s).writeText(items.joinToString("\n") { it.path + "\t" + it.size })
    }

    private fun read(s: SmbShare): List<FileItem> = synchronized(this) {
        cache[s.id] ?: try {
            val f = fileFor(s)
            if (!f.exists()) emptyList() else f.readLines().mapNotNull { l ->
                val p = l.substringBefore('\t'); if (p.isEmpty()) null else FileItem(p.substringAfterLast('/'), false, l.substringAfter('\t', "0").toLongOrNull() ?: 0, path = p, uri = s.uriFor(p))
            }
        } catch (_: Exception) { emptyList() }.also { cache[s.id] = it }
    }

    /** The words of a query, and whether a file name has all of them (in any order, ignoring case and punctuation). */
    fun tokens(q: String): List<String> = FileMeta.normalize(q).split(' ').filter { it.isNotEmpty() }

    fun matches(name: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val n = FileMeta.normalize(name)
        return tokens.all { it in n }
    }

    /** Names starting with what was typed first, then the rest in reading order. */
    fun rank(items: List<FileItem>, query: String): List<FileItem> {
        val q = FileMeta.normalize(query)
        return items.sortedWith(compareBy<FileItem> { !FileMeta.normalize(VideoFiles.title(it.name)).startsWith(q) }.thenComparator { a, b -> VideoFiles.natural(a.name, b.name) })
    }

    /** Videos whose names match [query], from this box and every saved share, [limit] at most. Runs on a background thread. */
    fun search(c: Context, query: String, limit: Int = 40): List<FileItem> {
        val t = tokens(query)
        if (t.isEmpty()) return emptyList()
        val found = ArrayList<FileItem>()
        if (LocalFiles.allowed(c)) LocalFiles.scan(c).filter { matches(it.name, t) }.mapTo(found) { FileItem(it.name, false, it.size, path = it.path, uri = it.uri) }
        for (s in SmbShares.all()) { ensure(s); read(s).filterTo(found) { matches(it.name, t) } }
        return rank(found, query).take(limit)
    }
}
