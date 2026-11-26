package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import tv.myliststv.launcher.App

/** Settings that belong to the video player only, kept apart from [Prefs] so the player's choices have one home. */
/**
 * Manual zoom: how wide and how tall the picture is drawn (percent of the normal fit) and how far it is moved sideways and up or down
 * (percent of the screen). The normal picture is 100, 100, 0, 0.
 */
data class Zoom(val w: Int = 100, val h: Int = 100, val x: Int = 0, val y: Int = 0) {
    val isDefault get() = this == Zoom()
    fun clamped() = Zoom(w.coerceIn(MIN, MAX), h.coerceIn(MIN, MAX), x.coerceIn(-POS, POS), y.coerceIn(-POS, POS))
    fun encode() = "$w,$h,$x,$y"

    companion object {
        const val MIN = 50; const val MAX = 250; const val POS = 50
        fun decode(s: String?): Zoom = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 4 }?.let { Zoom(it[0], it[1], it[2], it[3]).clamped() } ?: Zoom()

        /** The same factor on both sides that makes a picture of aspect [video] cover a screen of aspect [screen] (a 2.39:1 film on a 16:9 screen, or the reverse). */
        fun fill(video: Float, screen: Float): Zoom {
            if (video <= 0f || screen <= 0f) return Zoom()
            val f = maxOf(video / screen, screen / video)
            return Zoom((f * 100).toInt(), (f * 100).toInt()).clamped()
        }
    }
}

object PlayerPrefs {
    /** Each title keeps its own zoom: a scope film needs it, the next show does not. */
    fun zoom(metaId: String?): Zoom = if (metaId == null) Zoom() else Zoom.decode(sp().getString("zoom_$metaId", null))
    fun setZoom(metaId: String?, z: Zoom) {
        if (metaId == null) return
        if (z.isDefault) sp().edit().remove("zoom_$metaId").apply() else sp().edit().putString("zoom_$metaId", z.clamped().encode()).apply()
    }

    private fun sp(): SharedPreferences = App.instance.getSharedPreferences("player", Context.MODE_PRIVATE)

    /** Send Dolby and DTS to the TV or receiver as they are (on), or always decode here and send plain sound (off). */
    var passthrough: Boolean
        get() = sp().getBoolean("passthrough", true)
        set(v) = sp().edit().putBoolean("passthrough", v).apply()

    /** "Auto": the software decoder is used only for sound the TV's own chips cannot play. "First": it goes first (a box with a faulty decoder). */
    var softwareAudio: String
        get() = sp().getString("softAudio", "Auto") ?: "Auto"
        set(v) = sp().edit().putString("softAudio", v).apply()

    /** Subtitle size in percent of the normal size. */
    var subSize: Int
        get() = sp().getInt("subSize", 100)
        set(v) = sp().edit().putInt("subSize", v).apply()

    /** "Shadow" (text with an outline only), "Dim" (a see-through box) or "Solid". */
    var subBackground: String
        get() = sp().getString("subBg", "Shadow") ?: "Shadow"
        set(v) = sp().edit().putString("subBg", v).apply()

    /** How far to move a title's addon subtitles, in ms (positive = later). Remembered for each title. */
    fun subDelay(metaId: String?): Long = if (metaId == null) 0L else sp().getLong("subDelay_$metaId", 0L)
    fun setSubDelay(metaId: String?, ms: Long) { if (metaId != null) sp().edit().putLong("subDelay_$metaId", ms).apply() }

    /** 0 = Auto, otherwise seconds of video to keep ahead. */
    var bufferSec: Int
        get() = sp().getInt("bufferSec", 0)
        set(v) = sp().edit().putInt("bufferSec", v).apply()

    /** "Off", "Seamless" (only when the TV can switch without a blank screen) or "Always" (switch the display mode to the film's rate). */
    var frameRate: String
        get() = sp().getString("frameRate", "Seamless") ?: "Seamless"
        set(v) = sp().edit().putString("frameRate", v).apply()

    /** "Auto" trusts what the TV reports; "HDR" and "SDR" are for a TV that reports wrongly. */
    var displayHdr: String
        get() = sp().getString("displayHdr", "Auto") ?: "Auto"
        set(v) = sp().edit().putString("displayHdr", v).apply()

    /** Offer to resume or start over when a title was left part way. */
    var askResume: Boolean
        get() = sp().getBoolean("askResume", true)
        set(v) = sp().edit().putBoolean("askResume", v).apply()

    /** The live numbers (codec, bitrate, dropped frames) over the picture. */
    var stats: Boolean
        get() = sp().getBoolean("stats", false)
        set(v) = sp().edit().putBoolean("stats", v).apply()

    /** The first-choice rank: sources this TV is least likely to play drop down the list. */
    var rankByDevice: Boolean
        get() = sp().getBoolean("rankDevice", true)
        set(v) = sp().edit().putBoolean("rankDevice", v).apply()
}
