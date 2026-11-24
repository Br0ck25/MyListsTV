package tv.myliststv.launcher.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = tv.myliststv.launcher.App::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PicturesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun picture(w: Int, h: Int): Uri {
        val f = File.createTempFile("pic", ".png", ctx.cacheDir)
        f.outputStream().use { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }
    private fun sizeOf(f: File) = BitmapFactory.Options().also { it.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.path, it) }

    @Test fun aBigPictureIsShrunkToFitKeepingItsShape() {
        val out = File(ctx.cacheDir, "w.jpg")
        assertTrue(Pictures.save(ctx, picture(4000, 2000), out, 1280, 720, png = false))
        val o = sizeOf(out)
        assertTrue(o.outWidth <= 1280 && o.outHeight <= 720)
        assertEquals(2.0, o.outWidth.toDouble() / o.outHeight, 0.05)
    }

    @Test fun aSmallPictureIsNotBlownUp() {
        val out = File(ctx.cacheDir, "i/app.png")
        assertTrue(Pictures.save(ctx, picture(100, 100), out, 256, 256, png = true))
        assertEquals(100, sizeOf(out).outWidth)
    }

    @Test fun somethingThatIsNotAPictureIsRefused() {
        val f = File.createTempFile("not", ".txt", ctx.cacheDir).apply { writeText("hello") }
        assertFalse(Pictures.save(ctx, Uri.fromFile(f), File(ctx.cacheDir, "x.png"), 256, 256, png = true))
    }
}
