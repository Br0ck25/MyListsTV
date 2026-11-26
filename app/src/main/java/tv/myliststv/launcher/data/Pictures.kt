package tv.myliststv.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** The pictures the owner picks from the device (a wallpaper, an app's icon), kept small in the app's own folder. */
object Pictures {
    fun wallpaperFile(c: Context) = File(c.filesDir, "wallpaper.jpg")
    fun iconFile(c: Context, pkg: String) = File(File(c.filesDir, "icons"), "$pkg.png")

    /** Reads the picture at [uri], shrinks it to fit inside [w] by [h] and writes it to [out]. False when it is not a picture we can read. Not for the main thread. */
    fun save(c: Context, uri: Uri, out: File, w: Int, h: Int, png: Boolean): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; if (!png) inPreferredConfig = Bitmap.Config.RGB_565 }
        val raw = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (raw == null) false else {
            val k = minOf(1f, w.toFloat() / raw.width, h.toFloat() / raw.height)
            val bmp = if (k < 1f) Bitmap.createScaledBitmap(raw, (raw.width * k).toInt().coerceAtLeast(1), (raw.height * k).toInt().coerceAtLeast(1), true) else raw
            out.parentFile?.mkdirs()
            out.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (_: Throwable) { false }
}
