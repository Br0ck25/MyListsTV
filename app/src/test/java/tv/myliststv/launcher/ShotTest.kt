package tv.myliststv.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Developer tool, not a check: draws screens to PNG files so the look can be reviewed. Runs only when SHOTS_DIR is set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = App::class, qualifiers = "w960dp-h540dp-xhdpi")
class ShotTest {
    private val dir = System.getenv("SHOTS_DIR")

    private fun settle(ms: Long) { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) } }

    private fun shot(a: Activity, name: String) {
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screens() {
        Assume.assumeTrue(dir != null)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); settle(500); shot(s, "settings")
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get(); settle(7000); shot(m, "home")
    }
}
