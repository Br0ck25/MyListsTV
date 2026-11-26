package tv.myliststv.launcher.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.provider.Settings
import tv.myliststv.launcher.ui.Pop

/** The TV's own settings pages (the Android settings group of Settings) and its video inputs (HDMI and the like). */
object SystemLinks {
    val pages = listOf(
        "All settings" to Settings.ACTION_SETTINGS, "Wi-Fi and network" to Settings.ACTION_WIFI_SETTINGS, "Display" to Settings.ACTION_DISPLAY_SETTINGS,
        "Sound" to Settings.ACTION_SOUND_SETTINGS, "Bluetooth and remotes" to Settings.ACTION_BLUETOOTH_SETTINGS, "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "Date and time" to Settings.ACTION_DATE_SETTINGS, "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Screensaver" to Settings.ACTION_DREAM_SETTINGS,
    )

    /** Opens one of [pages]; a TV that lacks that page gets its main settings, and one with none says so. */
    fun open(act: Activity, action: String) {
        try { act.startActivity(Intent(action)) } catch (_: Exception) {
            try { act.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { Pop.toast(act, "This TV does not open its settings from apps.", true) }
        }
    }

    class Input(val id: String, val label: String)

    /** The inputs a cable box, console or stick can be plugged into, named as the TV names them (Sony's "HDMI 1 (PlayStation)"). */
    fun inputs(c: Context): List<Input> {
        val m = c.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try { m.tvInputList.filter { it.isPassthroughInput }.map { Input(it.id, (it.loadCustomLabel(c) ?: it.loadLabel(c)).toString()) } } catch (_: Exception) { emptyList() }
    }

    fun openInput(act: Activity, id: String) {
        try { act.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))) } catch (_: Exception) { Pop.toast(act, "This TV would not switch to that input.", true) }
    }
}
