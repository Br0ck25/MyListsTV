package tv.myliststv.launcher.data

import tv.myliststv.launcher.*

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * Loads images at the size they are shown, in RGB_565, into a byte-bounded memory cache and a
 * disk cache. One image is never decoded larger than its tile.
 */
object ImageLoader {
    private val pool = Executors.newFixedThreadPool(2)
    private var memory = LruCache<String, Bitmap>(6 * 1024 * 1024)
    private var dir: File? = null

    fun init(cacheDir: File, memoryBytes: Int) {
        dir = File(cacheDir, "img").also { it.mkdirs() }
        memory = object : LruCache<String, Bitmap>(memoryBytes) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
        pool.execute { prune() }
    }

    fun trim() { memory.evictAll() }

    /** [fade] fades the picture in when it arrives from the network (not when it is already in memory). */
    fun load(view: ImageView, url: String?, w: Int, h: Int, alpha: Boolean = false, fade: Boolean = false, onLoaded: ((Boolean) -> Unit)? = null) {
        view.setTag(TAG, url)
        if (url.isNullOrEmpty()) { view.setImageDrawable(null); return }
        val key = "$url@$w"
        val hit = memory.get(key)
        if (hit != null) { view.setImageBitmap(hit); onLoaded?.invoke(true); return }
        view.setImageDrawable(null)
        pool.execute {
            val bmp = try { decode(url, w, h, alpha) } catch (_: Throwable) { null }
            if (bmp == null) { Http.post { if (view.getTag(TAG) == url) onLoaded?.invoke(false) }; return@execute }
            memory.put(key, bmp)
            Http.post {
                if (view.getTag(TAG) == url) {
                    view.setImageDrawable(BitmapDrawable(view.resources, bmp))
                    if (fade) { view.alpha = 0f; view.animate().alpha(1f).setDuration(220).start() }
                    onLoaded?.invoke(true)
                }
            }
        }
    }

    private fun decode(url: String, w: Int, h: Int, alpha: Boolean): Bitmap? {
        val f = dir?.let { File(it, Http.sha1(url)) }
        val bytes: ByteArray = if (f != null && f.exists() && f.length() > 0) f.readBytes() else {
            val b = Http.fetchBytes(url)
            try { f?.writeBytes(b) } catch (_: Exception) {}
            b
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (alpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun prune() {
        val files = dir?.listFiles() ?: return
        var total = files.sumOf { it.length() }
        val max = 60L * 1024 * 1024
        if (total <= max) return
        for (f in files.sortedBy { it.lastModified() }) {
            total -= f.length(); f.delete()
            if (total <= max * 3 / 4) break
        }
    }

    fun clearDisk() { dir?.listFiles()?.forEach { it.delete() }; memory.evictAll() }

    private val TAG = tv.myliststv.launcher.R.id.tag_image
}
