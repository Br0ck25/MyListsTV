package tv.myliststv.launcher.data

import java.util.TimeZone
import kotlin.math.ceil

/** What a Continue Watching card says about where you are: episodes still to watch, time left, or when the next one airs. */
object EpisodesLeft {
    /** [left]: aired episodes still to watch, counting the one you are on. [airs]: "Airs in 3 days" when the next one is not out yet. */
    data class Info(val left: Int, val airs: String?)

    /** Null when the episode list does not contain the episode you are on (nothing honest to say). */
    fun compute(videos: List<Video>, current: WatchStore.Entry, now: Long, tz: TimeZone = TimeZone.getDefault()): Info? {
        val eps = videos.filter { it.season > 0 || current.season == 0 }.sortedWith(compareBy({ it.season }, { it.episode }))
        var i = eps.indexOfFirst { it.id == current.videoId }
        if (i < 0) i = eps.indexOfFirst { it.season == current.season && it.episode == current.episode }
        if (i < 0) return null
        val rest = eps.subList(i, eps.size)
        val aired = rest.count { (it.airDate()?.time ?: 0L) <= now }
        if (aired > 0) return Info(aired, null)
        val next = rest.first().airDate() ?: return Info(0, null)
        return Info(0, airsText(next.time, now, tz))
    }

    /** An air date is a calendar date (the addon sends no time of day), so count calendar days from today rather than hours. */
    fun airsText(airMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val day = 86_400_000L
        val days = (Math.floorDiv(airMs, day) - Math.floorDiv(nowMs + tz.getOffset(nowMs), day)).toInt()
        return when { days <= 0 -> "Airs Today"; days == 1 -> "Airs Tomorrow"; else -> "Airs in $days Days" }
    }

    fun leftText(n: Int) = "$n EP left"

    /** "45 min left" or "1h 5m left" for something you have started, null when its length is not known. */
    fun timeLeft(e: WatchStore.Entry): String? {
        if (e.durationMs <= 0) return null
        val min = ceil((e.durationMs - e.positionMs).coerceAtLeast(0) / 60_000.0).toInt()
        return if (min >= 60) "${min / 60}h ${min % 60}m left" else "$min min left"
    }
}
