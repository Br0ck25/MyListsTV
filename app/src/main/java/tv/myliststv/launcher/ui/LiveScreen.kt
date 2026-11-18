package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import tv.myliststv.launcher.PlayerActivity
import tv.myliststv.launcher.data.*

/** Live TV from IPTV playlists the user adds (M3U links). One row per group; OK tunes in. */
class LiveScreen(private val act: Activity) {
    private val rows = ArrayList<RowModel>()
    private val note = Ui.text(act, "", 14f, Ui.DIM, false, 3)
    private var all: List<Channel> = emptyList()
    private val list = RowsList(act, rows, Prefs.lite(), { }, { m -> (m.payload as? Channel)?.let(::play) })
    val view: View = Ui.vbox(act).also {
        note.setPadding(Ui.dp(act, 48), Ui.dp(act, 12), Ui.dp(act, 48), Ui.dp(act, 6))
        it.addView(note, Ui.matchWrap()); it.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun start() {
        note.text = if (LiveStore.playlists().isEmpty()) "No IPTV playlist yet. In the Addons tab choose “Add an addon or playlist” and paste an M3U link." else "Loading channels…"
        LiveStore.load { channels ->
            all = channels
            rows.clear()
            channels.groupBy { it.group }.entries.take(40).forEach { (g, chs) ->
                rows += RowModel(g, "${chs.size} channels", CardKind.WIDE, chs.take(80).map { CardModel(it.name, it.logo, payload = it) })
            }
            if (channels.isNotEmpty()) note.text = "${channels.size} channels. Up and down change channel while watching."
            list.changedAll()
        }
    }

    private fun play(c: Channel) {
        PlaySession.live = all
        PlaySession.liveIndex = all.indexOf(c).coerceAtLeast(0)
        PlaySession.meta = Meta("live", "tv", c.name)
        PlaySession.current = StreamItem("IPTV", "iptv", c.url, c.name, c.group, null, null, 0, 0, false, false, null)
        PlaySession.autoSelected = false; PlaySession.resumeMs = 0; PlaySession.ranked = emptyList(); PlaySession.videoId = "live"; PlaySession.season = -1
        act.startActivity(Intent(act, PlayerActivity::class.java))
    }
}
