package tv.myliststv.launcher.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Lets the player read a video straight from a network share: smb://host/share/folder/file.mkv. It reads a megabyte at a time and serves the
 * player from that, because the player asks for small pieces and a round trip to the NAS for each one would stutter.
 */
@UnstableApi
class SmbDataSource : BaseDataSource(true) {
    private var file: com.hierynomus.smbj.share.File? = null
    private var uri: Uri? = null
    private var pos = 0L
    private var end = 0L
    private var size = 0L
    private var buf = ByteArray(CHUNK)
    private var bufStart = 0L
    private var bufLen = 0

    override fun open(dataSpec: DataSpec): Long {
        val u = dataSpec.uri
        uri = u
        transferInitializing(dataSpec)
        try {
            val seg = u.pathSegments
            if (seg.size < 2) throw IOException("That is not a file on a share")
            val share = SmbShares.find(u.host.orEmpty(), if (u.port > 0) u.port else 445, seg[0]) ?: throw IOException("The network share ${seg[0]} is not set up on this box")
            val (f, len) = SmbClients.open(share, seg.drop(1).joinToString("/"))
            file = f; size = len
        } catch (e: IOException) { throw e } catch (e: Exception) { throw IOException(e.message ?: "Could not open the file on the share", e) }
        pos = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(size, pos + dataSpec.length) else size
        if (pos > size) throw IOException("Past the end of the file")
        bufLen = 0
        transferStarted(dataSpec)
        return end - pos
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (pos >= end) return C.RESULT_END_OF_INPUT
        if (pos < bufStart || pos >= bufStart + bufLen) {
            val f = file ?: throw IOException("The file is closed")
            val want = minOf(CHUNK.toLong(), size - pos).toInt()
            val n = try { f.read(buf, pos, 0, want) } catch (e: Exception) { throw IOException(e.message ?: "The share stopped answering", e) }
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufStart = pos; bufLen = n
        }
        val n = minOf(length.toLong(), bufStart + bufLen - pos, end - pos).toInt()
        System.arraycopy(buf, (pos - bufStart).toInt(), target, offset, n)
        pos += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } catch (_: Exception) { }
        file = null; uri = null
        transferEnded()
    }

    private companion object { const val CHUNK = 1 shl 20 }
}

/** Hands a smb:// address to the share reader and every other address to the normal one (web links, files and content on this box). */
@UnstableApi
class SchemeDataSource(private val normal: DataSource) : DataSource {
    private val smb = SmbDataSource()
    private var active: DataSource = normal
    private val listeners = ArrayList<TransferListener>()

    override fun addTransferListener(l: TransferListener) { listeners += l; normal.addTransferListener(l); smb.addTransferListener(l) }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme == "smb") smb else normal
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
    override fun getUri(): Uri? = active.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
    override fun close() { active.close() }
}
