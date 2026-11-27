package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import tv.myliststv.launcher.R
import tv.myliststv.launcher.data.*
import tv.myliststv.launcher.screens.PlayerActivity

/**
 * "Files": videos on this box, on a USB drive, or on a NAS or computer on the network, and Play on any of them. Folders open with OK,
 * Back goes up one, and hold OK on a network share to change or remove it. What you watch here shows in Continue Watching.
 */
class FilesScreen(private val act: Activity) {
    private enum class Kind { ROOT, LOCAL, SMB }

    /** One level of folders you have opened. [focus] is the row to come back to when you go up. */
    private class Page(val title: String, val kind: Kind, val path: String, val share: SmbShare? = null, var focus: String? = null)

    private val stack = ArrayList<Page>()
    private val heading = Ui.text(act, "Files", 24f, Ui.TEXT, true, 1)
    private val where = Ui.text(act, "", 14f, Ui.DIM, false, 1)
    private val body = Ui.vbox(act).apply { setPadding(Ui.dp(act, 42), Ui.dp(act, 6), Ui.dp(act, 42), Ui.dp(act, 56)) }
    private var videos: List<LocalFiles.Video> = emptyList()
    private var generation = 0

    val view: View = Ui.vbox(act).also {
        val head = Ui.vbox(act).apply { setPadding(Ui.dp(act, 48), Ui.dp(act, 10), Ui.dp(act, 48), Ui.dp(act, 6)) }
        head.addView(heading, Ui.matchWrap()); head.addView(where, Ui.matchWrap())
        it.addView(head, Ui.matchWrap())
        it.addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun start() { stack.clear(); stack += Page("Files", Kind.ROOT, ""); render() }

    /** Back goes up a folder. False when already at the top, so the launcher's own Back applies. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        val gone = stack.removeAt(stack.size - 1)
        stack.last().focus = gone.title
        render()
        return true
    }

    /** The answer to the permission question the page asked. */
    fun onPermission() { if (stack.lastOrNull()?.kind == Kind.ROOT) render() }

    // ---------------------------------------------------------------- pages

    private fun render() {
        val page = stack.last()
        val gen = ++generation
        heading.text = if (page.kind == Kind.ROOT) "Files" else page.title
        where.text = if (page.kind == Kind.ROOT) "Videos on this box, on USB drives and on your network" else crumb(page)
        body.removeAllViews()
        when (page.kind) {
            Kind.ROOT -> root(page, gen)
            Kind.LOCAL -> { add(info("Loading…")); background(gen) { LocalFiles.children(videos.ifEmpty { LocalFiles.scan(act).also { v -> videos = v } }, page.path) } }
            Kind.SMB -> { add(info("Loading…")); background(gen, onError = { e -> showError(page, e) }) { SmbClients.list(page.share!!, page.path) } }
        }
    }

    private fun crumb(p: Page) = when (p.kind) {
        Kind.SMB -> p.share!!.name + if (p.path.isEmpty()) "" else "  ›  " + p.path.replace("/", "  ›  ")
        else -> p.path.removePrefix("/storage/").replace("/", "  ›  ")
    }

    /** Runs [work] off the main thread and lists what it returns, unless the page has moved on. */
    private fun background(gen: Int, onError: ((Throwable) -> Unit)? = null, work: () -> List<FileItem>) {
        Http.background {
            val result = try { Result.success(work()) } catch (e: Throwable) { Result.failure(e) }
            Http.post {
                if (gen != generation) return@post
                result.onSuccess { list(it) }.onFailure { onError?.invoke(it) ?: showError(stack.last(), it) }
            }
        }
    }

    private fun root(page: Page, gen: Int) {
        add(Ui.sectionLabel(act, "On this box and drives"), 16)
        if (!LocalFiles.allowed(act)) {
            add(row(R.drawable.ic_info, "Allow access to videos", "So the videos on this box and on a USB drive can be listed.", null, { act.requestPermissions(arrayOf(LocalFiles.permission), REQ) }))
        } else {
            add(info("Looking for videos…"))
            val slot = body.childCount - 1
            Http.background {
                val found = try { LocalFiles.scan(act) } catch (_: Throwable) { emptyList() }
                val labels = LocalFiles.labels(act)
                Http.post {
                    if (gen != generation) return@post
                    videos = found
                    body.removeViewAt(slot)
                    val roots = LocalFiles.roots(found, labels)
                    if (roots.isEmpty()) body.addView(info("No videos found. Plug in a USB drive and give the box a minute to scan it."), slot, Ui.matchWrap())
                    roots.forEachIndexed { i, r ->
                        body.addView(row(R.drawable.ic_folder, r.name, "${r.count} video${if (r.count == 1) "" else "s"}", null, { open(Page(r.name, Kind.LOCAL, r.path)) }), slot + i, rowParams())
                    }
                    if (body.findFocus() == null) focusFirst(page)
                }
            }
        }
        add(Ui.sectionLabel(act, "Network shares"), 16)
        SmbShares.all().forEach { s ->
            add(row(R.drawable.ic_source, s.name, s.host + if (s.port != 445) ":${s.port}" else "", null, { open(Page(s.name, Kind.SMB, "", s)) }, { shareMenu(s) }))
        }
        add(row(R.drawable.ic_add, "Add a network share", "A NAS or another computer. You need its address and the folder it shares.", null, { editShare(null) }))
        focusFirst(page)
    }

    private fun list(items: List<FileItem>) {
        val page = stack.last()
        body.removeAllViews()
        if (items.isEmpty()) { add(info("No videos in this folder.")); return }
        items.forEach { f ->
            if (f.isDir) {
                val sub = if (f.count > 0) "${f.count} video${if (f.count == 1) "" else "s"}" else null
                add(row(R.drawable.ic_folder, f.name, sub, null, { open(Page(f.name, page.kind, f.path, page.share)) }, tag = f.name))
            } else {
                val seen = WatchStore.forMeta(VideoFiles.playId(f.uri))
                val sub = listOfNotNull(VideoFiles.sizeText(f.size).ifEmpty { null }, seen?.takeIf { it.fraction in 0.02f..0.93f }?.let { "Resume at ${(it.fraction * 100).toInt()}%" }).joinToString("  ·  ")
                add(row(R.drawable.ic_play, VideoFiles.title(f.name), sub.ifEmpty { null }, null, { FilesPlay.play(act, f) }, tag = f.name))
            }
        }
        focusFirst(page)
    }

    private fun open(p: Page) { stack.last().focus = p.title; stack += p; render() }

    private fun showError(page: Page, e: Throwable) {
        body.removeAllViews()
        add(info(friendly(e)))
        add(row(R.drawable.ic_refresh, "Try again", null, null, { render() }))
        if (page.kind == Kind.SMB && page.path.isEmpty()) add(row(R.drawable.ic_wrench, "Change this share", null, null, { editShare(page.share) }))
        focusFirst(page)
    }

    // ---------------------------------------------------------------- rows

    private fun rowParams() = Ui.matchWrap().apply { topMargin = Ui.dp(act, 2) }

    private fun add(v: View, topDp: Int = 0) { body.addView(v, rowParams().apply { if (topDp > 0) topMargin = Ui.dp(act, topDp) }) }

    private fun info(text: String) = Ui.text(act, text, 14f, Ui.DIM, false).apply { setPadding(Ui.dp(act, 14), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10)); setLineSpacing(0f, 1.1f) }

    private fun row(icon: Int, title: String, sub: String?, trailing: String?, onClick: () -> Unit, onLong: (() -> Unit)? = null, tag: String? = null): View {
        val c = act
        val r = Ui.hbox(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10)); background = Ui.menuRowBg(c); isFocusable = true; isClickable = true }
        r.addView(Ui.iconTile(c, icon, 32), LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)).apply { rightMargin = Ui.dp(c, 14) })
        val mid = Ui.vbox(c)
        mid.addView(Ui.text(c, title, 16f, Ui.TEXT, true, 2))
        if (!sub.isNullOrEmpty()) mid.addView(Ui.text(c, sub, 12f, Ui.DIM, false, 2), Ui.matchWrap().apply { topMargin = Ui.dp(c, 2) })
        r.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) r.addView(Ui.text(c, trailing, 14f, Ui.TEXT2), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 16) })
        r.setOnClickListener { onClick() }
        if (onLong != null) r.setOnLongClickListener { onLong(); true }
        r.tag = tag ?: title
        return r
    }

    /** Focus goes to the row you came back to, else the first row you can act on. */
    private fun focusFirst(page: Page) {
        body.post {
            val rows = (0 until body.childCount).map { body.getChildAt(it) }.filter { it.isFocusable }
            (rows.firstOrNull { it.tag == page.focus } ?: rows.firstOrNull())?.requestFocus()
        }
    }

    // ---------------------------------------------------------------- shares

    private fun shareMenu(s: SmbShare) {
        Sheet.menu(act, s.name, s.host, listOf(
            "Change" to { editShare(s) },
            "Remove" to { Sheet.confirm(act, "Remove ${s.name}?", "The box forgets its address and password. Nothing on the share changes.", "Remove", "Keep") { SmbShares.remove(s.id); render() } },
        ))
    }

    /** Address, share name and sign-in on one sheet; the connection is tried before it is kept. */
    private fun editShare(existing: SmbShare?) {
        val s = Sheet(act, if (existing == null) "Add a network share" else "Change ${existing.name}",
            "Address is the NAS or computer (nas, or 192.168.1.20). Share is the folder it offers (Movies). Leave the username empty to join as a guest.")
        val addr = s.field("Address", existing?.let { it.host + if (it.port != 445) ":${it.port}" else "" }.orEmpty()) { }
        val share = s.field("Share name", existing?.share.orEmpty()) { }
        val user = s.field("Username (empty for guest)", existing?.user.orEmpty()) { }
        val pass = s.field("Password", existing?.password.orEmpty()) { }.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; transformationMethod = PasswordTransformationMethod.getInstance()
        }
        s.card("Connect and save", highlighted = true) {
            val (host, port, inAddress) = SmbShares.parseAddress(addr.text.toString())
            val name = share.text.toString().trim().trim('/').ifEmpty { inAddress }.substringBefore('/')
            if (host.isEmpty() || name.isEmpty()) { Pop.toast(act, "Both the address and the share name are needed", true); return@card }
            s.dismiss()
            tryShare(SmbShare(existing?.id ?: SmbShares.newId(), "$name on $host", host, name, user.text.toString().trim(), pass.text.toString(), "", port))
        }
        s.card("Cancel") { s.dismiss() }
        s.show(); addr.requestFocus()
    }

    private fun tryShare(sh: SmbShare) {
        Pop.toast(act, "Connecting to ${sh.host}…")
        Http.background {
            val error = try { SmbClients.forget(sh); SmbClients.list(sh, ""); null } catch (e: Throwable) { e }
            Http.post {
                if (error == null) { SmbShares.save(sh); Pop.toast(act, "Connected"); stack.clear(); stack += Page("Files", Kind.ROOT, ""); stack.last().focus = sh.name; render() }
                else Sheet.message(act, "Could not connect", friendly(error),
                    Sheet.Act("Change it", true) { editShare(sh) }, Sheet.Act("Save anyway") { SmbShares.save(sh); render() }, Sheet.Act("Cancel"))
            }
        }
    }

    companion object {
        const val REQ = 4721

        /** What went wrong, in words a person can act on. */
        fun friendly(e: Throwable): String {
            val m = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return when {
                "LOGON_FAILURE" in m || "ACCESS_DENIED" in m || "WRONG_PASSWORD" in m -> "The username or password was not accepted. Check them, or try a guest login."
                "BAD_NETWORK_NAME" in m || "OBJECT_PATH_NOT_FOUND" in m || "OBJECT_NAME_NOT_FOUND" in m -> "The share name was not found on that address. Check its spelling."
                "NO_SUCH_FILE" in m -> "That folder is no longer there."
                "refused" in m || "Connection" in m || "timed out" in m || "Unable to resolve" in m || "UnknownHost" in m || "failed to connect" in m -> "Could not reach that address. Check it, and that the NAS or computer is on."
                m.isNotBlank() -> m.take(160)
                else -> "That did not work."
            }
        }
    }
}

/** Starts a file in the player: the same player as everything else, with its resume point and Continue Watching entry kept under the file. */
object FilesPlay {
    fun play(act: Activity, f: FileItem) {
        val id = VideoFiles.playId(f.uri)
        val resume = WatchStore.forMeta(id)
        val stream = StreamItem("Files", "", f.uri, f.name, "", f.name, null, 0, f.size, false, false, null)
        PlaySession.apply {
            meta = Meta(id, "movie", VideoFiles.title(f.name))
            videoId = f.uri; season = -1; episode = -1; episodeTitle = null
            ranked = listOf(stream); current = stream; autoSelected = false; resolving = false; noStreams = null
            resumeMs = if (resume != null && resume.fraction in 0.02f..0.93f) resume.positionMs else 0L
        }
        act.startActivity(Intent(act, PlayerActivity::class.java))
    }

    /** Continue Watching on Home: the file is opened again from where it stopped. */
    fun resume(act: Activity, e: WatchStore.Entry) = play(act, FileItem(e.name, false, 0, uri = e.videoId))
}
