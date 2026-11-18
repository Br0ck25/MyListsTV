package tv.myliststv.launcher.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import tv.myliststv.launcher.AppEntry
import tv.myliststv.launcher.AppRepository
import tv.myliststv.launcher.data.*

/**
 * Home, Movies and Series: a hero line for the focused title and rows of posters below it.
 * Rows fill in as their catalogs answer, cached copies first.
 */
class BrowseScreen(
    private val act: Activity,
    private val type: String?,
    private val withContinue: Boolean,
    private val withApps: Boolean,
    private val onMeta: (Meta) -> Unit,
    private val onResume: (WatchStore.Entry) -> Unit,
    private val onApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
    private val onBackdrop: (String?) -> Unit = {},
) {
    private val c = act
    private val lite = Prefs.lite()
    private val rows = ArrayList<RowModel>()
    private val heroTitle = Ui.text(c, "", 30f, Ui.TEXT, true, 1)
    private val heroMeta = Ui.text(c, "", 14f, Ui.ACCENT, true, 1)
    private val heroDesc = Ui.text(c, "", 14f, Ui.TEXT2, false, 3)
    private val list = RowsList(c, rows, lite, ::hero, { model ->
        when (val p = model.payload) {
            is Meta -> onMeta(p)
            is WatchStore.Entry -> onResume(p)
            is AppEntry -> onApp(p)
        }
    }, { model ->
        when (val p = model.payload) {
            is AppEntry -> onAppMenu(p)
            is WatchStore.Entry -> android.app.AlertDialog.Builder(c).setTitle(p.name)
                .setItems(arrayOf("Remove from Continue watching")) { _, _ -> WatchStore.remove(p.metaId); refreshContinue() }.show()
        }
    })
    val view: View

    init {
        val root = Ui.vbox(c)
        val hero = Ui.vbox(c).apply { setPadding(Ui.dp(c, 48), Ui.dp(c, 10), Ui.dp(c, 420), Ui.dp(c, 6)) }
        hero.addView(heroTitle); hero.addView(heroMeta); hero.addView(heroDesc)
        (heroMeta.layoutParams as? LinearLayout.LayoutParams)
        root.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 120)))
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = root
    }

    fun start() {
        rows.clear()
        if (withContinue) addContinue()
        val hidden = Prefs.homeHidden
        val defs = Catalogs.browseRows(type, 8).filter { (_, cat) -> Catalogs.rowTitle(type, cat) !in hidden }
        if (defs.isEmpty() && Catalogs.enabled().isEmpty()) {
            heroTitle.text = "No catalog addon is on"
            heroDesc.text = "Turn one on in the Addons tab."
        }
        if (withContinue && StreamResolver.providers("movie", "tt0000001").isEmpty()) {
            heroTitle.text = "Welcome to My Lists TV"
            heroMeta.text = "One more step before you can play"
            heroDesc.text = "Install a stream addon in the Addons tab. You can paste its link from your phone."
        }
        for ((addon, cat) in defs) {
            val title = Catalogs.rowTitle(type, cat)
            val wide = Prefs.horizontalPosters
            val row = RowModel(title, "via ${addon.name}", if (wide) CardKind.WIDE else CardKind.POSTER, emptyList(), loaded = false)
            rows += row
            rows.sortBy { Prefs.homeRank(it.title) }
            Catalogs.load(addon, cat) { metas, _ ->
                val idx = rows.indexOf(row)
                if (idx < 0) return@load
                if (metas != null) {
                    row.items = metas.filter(Profiles::allows).take(30).map { if (wide) CardModel(it.name, it.background ?: it.poster, listOfNotNull(it.year, it.rating?.let { r -> "\u2605 $r" }).joinToString("  \u00b7  "), payload = it) else CardModel(it.name, it.poster, payload = it) }
                    row.loaded = true
                } else if (row.items.isEmpty()) row.loaded = true
                list.changed(idx)
                if (heroTitle.text.isNullOrEmpty() && row.items.isNotEmpty()) hero(row.items.first())
            }
        }
        list.changedAll()
        if (withApps && Prefs.showAppsRow && "Your apps" !in hidden) {
            Http.background {
                val apps = AppRepository.forHome(act)
                Http.post {
                    val row = RowModel("Your apps", "Installed", CardKind.APP, apps.map { a ->
                        AppRepository.look(act.packageManager, a.component).let { (icon, banner) -> CardModel(a.label, null, payload = a, icon = icon, banner = banner) }
                    })
                    rows += row
                    rows.sortBy { Prefs.homeRank(it.title) }
                    list.changedAll()
                }
            }
        }
    }

    private fun addContinue() {
        if (!Prefs.showContinue || "Continue watching" in Prefs.homeHidden) return
        val cw = WatchStore.continueWatching()
        if (cw.isEmpty()) return
        rows.add(RowModel("Continue watching", "This device", CardKind.WIDE, cw.map { e ->
            val sub = if (e.type == "series" && e.season >= 0) "S${e.season} · E${e.episode}" else "Movie"
            CardModel(e.name, e.background ?: e.poster, "$sub · ${(e.fraction * 100).toInt()}% watched", e.fraction, e)
        }))
    }

    /** Called when coming back from the player so the progress shows without reloading catalogs. */
    fun refreshContinue() {
        if (!withContinue) return
        rows.removeAll { it.title == "Continue watching" }
        addContinue()
        rows.sortBy { Prefs.homeRank(it.title) }
        list.changedAll()
    }

    private fun hero(m: CardModel) {
        when (val p = m.payload) {
            is Meta -> {
                onBackdrop(p.background ?: p.poster)
                heroTitle.text = p.name
                heroMeta.text = listOfNotNull(p.year, p.rating?.let { "★ $it" }, p.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                heroDesc.text = p.description ?: ""
            }
            is WatchStore.Entry -> {
                onBackdrop(p.background ?: p.poster)
                heroTitle.text = p.name
                heroMeta.text = if (p.type == "series" && p.season >= 0) "S${p.season} · E${p.episode}  ${p.episodeTitle ?: ""}".trim() else "Resume"
                heroDesc.text = "${(p.fraction * 100).toInt()}% watched"
            }
            is AppEntry -> { heroTitle.text = p.label; heroMeta.text = "Installed app"; heroDesc.text = "Press OK to open." }
        }
    }
}
