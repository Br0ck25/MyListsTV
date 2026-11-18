package tv.myliststv.launcher.ui

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.myliststv.launcher.AppEntry
import tv.myliststv.launcher.AppRepository
import tv.myliststv.launcher.data.Http
import tv.myliststv.launcher.data.Prefs

/** Every installed app in a grid. Hold OK for app info and uninstall. */
class AppsScreen(private val act: Activity, private val onApp: (AppEntry) -> Unit, private val onMenu: (AppEntry) -> Unit) {
    val view: View
    private val rv = RecyclerView(act)

    init {
        rv.layoutManager = GridLayoutManager(act, 4)
        rv.clipToPadding = false
        rv.setPadding(Ui.dp(act, 42), Ui.dp(act, 12), Ui.dp(act, 42), Ui.dp(act, 20))
        rv.itemAnimator = null
        view = rv
    }

    fun start() {
        Http.background {
            val apps = AppRepository.load(act)
            Http.post {
                val models = apps.map { a ->
                    AppRepository.look(act.packageManager, a.component).let { (icon, banner) -> CardModel(a.label, null, payload = a, icon = icon, banner = banner) }
                }
                rv.adapter = CardAdapter(CardKind.APP, models, Prefs.lite(), { }, { m -> (m.payload as? AppEntry)?.let(onApp) }, { m -> (m.payload as? AppEntry)?.let(onMenu) })
            }
        }
    }
}
