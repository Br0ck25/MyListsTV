package tv.myliststv.launcher.ui

import tv.myliststv.launcher.data.Meta
import tv.myliststv.launcher.data.StreamItem
import tv.myliststv.launcher.data.StreamResolver

/** What the player needs, handed over in memory because a stream list is too big and too short-lived for an intent. */
object PlaySession {
    var meta: Meta? = null
    var videoId: String = ""
    var season: Int = -1
    var episode: Int = -1
    var episodeTitle: String? = null
    /** Best first. */
    var ranked: List<StreamItem> = emptyList()
    var current: StreamItem? = null
    /** True when the app chose the stream itself, so automatic fallback may replace it. */
    var autoSelected: Boolean = true
    var resumeMs: Long = 0
    /** True while the player itself is finding the first source (the details page starts the player at once, so there is no pop-up in between). */
    var resolving: Boolean = false
    /** Set by the player when it found nothing to play, so the details page can say why when it comes back. */
    var noStreams: List<StreamResolver.AddonStatus>? = null
}
