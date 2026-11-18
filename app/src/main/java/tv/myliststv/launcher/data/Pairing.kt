package tv.myliststv.launcher.data

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/**
 * Sign in with a code. Uses the pairing flow My Lists already runs for TV launchers: the TV asks for a code, the owner
 * opens the link page on a phone and signs in there, and the TV's poll then returns the owner's personal addon link.
 * Signing in swaps the default My Lists addon for that link, so Home shows the owner's own lists.
 */
object Pairing {
    private const val ORIGIN = "https://mylistsaddon.com"
    data class Start(val deviceCode: String, val userCode: String)

    fun parseStart(text: String): Start? = try {
        JSONObject(text).takeIf { it.optBoolean("ok") }?.let { Start(it.getString("deviceCode"), it.getString("userCode")) }
    } catch (_: Exception) { null }

    /** The personal addon link once the phone has approved, else null (still waiting). */
    fun parsePoll(text: String): String? = try {
        JSONObject(text).takeIf { it.optString("status") == "authenticated" }?.optString("manifestUrl")?.ifEmpty { null }
    } catch (_: Exception) { null }

    /** The page to open on the phone. */
    fun linkUrl(s: Start) = "mylistsaddon.com/link?code=${s.userCode}"

    @Volatile private var cancelled = false

    fun start(cb: (Start?) -> Unit) = Http.background {
        val s = try { parseStart(Http.send("POST", "$ORIGIN/api/nuvio-tv/device/code", "").second) } catch (_: Exception) { null }
        cancelled = false
        Http.post { cb(s) }
    }

    fun cancel() { cancelled = true }

    /** Checks every 3 seconds for up to 10 minutes. [done] gets the addon link, or null if the code expired or was cancelled. */
    fun poll(s: Start, done: (String?) -> Unit) = Http.background {
        val end = System.currentTimeMillis() + 10 * 60_000
        var link: String? = null
        while (!cancelled && link == null && System.currentTimeMillis() < end) {
            try {
                val (status, body) = Http.send("GET", "$ORIGIN/api/nuvio-tv/device/poll?deviceCode=${s.deviceCode}", null)
                if (status == 404) break
                link = parsePoll(body)
            } catch (_: Exception) {}
            if (link == null) Thread.sleep(3000)
        }
        val result = link
        Http.post { if (!cancelled || result != null) done(result) }
    }

    private fun parts(manifestUrl: String) = manifestUrl.removePrefix("https://").removePrefix("http://").split('/')

    /** Who the link belongs to, for display: the user in /user/tv/manifest.json. A paired TV's link is /i/<secret>/tv/..., which names nobody and must never be shown. */
    fun userOf(manifestUrl: String): String = parts(manifestUrl).getOrNull(1)?.takeIf { it != "i" } ?: "your account"

    /** What My Lists wants as the TV's id: the secret in /i/<secret>/tv/... (a username is refused for history and progress). */
    fun targetOf(manifestUrl: String): String = parts(manifestUrl).let { p -> if (p.getOrNull(1) == "i") p.getOrNull(2) ?: "" else p.getOrNull(1) ?: "" }

    fun signIn(manifestUrl: String) {
        AddonStore.remove(AddonStore.DEFAULT_URL)
        AddonStore.add(manifestUrl)
        Prefs.accountLink = manifestUrl
        AddonStore.needsRefresh = true
        Backup.auto()
    }

    /** Back to the default My Lists catalogs. Other addons stay. */
    fun signOut() {
        AddonStore.remove(Prefs.accountLink)
        AddonStore.add(AddonStore.DEFAULT_URL)
        Prefs.accountLink = ""
        AddonStore.needsRefresh = true
        Backup.auto()
    }
}
