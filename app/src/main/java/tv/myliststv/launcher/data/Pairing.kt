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

    /** The account name in the poll answer, or empty. */
    fun parseUser(text: String): String = try { JSONObject(text).optString("username") } catch (_: Exception) { "" }

    /** The page to open on the phone, and the same as a full link (for the QR code). */
    fun linkUrl(s: Start) = "mylistsaddon.com/link?code=${s.userCode}"
    fun linkFull(s: Start) = "https://" + linkUrl(s)

    @Volatile private var cancelled = false

    /** [cb] gets the code, or null and a short reason (no network, or My Lists answering with something else, such as a redirect). */
    fun start(cb: (Start?, String?) -> Unit) = Http.background {
        var why: String? = null
        val s = try {
            val (status, body) = Http.send("POST", "$ORIGIN/api/nuvio-tv/device/code", "")
            parseStart(body).also { if (it == null) why = reason(status) }
        } catch (_: Exception) { why = "Could not reach My Lists. Check your network."; null }
        cancelled = false
        Http.post { cb(s, why) }
    }

    fun reason(status: Int) = if (status in 300..399) "mylistsaddon.com answered with a redirect (HTTP $status) instead of the sign-in service. The site's address may be set up wrongly; check its Cloudflare rules."
        else "My Lists answered with an error (HTTP $status). Try again in a moment."

    fun cancel() { cancelled = true }

    /** Checks every 3 seconds for up to 10 minutes. [done] gets the addon link and the account name, or a null link if the code expired or was cancelled. */
    fun poll(s: Start, done: (String?, String) -> Unit) = Http.background {
        val end = System.currentTimeMillis() + 10 * 60_000
        var link: String? = null; var user = ""
        while (!cancelled && link == null && System.currentTimeMillis() < end) {
            try {
                val (status, body) = Http.send("GET", "$ORIGIN/api/nuvio-tv/device/poll?deviceCode=${s.deviceCode}", null)
                if (status == 404) break
                link = parsePoll(body); if (link != null) user = parseUser(body)
            } catch (_: Exception) {}
            if (link == null) Thread.sleep(3000)
        }
        val result = link
        Http.post { if (!cancelled || result != null) done(result, user) }
    }

    private fun parts(manifestUrl: String) = manifestUrl.removePrefix("https://").removePrefix("http://").split('/')

    /** Who the link belongs to, for display: the user in /user/tv/manifest.json. A paired TV's link is /i/<secret>/tv/..., which names nobody and must never be shown. */
    fun userOf(manifestUrl: String): String = parts(manifestUrl).getOrNull(1)?.takeIf { it != "i" } ?: "your account"

    /** What My Lists wants as the TV's id: the secret in /i/<secret>/tv/... (a username is refused for history and progress). */
    fun targetOf(manifestUrl: String): String = parts(manifestUrl).let { p -> if (p.getOrNull(1) == "i") p.getOrNull(2) ?: "" else p.getOrNull(1) ?: "" }

    fun signIn(manifestUrl: String, name: String = "") {
        AddonStore.remove(AddonStore.DEFAULT_URL)
        AddonStore.add(manifestUrl)
        Prefs.accountLink = manifestUrl; Prefs.accountName = name
        Library.pull { }
        SetupSync.pull { AddonStore.needsRefresh = true }
        AddonStore.needsRefresh = true
        Backup.auto()
    }

    /** Back to the default My Lists catalogs. Other addons stay. */
    fun signOut() {
        AddonStore.remove(Prefs.accountLink)
        AddonStore.add(AddonStore.DEFAULT_URL)
        Prefs.accountLink = ""; Prefs.accountName = ""
        AddonStore.needsRefresh = true
        Backup.auto()
    }
}
