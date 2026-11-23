package tv.myliststv.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import tv.myliststv.launcher.App

/**
 * Keeps a signed-in profile's stream addons on its account, so a new TV only needs the sign-in. The account's
 * own addon link (it holds this TV's pairing secret) and the default addon are not sent: each TV has its own. The server stores
 * the list encrypted at rest. Changes are pushed at once; the account is read when the launcher opens and after signing in.
 */
object SetupSync {
    private const val ORIGIN = "https://mylistsaddon.com"
    /** True while a pulled list is being applied, so applying it does not send it straight back. */
    @Volatile var applying = false

    private fun sp() = App.instance.getSharedPreferences("setupsync", android.content.Context.MODE_PRIVATE)
    private fun on() = Prefs.accountLink.isNotEmpty()
    private fun post(o: JSONObject): Pair<Int, String> = Http.send("POST", "$ORIGIN/api/nuvio-tv/setup", o.put("targetId", Pairing.targetOf(Prefs.accountLink)).toString())
    private fun isShared(url: String) = url != AddonStore.DEFAULT_URL && url != Prefs.accountLink

    /** Called when the addon list changes by the viewer's own doing. */
    fun changed() { if (!applying) push() }

    /** What is sent: the profile's own addons (not the account link or the default). */
    internal fun payload(): JSONObject = JSONObject().put("action", "set")
        .put("addons", JSONArray().also { a -> AddonStore.ownSaved().filter { isShared(it.url) }.forEach { a.put(JSONObject().put("url", it.url).put("on", it.enabled)) } })

    fun push() {
        if (!on()) return
        val dirty = Profiles.key("dirty"); val at = Profiles.key("syncedAt")
        val body = payload()
        sp().edit().putBoolean(dirty, true).apply()
        Http.background {
            try {
                val (code, text) = post(body)
                if (code in 200..299) sp().edit().putLong(at, JSONObject(text).optLong("updatedAt")).putBoolean(dirty, false).apply()
            } catch (_: Exception) {}
        }
    }

    /** Brings the account's lists in. The first time the two are joined; after that the newer side wins. [changed] runs on the main thread if the local lists changed. */
    fun pull(changed: () -> Unit) {
        if (!on()) return
        if (sp().getBoolean(Profiles.key("dirty"), false)) { push(); return }
        Http.background {
            try {
                val (code, text) = post(JSONObject().put("action", "get"))
                if (code !in 200..299) return@background
                val o = JSONObject(text); o.optString("username").takeIf { it.isNotEmpty() }?.let { Prefs.accountName = it }; val at = o.optLong("updatedAt")
                val theirs = o.optJSONArray("addons") ?: JSONArray()
                val remoteAddons = (0 until theirs.length()).mapNotNull { theirs.optJSONObject(it) }.map { AddonStore.Saved(it.optString("url"), it.optBoolean("on", true)) }.filter { it.url.isNotEmpty() }
                val first = sp().getLong(Profiles.key("syncedAt"), 0L) == 0L
                if (!first && at <= sp().getLong(Profiles.key("syncedAt"), 0L)) return@background
                val mineAddons = AddonStore.ownSaved()
                val base = mineAddons.filter { !isShared(it.url) }
                val addons = base + if (first) remoteAddons + mineAddons.filter { m -> isShared(m.url) && remoteAddons.none { it.url == m.url } } else remoteAddons
                applying = true
                try { AddonStore.restore(addons) } finally { applying = false }
                if (first && (addons.size - base.size != remoteAddons.size || at == 0L)) push() else sp().edit().putLong(Profiles.key("syncedAt"), at).apply()
                Http.post(changed)
            } catch (_: Exception) {}
        }
    }
}
