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
    private val heroTitle = Ui.text(c, "", 34f, Ui.TEXT, true, 1)
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
            is WatchStore.Entry -> Sheet.menu(c, p.name, if (p.type == "series" && p.season >= 0) "S${p.season} \u00b7 E${p.episode}" else null, listOf(
                "Go to details" to { onMeta(Meta(p.metaId, p.type, p.name, p.poster, p.background)) },
                "Mark as watched" to { markWatched(p); refreshContinue() },
                "Remove from Continue Watching" to { WatchStore.remove(p.metaId); refreshContinue() }))
        }
    })
    val view: View

    init {
        val root = Ui.vbox(c)
        val hero = Ui.vbox(c).apply { setPadding(Ui.dp(c, 48), Ui.dp(c, 10), Ui.dp(c, 420), Ui.dp(c, 6)) }
        hero.addView(heroTitle); hero.addView(heroMeta); hero.addView(heroDesc)
        (heroMeta.layoutParams as? LinearLayout.LayoutParams)
        root.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 128)))
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = root
    }

    fun start() {
        rows.clear()
        if (withContinue) { continueRow()?.let { rows += it }; airingRow()?.let { rows += it } }
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
        if (withApps && Prefs.showAppsRow && "Your Apps" !in hidden) {
            Http.background {
                val apps = AppRepository.forHome(act)
                Http.post {
                    val row = RowModel("Your Apps", "Installed", CardKind.APP, apps.map { a ->
                        AppRepository.look(act, a.component).let { (icon, banner) -> CardModel(a.label, null, payload = a, icon = icon, banner = banner) }
                    })
                    rows += row
                    rows.sortBy { Prefs.homeRank(it.title) }
                    list.changedAll()
                }
            }
        }
    }

    /** Hold OK on a Continue Watching card, Mark as watched: the episode (or movie) is done, here and in the account's watched history. */
    private fun markWatched(e: WatchStore.Entry) {
        val episode = e.type == "series" && e.season >= 0
        if (episode) { Watched.set(e.metaId, e.videoId, true); WatchStore.remove(e.metaId) }
        else WatchStore.save(e.copy(positionMs = 1L, durationMs = 1L, updated = System.currentTimeMillis()))
        Progress.markWatched(e.metaId, e.type, e.season, e.episode, true)
        tv.myliststv.launcher.ui.Pop.toast(c, if (Prefs.accountLink.isNotEmpty()) "Marked watched, and added to My Lists" else "Marked watched")
    }

    private var continueSig = ""
    private var airingSig = ""

    /** What Continue Watching holds: the saved entries, minus the next-up episodes that Airing Next already shows (when that is turned on in Settings). */
    private fun continueEntries(): List<WatchStore.Entry> {
        if (!Prefs.showContinue || "Continue Watching" in Prefs.homeHidden) return emptyList()
        val cw = WatchStore.continueWatching()
        if (!(Prefs.showAiring && "Airing Next" !in Prefs.homeHidden && Prefs.hideUpcomingContinue)) return cw
        val upcoming = Progress.airing()
        return cw.filter { e -> !(e.durationMs == 0L && upcoming.any { it.id == e.metaId && it.season == e.season && it.episode == e.episode }) }
    }

    private fun continueSignature(l: List<WatchStore.Entry>) = l.joinToString(";") { "${it.metaId}|${it.videoId}|${it.positionMs}|${it.durationMs}" }
    private fun airingSignature(): String = if (!Prefs.showAiring || "Airing Next" in Prefs.homeHidden) "" else Progress.airing().joinToString(";") { "${it.id}|${it.season}|${it.episode}|${it.airDate}" }

    private fun continueRow(): RowModel? {
        val cw = continueEntries()
        continueSig = continueSignature(cw)
        if (cw.isEmpty()) return null
        val models = cw.map { e ->
            val sub = if (e.type == "series" && e.season >= 0) "S${e.season} · E${e.episode}" else "Movie"
            CardModel(e.name, e.background ?: e.poster, if (e.durationMs == 0L) "Next up · $sub" else "$sub · ${(e.fraction * 100).toInt()}% watched", e.fraction, e, badgeEnd = EpisodesLeft.timeLeft(e))
        }
        val row = RowModel("Continue Watching", "This device", CardKind.WIDE, models)
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
        return row
    }

    /** The next episode of each followed show, soonest first. Tapping one opens the show. */
    private fun airingRow(): RowModel? {
        airingSig = airingSignature()
        if (airingSig.isEmpty()) return null
        val a = Progress.airing()
        val day = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US); val parse = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return RowModel("Airing Next", "From your account", CardKind.WIDE, a.map { s ->
            val date = try { day.format(parse.parse(s.airDate)!!) } catch (_: Exception) { s.airDate }
            val tag = if (s.premiere) "Season premiere" else if (s.finale) "Season finale" else "S${s.season} \u00b7 E${s.episode}"
            CardModel(s.name, s.still ?: s.poster, "$tag \u00b7 $date", 0f, Meta(s.id, "series", s.name, s.poster, s.still))
        })
    }

    /** The account's watched list arrived: redraw the cards so their check marks show, without reloading any row. */
    fun refreshChecks() { rows.forEach { r -> r.adapter?.let { it.notifyItemRangeChanged(0, r.items.size) } } }

    /** Down from the tab bar lands on the first row's remembered card. False when the rows are not there yet (the default focus move then applies). */
    fun enter() = list.enter()

    /** Hide from Home: the app leaves the apps row where it is, and the focus moves to its neighbour. Nothing else reloads and the page keeps its place. */
    fun hideApp(pkg: String): Boolean {
        val r = rows.indexOfFirst { it.title == "Your Apps" }.takeIf { it >= 0 } ?: return false
        val row = rows[r]
        val i = row.items.indexOfFirst { (it.payload as? AppEntry)?.component?.packageName == pkg }.takeIf { it >= 0 } ?: return false
        row.items = row.items.toMutableList().also { it.removeAt(i) }
        row.adapter?.removeAt(i)
        val strip = (list.findViewHolderForAdapterPosition(r)?.itemView as? RowView)?.strip ?: return true
        if (row.items.isEmpty()) { list.changed(r); list.post { list.requestFocus() }; return true }
        row.lastPos = i.coerceAtMost(row.items.size - 1)
        strip.post { strip.findViewHolderForAdapterPosition(row.lastPos)?.itemView?.requestFocus() }
        return true
    }

    /**
     * Called when coming back to Home, and when the account's lists arrive. Only a row whose contents really changed is touched, and
     * only that row is redrawn: every other row (and the focus, and where you were in the page) stays exactly as it is.
     */
    fun refreshContinue() {
        if (!withContinue) return
        val cw = continueEntries()
        if (continueSignature(cw) != continueSig) swapRow("Continue Watching", continueRow())
        if (airingSignature() != airingSig) swapRow("Airing Next", airingRow())
    }

    private fun swapRow(title: String, row: RowModel?) {
        val at = rows.indexOfFirst { it.title == title }
        when {
            at >= 0 && row == null -> { rows.removeAt(at); list.removed(at) }
            at >= 0 && row != null -> { row.lastPos = rows[at].lastPos.coerceAtMost((row.items.size - 1).coerceAtLeast(0)); rows[at] = row; list.changed(at) }
            row != null -> { rows.add(row); rows.sortBy { Prefs.homeRank(it.title) }; list.inserted(rows.indexOf(row)) }
        }
    }

    /** Back on this page with nothing focused (the page behind a details page, say): the card you were last on. */
    fun restoreFocus() = list.restoreFocus()


    private var heroId: String? = null

    private fun hero(m: CardModel) {
        when (val p = m.payload) {
            is Meta -> {
                onBackdrop(p.background ?: p.poster)
                heroTitle.text = p.name
                heroMeta.text = listOfNotNull(p.year, p.rating?.let { "★ $it" }, p.genres.take(3).joinToString(", ").ifEmpty { null }).joinToString("   ")
                heroDesc.text = p.description ?: ""
                heroId = p.id
                // A tile with no story yet (Airing Next) asks for the title's details.
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
            is AppEntry -> { onBackdrop(""); heroTitle.text = p.label; heroMeta.text = "Installed app"; heroDesc.text = "Press OK to open." }
        }
    }
}
