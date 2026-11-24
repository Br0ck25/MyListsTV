package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
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
                .setItems(arrayOf("Mark as watched", "Remove from Continue watching")) { _, i ->
                    if (i == 0) markWatched(p) else WatchStore.remove(p.metaId)
                    refreshContinue()
                }.show()
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
        if (withContinue) { addContinue(); addAiring() }
        val hidden = Prefs.homeHidden
        val defs = Catalogs.browseRows(type, 8).filter { (_, cat) -> Catalogs.rowTitle(type, cat) !in hidden }
        if (defs.isEmpty() && Catalogs.enabled().isEmpty()) {
            heroTitle.text = "No catalog addon is on"
            heroDesc.text = "Turn one on in Settings, then Addons."
        }
        if (withContinue && StreamResolver.providers("movie", "tt0000001").isEmpty()) {
            heroTitle.text = "Welcome to My Lists TV"
            heroMeta.text = "One more step before you can play"
            heroDesc.text = "Install a stream addon in Settings, then Addons. You can paste its link from your phone."
        }
        for ((addon, cat) in defs) {
            val title = Catalogs.rowTitle(type, cat)
            val wide = Prefs.horizontalPosters
            val row = RowModel(title, "via ${if (addon.name.startsWith("My Lists")) "My Lists" else addon.name}", if (wide) CardKind.WIDE else CardKind.POSTER, emptyList(), loaded = false)
            rows += row
            rows.sortBy { Prefs.homeRank(it.title) }
            Catalogs.load(addon, cat) { metas, _ ->
                val idx = rows.indexOf(row)
                if (idx < 0) return@load
                if (metas != null) {
                    row.items = metas.filter(Profiles::allows).take(30).map { if (wide) CardModel(it.name, it.background ?: it.poster, listOfNotNull(it.year, it.rating?.let { r -> "\u2605 $r" }).joinToString("  \u00b7  "), payload = it) else CardModel(it.name, it.poster, payload = it, done = Watched.titleDone(it)) }
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

    /** Hold OK on a Continue watching card, Mark as watched: the episode (or movie) is done, here and in the account's watched history. */
    private fun markWatched(e: WatchStore.Entry) {
        val episode = e.type == "series" && e.season >= 0
        if (episode) { Watched.set(e.metaId, e.videoId, true); WatchStore.remove(e.metaId) }
        else WatchStore.save(e.copy(positionMs = 1L, durationMs = 1L, updated = System.currentTimeMillis()))
        Progress.markWatched(e.metaId, e.type, e.season, e.episode, true)
        tv.myliststv.launcher.ui.Pop.toast(c, if (Prefs.accountLink.isNotEmpty()) "Marked watched, and added to My Lists" else "Marked watched")
    }

    private fun addContinue() {
        if (!Prefs.showContinue || "Continue watching" in Prefs.homeHidden) return
        val cw = WatchStore.continueWatching()
        if (cw.isEmpty()) return
        val models = cw.map { e ->
            val sub = if (e.type == "series" && e.season >= 0) "S${e.season} · E${e.episode}" else "Movie"
            CardModel(e.name, e.background ?: e.poster, if (e.durationMs == 0L) "Next up · $sub" else "$sub · ${(e.fraction * 100).toInt()}% watched", e.fraction, e, badgeEnd = EpisodesLeft.timeLeft(e))
        }
        val row = RowModel("Continue watching", "This device", CardKind.WIDE, models)
        rows.add(row)
        // Episodes left needs the show's episode list: ask for it per show and redraw just that card (the row is not rebuilt, so focus stays).
        cw.forEachIndexed { i, e ->
            if (e.type != "series") return@forEachIndexed
            Catalogs.meta("series", e.metaId, Meta(e.metaId, "series", e.name)) { m ->
                val info = m?.let { EpisodesLeft.compute(it.videos, e, System.currentTimeMillis()) } ?: return@meta
                val card = models.getOrNull(i) ?: return@meta
                val left = EpisodesLeft.leftText(info.left)
                val end = info.airs ?: card.badgeEnd ?: m.runtime
                if (card.badge == left && card.badgeEnd == end) return@meta
                card.badge = left; card.badgeEnd = end
                if (row in rows) row.adapter?.notifyItemChanged(i)
            }
        }
    }

    /** The next episode of each followed show, soonest first. Tapping one opens the show. */
    private fun addAiring() {
        if (!Prefs.showAiring || "Airing next" in Prefs.homeHidden) return
        val a = Progress.airing()
        if (a.isEmpty()) return
        val day = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US); val parse = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        rows.add(RowModel("Airing next", "From your account", CardKind.WIDE, a.map { s ->
            val date = try { day.format(parse.parse(s.airDate)!!) } catch (_: Exception) { s.airDate }
            val tag = if (s.premiere) "Season premiere" else if (s.finale) "Season finale" else "S${s.season} \u00b7 E${s.episode}"
            CardModel(s.name, s.still ?: s.poster, "$tag \u00b7 $date", 0f, Meta(s.id, "series", s.name, s.poster, s.still))
        }))
    }

    /** Called when coming back from the player, or when the account's lists arrive, so they show without reloading catalogs. */
    fun refreshContinue() {
        if (!withContinue) return
        rows.removeAll { it.title == "Continue watching" || it.title == "Airing next" }
        addContinue(); addAiring()
        rows.sortBy { Prefs.homeRank(it.title) }
        list.changedAll()
    }

    private var heroId: String? = null

    private fun hero(m: CardModel) {
        when (val p = m.payload) {
            is Meta -> {
                onBackdrop(p.background ?: p.poster)
                heroTitle.text = p.name
                heroMeta.text = listOfNotNull(p.year, p.rating?.let { "★ $it" }, p.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                heroDesc.text = p.description ?: ""
                heroId = p.id
                // A tile with no story yet (Airing next) asks for the title's details.
                if (p.description == null && p.id.startsWith("tt")) Catalogs.meta(p.type, p.id, p) { full ->
                    if (full != null && heroId == p.id) {
                        full.background?.let(onBackdrop)
                        heroMeta.text = listOfNotNull(full.year, full.rating?.let { "★ $it" }, full.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                        heroDesc.text = full.description ?: ""
                    }
                }
            }
            is WatchStore.Entry -> {
                onBackdrop(p.background ?: p.poster)
                heroTitle.text = p.name
                val where = if (p.type == "series" && p.season >= 0) "S${p.season} · E${p.episode}  ${p.episodeTitle ?: ""}".trim() else "Movie"
                val progress = if (p.durationMs == 0L) "Next up" else "${(p.fraction * 100).toInt()}% watched"
                heroMeta.text = "$where   $progress"; heroDesc.text = ""
                heroId = p.metaId
                // The title's own details (year, rating, genres, story), like any other tile.
                Catalogs.meta(p.type, p.metaId, null) { full ->
                    if (full != null && heroId == p.metaId) {
                        full.background?.let(onBackdrop)
                        heroMeta.text = listOfNotNull(where, progress, full.year, full.rating?.let { "★ $it" }, full.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                        heroDesc.text = full.description ?: ""
                    }
                }
            }
            is AppEntry -> { heroTitle.text = p.label; heroMeta.text = "Installed app"; heroDesc.text = "Press OK to open." }
        }
    }
}
