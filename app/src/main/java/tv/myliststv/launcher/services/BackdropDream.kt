package tv.myliststv.launcher.services

import tv.myliststv.launcher.screens.*

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.ui.Ui

/** Screensaver: the backdrops of what is on Home, one every 12 seconds, with the title. Choose it in Android's screensaver settings. */
class BackdropDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var items: List<Meta> = emptyList()
    private var at = 0
    private lateinit var pic: ImageView
    private lateinit var name: android.widget.TextView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true
        pic = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        name = Ui.text(this, "", 24f, Ui.TEXT, true, 1)
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Ui.BG)
            addView(pic, FrameLayout.LayoutParams(-1, -1))
            addView(android.view.View(this@BackdropDream).apply { background = Ui.scrimBottom() }, FrameLayout.LayoutParams(-1, Ui.dp(this@BackdropDream, 240), Gravity.BOTTOM))
            addView(name, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(Ui.dp(this@BackdropDream, 48), 0, 0, Ui.dp(this@BackdropDream, 40)) })
        })
        AddonStore.load {
            val found = ArrayList<Meta>()
            Catalogs.browseRows(null, 3).forEach { (addon, cat) -> Catalogs.load(addon, cat) { metas, _ -> if (metas != null) { found += metas.filter { it.background != null && Profiles.allows(it) }.take(15); items = found.distinct() } } }
            handler.postDelayed(::next, 1500)
        }
    }

    private fun next() {
        if (items.isNotEmpty()) {
            val m = items[at++ % items.size]
            ImageLoader.load(pic, m.background, Ui.dp(this, 960), Ui.dp(this, 540), fade = true); name.text = m.name
        }
        handler.postDelayed(::next, 12_000)
    }

    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }
}
