package tv.myliststv.launcher.ui

import android.app.Activity
import android.widget.Toast
import tv.myliststv.launcher.data.*

/** Hold OK on a poster or tile: Go to details, add to or remove from the Library, mark as watched or not. The same everywhere a title appears. */
object TitleMenu {
    fun show(act: Activity, meta: Meta, open: () -> Unit, changed: () -> Unit) {
        val s = Sheet(act, meta.name, listOfNotNull(if (meta.type == "series") "Series" else "Movie", meta.year).joinToString("  \u2022  "))
        val first = s.card("Go to details", "Open its page") { s.dismiss(); open() }
        val saved = Library.has(meta.id)
        s.card(if (saved) "Remove from Library" else "Add to Library", if (saved) "Take it off your Library" else "Keep it in your Library") {
            s.dismiss(); val now = Library.toggle(meta)
            tv.myliststv.launcher.ui.Pop.toast(act, if (now) "Added to Library" else "Removed from Library"); changed()
        }
        val done = Watched.titleDone(meta)
        s.card(if (done) "Mark as unwatched" else "Mark as watched", if (done) "Take off the check mark" else "Adds a check mark, and to My Lists when you are signed in") {
            s.dismiss(); setWatched(act, meta, !done); changed()
        }
        s.show(); first.requestFocus()
    }

    /** Marks a whole title watched (or not), here and in the account's watched history. */
    fun setWatched(act: Activity, meta: Meta, watched: Boolean) {
        Watched.setTitle(meta, watched)
        if (watched) WatchStore.remove(meta.id)
        val movie = meta.type == "movie"
        if (movie) Progress.markWatched(meta.id, meta.type, -1, -1, watched) else Progress.markWholeShow(meta.id, watched)
        tv.myliststv.launcher.ui.Pop.toast(act, if (!watched) "Marked not watched" else if (Prefs.accountLink.isNotEmpty()) "Marked watched, and added to My Lists" else "Marked watched")
    }
}
