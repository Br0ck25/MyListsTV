package tv.myliststv.launcher.data

import android.content.Context
import android.content.SharedPreferences
import tv.myliststv.launcher.App

/** Settings that belong to the video player only, kept apart from [Prefs] so the player's choices have one home. */
object PlayerPrefs {
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
