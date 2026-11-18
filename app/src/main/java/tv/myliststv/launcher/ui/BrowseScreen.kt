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
) {
    private val c = act
    private val lite = Prefs.lite()
    private val rows = ArrayList<RowModel>()
    private val heroTitle = Ui.text(c, "", 26f, Ui.TEXT, true, 1)
    private val heroMeta = Ui.text(c, "", 14f, Ui.ACCENT, true, 1)
    private val heroDesc = Ui.text(c, "", 14f, 0xFFC3C8DC.toInt(), false, 3)
    private val list = RowsList(c, rows, lite, ::hero, { model ->
        when (val p = model.payload) {
            is Meta -> onMeta(p)
            is WatchStore.Entry -> onResume(p)
            is AppEntry -> onApp(p)
        }
    }, { model -> (model.payload as? AppEntry)?.let(onAppMenu) })
    val view: View

    init {
        val root = Ui.vbox(c)
        val hero = Ui.vbox(c).apply { setPadding(Ui.dp(c, 48), Ui.dp(c, 10), Ui.dp(c, 120), Ui.dp(c, 6)) }
        hero.addView(heroTitle); hero.addView(heroMeta); hero.addView(heroDesc)
        (heroMeta.layoutParams as? LinearLayout.LayoutParams)
        root.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 112)))
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = root
    }

    fun start() {
        rows.clear()
        if (withContinue) addContinue()
        val defs = Catalogs.browseRows(type, 8)
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
            val label = if (cat.type == "movie") "Movies" else "Series"
            val title = if (type == null && !cat.name.contains(label, true)) "${cat.name} · $label" else cat.name
            val row = RowModel(title, "via ${addon.name}", CardKind.POSTER, emptyList(), loaded = false)
            rows += row
            Catalogs.load(addon, cat) { metas, _ ->
                val idx = rows.indexOf(row)
                if (idx < 0) return@load
                if (metas != null) {
                    row.items = metas.take(30).map { CardModel(it.name, it.poster, payload = it) }
                    row.loaded = true
                } else if (row.items.isEmpty()) row.loaded = true
                list.changed(idx)
                if (heroTitle.text.isNullOrEmpty() && row.items.isNotEmpty()) hero(row.items.first())
            }
        }
        list.changedAll()
        if (withApps && Prefs.showAppsRow) {
            Http.background {
                val apps = AppRepository.load(act)
                Http.post {
                    val row = RowModel("Your apps", "Installed", CardKind.APP, apps.map { a ->
                        CardModel(a.label, null, payload = a, icon = try { act.packageManager.getActivityIcon(a.component) } catch (_: Exception) { null })
                    })
                    rows += row
                    list.inserted(rows.size - 1)
                }
            }
        }
    }

    private fun addContinue() {
        if (!Prefs.showContinue) return
        val cw = WatchStore.continueWatching()
        if (cw.isEmpty()) return
        rows.add(0, RowModel("Continue watching", "This device", CardKind.WIDE, cw.map { e ->
            val sub = if (e.type == "series" && e.season >= 0) "S${e.season} · E${e.episode}" else "Movie"
            CardModel(e.name, e.background ?: e.poster, "$sub · ${(e.fraction * 100).toInt()}% watched", e.fraction, e)
        }))
    }

    /** Called when coming back from the player so the progress shows without reloading catalogs. */
    fun refreshContinue() {
        if (!withContinue) return
        val had = rows.isNotEmpty() && rows[0].title == "Continue watching"
        if (had) rows.removeAt(0)
        addContinue()
        list.changedAll()
    }

    private fun hero(m: CardModel) {
        when (val p = m.payload) {
            is Meta -> {
                heroTitle.text = p.name
                heroMeta.text = listOfNotNull(p.year, p.rating?.let { "★ $it" }, p.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                heroDesc.text = p.description ?: ""
            }
            is WatchStore.Entry -> {
                heroTitle.text = p.name
                heroMeta.text = if (p.type == "series" && p.season >= 0) "S${p.season} · E${p.episode}  ${p.episodeTitle ?: ""}".trim() else "Resume"
                heroDesc.text = "${(p.fraction * 100).toInt()}% watched"
            }
            is AppEntry -> { heroTitle.text = p.label; heroMeta.text = "Installed app"; heroDesc.text = "Press OK to open." }
        }
    }
}
