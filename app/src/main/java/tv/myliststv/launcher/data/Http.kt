package tv.myliststv.launcher.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small HTTP layer: HttpURLConnection (built in, no library), a three-thread pool, and a disk cache
 * that answers instantly from the last copy and refreshes in the background (stale-while-revalidate).
 */
object Http {
    private const val MAX_CACHE_BYTES = 40L * 1024 * 1024
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private var dir: File? = null

    fun init(context: Context) {
        val d = File(context.cacheDir, "http").also { it.mkdirs() }
        dir = d
        io.execute { prune(d) }
    }

    /** Calls [cb] on the main thread: first with the cached copy if any, then with a fresh copy if the cache was older than [ttlMs]. */
    fun get(url: String, ttlMs: Long, cb: (body: String?, fromCache: Boolean, error: String?) -> Unit) {
        io.execute {
            val f = file(url)
            val cached = try { if (f != null && f.exists()) f.readText() else null } catch (_: Exception) { null }
            val age = if (cached != null && f != null) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
            if (cached != null) main.post { cb(cached, true, null) }
            if (cached != null && age < ttlMs) return@execute
            try {
                val body = fetchBlocking(url, 12_000, 20_000)
                try { f?.writeText(body) } catch (_: Exception) {}
                main.post { cb(body, false, null) }
            } catch (e: Exception) {
                if (cached == null) main.post { cb(null, false, e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Runs [block] on the network pool. */
    fun background(block: () -> Unit) { io.execute(block) }

    fun post(block: () -> Unit) { main.post(block) }

    fun postDelayed(ms: Long, block: () -> Unit) { main.postDelayed(block, ms) }

    /** Blocking download for use from a background thread. Follows up to five redirects, including http to https. */
    fun fetchBlocking(url: String, connectMs: Int, readMs: Int, headers: Map<String, String> = emptyMap()): String {
        var current = url
        for (hop in 0..5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "MyListsTV/0.1")
            c.setRequestProperty("Accept", "application/json,*/*")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Redirect with no location")
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                return c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    /** Sends [body] with PUT and returns the HTTP status. For use from a background thread. */
    fun put(url: String, body: String): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "MyListsTV/0.1"); c.setRequestProperty("Content-Type", "text/plain")
            c.outputStream.use { it.write(body.toByteArray()) }
            return c.responseCode
        } finally { c.disconnect() }
    }

    /** Streams a download straight to a file (large guides must never be held in memory). */
    fun download(url: String, to: File) {
        var current = url
        for (hop in 0..5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 12_000; c.readTimeout = 30_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "MyListsTV/0.1")
            try {
                val code = c.responseCode
                if (code in 300..399) { current = URL(URL(current), c.getHeaderField("Location") ?: throw IllegalStateException("Redirect with no location")).toString(); continue }
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val tmp = File(to.path + ".part")
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(to); return
            } finally { c.disconnect() }
        }
        throw IllegalStateException("Too many redirects")
    }

    /** Downloads raw bytes (images). */
    fun fetchBytes(url: String, connectMs: Int = 10_000, readMs: Int = 20_000): ByteArray {
        var current = url
        for (hop in 0..5) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "MyListsTV/0.1")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("Redirect with no location")
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                return c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    fun file(url: String): File? = dir?.let { File(it, sha1(url)) }

    fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun clear() { dir?.listFiles()?.forEach { it.delete() } }

    fun cacheBytes(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0L

    private fun prune(d: File) {
        val files = d.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            total -= f.length()
            f.delete()
            if (total <= MAX_CACHE_BYTES * 3 / 4) break
        }
    }
}
