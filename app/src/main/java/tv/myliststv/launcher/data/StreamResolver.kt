package tv.myliststv.launcher.data

/** Asks every enabled stream addon for sources and merges the answers as they arrive. */
object StreamResolver {

    data class AddonStatus(val name: String, val ok: Boolean, val count: Int, val millis: Long)

    class Session(
        val onUpdate: (List<StreamItem>, List<AddonStatus>) -> Unit,
        val onDone: (List<StreamItem>, List<AddonStatus>) -> Unit,
    ) { @Volatile var cancelled = false }

    fun providers(type: String, videoId: String): List<Addon> = AddonStore.loaded.filter { a ->
        a.enabled && a.manifest.resources["stream"]?.let { r ->
            (r.types.isEmpty() || type in r.types) && (r.idPrefixes.isEmpty() || r.idPrefixes.any { videoId.startsWith(it) })
        } == true
    }

    fun resolve(type: String, videoId: String, timeoutMs: Int, session: Session) {
        val addons = providers(type, videoId)
        if (addons.isEmpty()) { Http.post { session.onDone(emptyList(), emptyList()) }; return }
        val all = ArrayList<StreamItem>()
        val statuses = ArrayList<AddonStatus>()
        var pending = addons.size
        for (a in addons) Http.background {
            val t0 = System.currentTimeMillis()
            var list: List<StreamItem> = emptyList()
            var ok = false
            try {
                val body = Http.fetchBlocking(Urls.stream(a.baseUrl, type, videoId), timeoutMs, timeoutMs)
                list = Json.parseStreams(body, a.name, a.manifestUrl); ok = true
            } catch (_: Exception) {}
            val ms = System.currentTimeMillis() - t0
            val (snapAll, snapSt, last) = synchronized(all) {
                all += list; statuses += AddonStatus(a.name, ok, list.size, ms); pending--
                Triple(ArrayList(all), ArrayList(statuses), pending == 0)
            }
            Http.post {
                if (!session.cancelled) {
                    session.onUpdate(snapAll, snapSt)
                    if (last) session.onDone(snapAll, snapSt)
                }
            }
        }
    }
}
