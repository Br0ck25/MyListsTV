package tv.myliststv.launcher.screens

import tv.myliststv.launcher.services.*

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import tv.myliststv.launcher.data.AddonStore
import tv.myliststv.launcher.ui.AddonsScreen
import tv.myliststv.launcher.ui.Ui

/** Settings, Addons: the list of addons, in a page of its own. Home reloads when you come back. */
class AddonsActivity : Activity() {
    private lateinit var screen: AddonsScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = AddonsScreen(this) { AddonStore.needsRefresh = true }
        // A modal over Settings: a rounded card in a window that only dims what is behind it.
        setContentView(FrameLayout(this).apply { background = Ui.rounded(this@AddonsActivity, Ui.CARD, Ui.R_DIALOG); clipToOutline = true; addView(screen.view) })
        window.setGravity(android.view.Gravity.CENTER)
        window.setLayout(Ui.dp(this, 700), (resources.displayMetrics.heightPixels * 0.9f).toInt())   // after the content: a dialog-themed window resets its size when content is attached
        screen.start()
    }

    override fun onDestroy() { screen.stop(); super.onDestroy() }
}
