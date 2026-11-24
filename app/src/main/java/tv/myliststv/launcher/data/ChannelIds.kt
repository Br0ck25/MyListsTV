package tv.myliststv.launcher.data

import org.json.JSONObject

/** The id a My Lists channel answers to for its details, which can differ from the id on its tile in a catalog. */
object ChannelIds {
    private const val ORIGIN = "https://mylistsaddon.com"

    /** The account's sync list: its channels, each with the id the addon serves details for. */
    fun syncUrl(accountLink: String) = "$ORIGIN/api/nuvio-tv/sync/" + Pairing.targetOf(accountLink)

    /** The details id of the channel called [name] in a sync answer, or null when it is not there. */
    fun byName(sync: String, name: String): String? = try {
        val a = JSONObject(sync).optJSONArray("channels")
        (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.firstOrNull { it.optString("name").equals(name, true) }
            ?.optString("catalogId")?.takeIf { it.startsWith("channel_") }
    } catch (_: Exception) { null }
}
