package tv.myliststv.launcher.ui

import tv.myliststv.launcher.data.Meta
import tv.myliststv.launcher.data.StreamItem

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
    /** Set when watching live TV: the channel list and where we are in it. Null for movies and series. */
    var live: List<tv.myliststv.launcher.data.Channel>? = null
    var liveIndex: Int = 0
}
