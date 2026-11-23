package tv.myliststv.launcher.data

import tv.myliststv.launcher.App

/** One place that decides what a pasted link is: a Stremio addon, or a recovery code from the phone page. */
object Links {
    fun isAddon(link: String) = link.trim().let { it.startsWith("stremio://") || it.substringBefore('?').endsWith("manifest.json") }

    fun add(link: String, reply: (String) -> Unit) {
        val t = link.trim()
        if (Backup.isCode(t)) { Backup.restore(App.instance, t, reply); return }
        if (!isAddon(t)) return reply("That does not look like an addon link or a recovery code.")
        val url = AddonStore.normalize(t) ?: return reply("That does not look like an addon link.")
        AddonStore.probe(url) { m, err -> if (m == null) reply(err ?: "Could not install it.") else { AddonStore.add(url); Backup.auto(); reply("Installed ${m.name}") } }
    }
}
