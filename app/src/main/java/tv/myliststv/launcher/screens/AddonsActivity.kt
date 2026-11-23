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
        setContentView(FrameLayout(this).apply { setBackgroundColor(Ui.BG); addView(screen.view) })
        screen.start()
    }

    override fun onDestroy() { screen.stop(); super.onDestroy() }
}
