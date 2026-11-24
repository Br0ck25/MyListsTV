package tv.myliststv.launcher.data

import tv.myliststv.launcher.screens.*
import tv.myliststv.launcher.services.*

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import tv.myliststv.launcher.screens.DetailsActivity

/** Puts Continue Watching on the system home screen's Watch Next row. Silently does nothing on devices without it. */
object WatchNext {
    fun publish(c: Context) = Http.background {
        try {
            if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return@background
            val cr = c.contentResolver
            cr.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)   // only removes this app's entries
            for (e in WatchStore.continueWatching().take(5)) {
                val open = Intent(c, DetailsActivity::class.java).putExtra("meta", Json.metaToJson(Meta(e.metaId, e.type, e.name, e.poster, e.background))).putExtra("play", e.videoId)
                val b = WatchNextProgram.Builder()
                    .setType(if (e.type == "series") TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(e.updated).setTitle(e.name).setInternalProviderId(e.metaId)
                    .setLastPlaybackPositionMillis(e.positionMs.toInt()).setDurationMillis(e.durationMs.toInt())
                    .setIntentUri(Uri.parse(open.toUri(Intent.URI_INTENT_SCHEME)))
                (e.poster ?: e.background)?.let { b.setPosterArtUri(Uri.parse(it)).setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3) }
                if (e.type == "series" && e.season >= 0) b.setSeasonNumber(e.season).setEpisodeNumber(e.episode)
                cr.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
            }
        } catch (_: Throwable) {}
    }
}
