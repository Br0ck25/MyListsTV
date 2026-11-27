package tv.myliststv.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.util.Locale

/** One line of a Files page: a folder to open or a video to play. */
data class FileItem(
    val name: String,
    val isDir: Boolean,
    val size: Long = 0,
    /** Where to go to open a folder (a path on the drive, or inside a share). */
    val path: String = "",
    /** What the player is given for a video. */
    val uri: String = "",
    /** For a folder: how many videos are inside it, however deep. */
    val count: Int = 0,
)

/** What is and is not a video, how a file name reads as a title, and the small text helpers the Files pages share. */
object VideoFiles {
    private val extensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mts", "mpg", "mpeg", "webm", "flv", "3gp", "ogv", "divx")

    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

    /** "The.Movie.2019.1080p.mkv" is shown as it is, minus the extension; dots and underscores between words become spaces. */
    fun title(fileName: String): String {
        val base = if (isVideo(fileName)) fileName.substringBeforeLast('.') else fileName
        return base.replace('_', ' ').replace(Regex("""(?<=[A-Za-z0-9])\.(?=[A-Za-z0-9])"""), " ").trim().ifEmpty { fileName }
    }

    fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> String.format(Locale.US, "%d MB", bytes shr 20)
        else -> String.format(Locale.US, "%d KB", (bytes shr 10).coerceAtLeast(1))
    }

    /** Folders first, then videos; each in the order a person reads them (episode 2 before episode 10). */
    val order = Comparator<FileItem> { a, b -> if (a.isDir != b.isDir) if (a.isDir) -1 else 1 else natural(a.name, b.name) }

    fun natural(a: String, b: String): Int {
        val x = a.lowercase(Locale.ROOT); val y = b.lowercase(Locale.ROOT)
        var i = 0; var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                var ei = i; while (ei < x.length && x[ei].isDigit()) ei++
                var ej = j; while (ej < y.length && y[ej].isDigit()) ej++
                val nx = x.substring(i, ei).trimStart('0'); val ny = y.substring(j, ej).trimStart('0')
                if (nx.length != ny.length) return nx.length - ny.length
                val c = nx.compareTo(ny); if (c != 0) return c
                i = ei; j = ej
            } else {
                if (x[i] != y[j]) return x[i] - y[j]
                i++; j++
            }
        }
        return (x.length - i) - (y.length - j)
    }

    /** The id a played file is kept under (resume point, Continue Watching): stable for the same file. */
    fun playId(uri: String) = "file:" + Http.sha1(uri).take(16)
}

/**
 * Videos on this box and on any drive plugged into it, found through the media index. That index is the one door a TV app has to a USB
 * drive: this box has no file picker and no All files access screen to grant, so reading folders directly is not possible.
 */
object LocalFiles {
    /** [path] is the file's real path (used to show folders), [uri] what the player opens. */
    data class Video(val uri: String, val path: String, val name: String, val size: Long)

    val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun allowed(c: Context) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Every video the index knows of, on every volume. Runs on a background thread. */
    fun scan(c: Context): List<Video> {
        val out = ArrayList<Video>()
        val volumes: List<String> = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(c).toList() else listOf("external")
        for (v in volumes) {
            val base = MediaStore.Video.Media.getContentUri(v)
            try {
                c.contentResolver.query(base, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATA), null, null, null)?.use { q ->
                    while (q.moveToNext()) {
                        val path = q.getString(3) ?: continue
                        out += Video(ContentUris.withAppendedId(base, q.getLong(0)).toString(), path, q.getString(1) ?: path.substringAfterLast('/'), q.getLong(2))
                    }
                }
            } catch (_: Exception) { }
        }
        return out
    }

    /** What each drive is called: from the system where it says, else "Internal storage" and "USB drive". */
    fun labels(c: Context): Map<String, String> = try {
        val sm = c.getSystemService(StorageManager::class.java)
        sm.storageVolumes.mapNotNull { v -> v.directory?.path?.let { it to v.getDescription(c) } }.toMap()
    } catch (_: Throwable) { emptyMap() }

    private val rootPattern = Regex("""^(/storage/emulated/\d+|/storage/[^/]+|/mnt/[^/]+)""")

    fun rootOf(path: String): String = rootPattern.find(path)?.value ?: path.substringBeforeLast('/', "/")

    fun rootLabel(root: String, labels: Map<String, String>): String =
        labels[root]?.takeIf { it.isNotBlank() } ?: if (root.contains("/emulated/")) "Internal storage" else "USB drive (${root.substringAfterLast('/')})"

    /** One row per drive that holds a video. */
    fun roots(videos: List<Video>, labels: Map<String, String>): List<FileItem> =
        videos.groupBy { rootOf(it.path) }.map { (root, vs) -> FileItem(rootLabel(root, labels), true, path = root, count = vs.size) }
            .sortedWith(compareBy({ !it.name.startsWith("Internal") }, { it.name }))

    /** What is directly in [dir]: the folders below it that hold a video, and the videos in it. */
    fun children(videos: List<Video>, dir: String): List<FileItem> {
        val prefix = dir.trimEnd('/') + "/"
        val folders = LinkedHashMap<String, Int>()
        val files = ArrayList<FileItem>()
        for (v in videos) {
            if (!v.path.startsWith(prefix)) continue
            val rest = v.path.substring(prefix.length)
            if ('/' in rest) folders.merge(rest.substringBefore('/'), 1, Int::plus)
            else files += FileItem(v.name, false, v.size, uri = v.uri, path = v.path)
        }
        return (folders.map { (n, k) -> FileItem(n, true, path = prefix + n, count = k) } + files).sortedWith(VideoFiles.order)
    }
}
