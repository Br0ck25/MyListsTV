package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.json.JSONArray
import org.json.JSONObject
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** A folder shared by a NAS or another computer (SMB, the Windows file-sharing protocol), and what it takes to sign in to it. */
data class SmbShare(
    val id: String,
    val name: String,
    val host: String,
    val share: String,
    val user: String = "",
    val password: String = "",
    val domain: String = "",
    val port: Int = 445,
) {
    /** What the player is given for a file in it. No password is ever put in it; the player looks the share up by host, port and name. */
    fun uriFor(path: String): String {
        fun enc(t: String) = java.net.URLEncoder.encode(t, "UTF-8").replace("+", "%20")
        return "smb://" + (if (port == 445) host else "$host:$port") + "/" + enc(share) + path.split('/').filter { it.isNotEmpty() }.joinToString("") { "/" + enc(it) }
    }
}

/** The shares this box knows. The password is kept encrypted (see Secure), like an addon link that carries a key. */
object SmbShares {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) { sp = c.getSharedPreferences("smbshares", Context.MODE_PRIVATE) }

    fun all(): List<SmbShare> = try {
        val a = JSONArray(sp.getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            SmbShare(o.getString("id"), o.getString("name"), o.getString("host"), o.getString("share"), o.optString("user"), (Secure.unwrap(o.optString("pw")) ?: ""), o.optString("domain"), o.optInt("port", 445))
        }
    } catch (_: Exception) { emptyList() }

    private fun write(l: List<SmbShare>) {
        val a = JSONArray()
        l.forEach { s -> a.put(JSONObject().put("id", s.id).put("name", s.name).put("host", s.host).put("share", s.share).put("user", s.user).put("pw", Secure.wrap(s.password)).put("domain", s.domain).put("port", s.port)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    /** Adds a share, or replaces the one with the same id. */
    fun save(s: SmbShare) { write(all().filter { it.id != s.id } + s); SmbClients.forget(s) }

    fun remove(id: String) { all().firstOrNull { it.id == id }?.let { SmbClients.forget(it) }; write(all().filter { it.id != id }) }

    fun find(host: String, port: Int, share: String): SmbShare? = all().firstOrNull { it.host.equals(host, true) && it.port == port && it.share.equals(share, true) }

    fun newId() = "s" + System.currentTimeMillis()

    /** "nas", "nas:4445", "smb://nas/Movies" or "\\\\nas\\Movies" read as a host, a port and (when given) a share name. */
    fun parseAddress(text: String): Triple<String, Int, String> {
        var t = text.trim().removePrefix("smb://").replace('\\', '/').trim('/')
        val parts = t.split('/').filter { it.isNotEmpty() }
        t = parts.firstOrNull().orEmpty()
        val host = t.substringBefore(':')
        val port = t.substringAfter(':', "").toIntOrNull() ?: 445
        return Triple(host, port, parts.drop(1).joinToString("/"))
    }
}

/** Connections to shares, kept open between pages and while a video plays, and made again when one drops. */
object SmbClients {
    private class Conn(val client: SMBClient, val share: DiskShare)
    private val conns = HashMap<String, Conn>()

    private fun key(s: SmbShare) = "${s.host}:${s.port}/${s.share}|${s.user}"

    private val config: SmbConfig by lazy {
        SmbConfig.builder().withTimeout(20, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).withMultiProtocolNegotiate(true).build()
    }

    @Synchronized fun share(s: SmbShare): DiskShare {
        conns[key(s)]?.let { if (it.share.isConnected) return it.share; close(it) }
        val client = SMBClient(config)
        try {
            val conn = client.connect(s.host, s.port)
            val auth = if (s.user.isBlank()) AuthenticationContext.guest() else AuthenticationContext(s.user, s.password.toCharArray(), s.domain)
            val session = conn.authenticate(auth)
            val disk = session.connectShare(s.share) as? DiskShare ?: throw java.io.IOException("${s.share} is not a folder share")
            conns[key(s)] = Conn(client, disk)
            return disk
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) { }
            throw e
        }
    }

    @Synchronized fun forget(s: SmbShare) { conns.remove(key(s))?.let { close(it) } }

    private fun close(c: Conn) { try { c.client.close() } catch (_: Exception) { } }

    private fun dos(path: String) = path.trim('/').replace('/', '\\')

    /** What is in [dir] (a path inside the share, "" for its top): folders, and video files with the address the player opens. */
    fun list(s: SmbShare, dir: String): List<FileItem> {
        val d = share(s)
        val here = dir.trim('/')
        val items = d.list(dos(here)).mapNotNull { f ->
            val n = f.fileName
            if (n == "." || n == ".." || n.startsWith("$") || n.startsWith(".")) return@mapNotNull null
            val isDir = (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            val hidden = (f.fileAttributes and (FileAttributes.FILE_ATTRIBUTE_HIDDEN.value or FileAttributes.FILE_ATTRIBUTE_SYSTEM.value)) != 0L
            if (hidden) return@mapNotNull null
            val rel = if (here.isEmpty()) n else "$here/$n"
            when {
                isDir -> FileItem(n, true, path = rel)
                VideoFiles.isVideo(n) -> FileItem(n, false, f.endOfFile, path = rel, uri = s.uriFor(rel))
                else -> null
            }
        }
        return items.sortedWith(VideoFiles.order)
    }

    /** Opens a file for reading and says how long it is. */
    fun open(s: SmbShare, path: String): Pair<com.hierynomus.smbj.share.File, Long> {
        val f = share(s).openFile(dos(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        return f to f.getFileInformation(FileStandardInformation::class.java).endOfFile
    }
}
