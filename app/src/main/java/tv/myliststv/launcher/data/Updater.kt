package tv.myliststv.launcher.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File

/** Sideload updates: finds the newest GitHub release, downloads its APK, and hands it to the system installer. */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/Br0ck25/MyListsTV/releases"

    /** [beta]: published as a pre-release (a build from main that has not been promoted to production). */
    data class Release(val build: Int, val tag: String, val apkUrl: String, val beta: Boolean = false)

    fun installedBuild(c: Context): Int {
        val p = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else @Suppress("DEPRECATION") p.versionCode
    }

    /** [cb] runs on the main thread with the newest release, or null if it could not be read. */
    fun latest(cb: (Release?) -> Unit) = Http.background {
        val r = try { parse(Http.fetchBlocking(RELEASES, 8000, 10000), Prefs.updateChannel == "beta") } catch (_: Exception) { null }
        Http.post { cb(r) }
    }

    /**
     * Picks the highest build number among releases that carry an APK. Production takes only releases that are not pre-releases (the
     * ones promoted by the owner); beta takes every release, so a promoted build counts for both. Drafts never count. Pure, so tests can check it.
     */
    fun parse(json: String, includeBeta: Boolean = false): Release? {
        val a = JSONArray(json)
        var best: Release? = null
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optBoolean("draft")) continue
            val pre = o.optBoolean("prerelease")
            if (pre && !includeBeta) continue
            val tag = o.optString("tag_name")
            val n = Version.build(tag) ?: continue
            val assets = o.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
            if (best == null || n > best.build) best = Release(n, tag, apk.optString("browser_download_url"), pre)
        }
        return best
    }

    /** Downloads the APK and opens the installer. [progress] gets a status line on the main thread. */
    fun install(c: Context, r: Release, progress: (String) -> Unit) = Http.background {
        try {
            Http.post { progress("Downloading ${r.tag}…") }
            val bytes = Http.fetchBytes(r.apkUrl, 15_000, 60_000)
            val dir = File(c.cacheDir, "update").also { it.mkdirs() }
            val f = File(dir, "MyListsTV.apk"); f.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
            Http.post {
                progress("Opening the installer…")
                c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) { Http.post { progress("Update failed: ${e.message}") } }
    }
}
