package tv.myliststv.launcher.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.content.ComponentName
import org.xmlpull.v1.XmlPullParser

/**
 * Icon packs in the common launcher format (the ones Play Store lists for Nova, ADW and Apex): an installed app whose appfilter.xml says
 * which picture stands for which app. One pack is used at a time, read once into two small tables; the pictures load as they are shown.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME")
    private val COMPONENT = Regex("""ComponentInfo\{([^/]+)/([^}]+)\}""")

    fun installed(c: Context): List<Pack> {
        val pm = c.packageManager
        return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }.map { it.activityInfo.packageName }.distinct()
            .map { p -> Pack(p, try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p }) }
            .sortedBy { it.label.lowercase() }
    }

    /** "pkg/Class" to picture name, and "pkg" to the picture of its first listed activity. */
    class Table(val byComponent: Map<String, String>, val byPackage: Map<String, String>)

    fun parse(p: XmlPullParser): Table {
        val comp = HashMap<String, String>(); val pkg = HashMap<String, String>()
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "item") {
                val d = p.getAttributeValue(null, "drawable"); val m = p.getAttributeValue(null, "component")?.let { COMPONENT.find(it) }
                if (d != null && m != null) {
                    val (a, cls) = m.destructured
                    comp["$a/${if (cls.startsWith(".")) a + cls else cls}"] = d; if (a !in pkg) pkg[a] = d
                }
            }
            p.next()
        }
        return Table(comp, pkg)
    }

    private var loaded = ""
    private var table = Table(emptyMap(), emptyMap())
    private var res: Resources? = null

    /** Reads the chosen pack's list if it has not been read yet. Not for the main thread. */
    @Synchronized fun prepare(c: Context) {
        val pack = Prefs.iconPack
        if (pack == loaded) return
        loaded = pack; table = Table(emptyMap(), emptyMap()); res = null
        if (pack.isEmpty()) return
        try {
            val r = c.packageManager.getResourcesForApplication(pack)
            val id = r.getIdentifier("appfilter", "xml", pack)
            table = if (id != 0) parse(r.getXml(id)) else parse(android.util.Xml.newPullParser().apply { setInput(r.assets.open("appfilter.xml"), null) })
            res = r
        } catch (_: Throwable) { }
    }

    /** The pack's picture for an app, or null when there is no pack or it has none for this app. */
    @Synchronized fun drawable(component: ComponentName): Drawable? {
        val r = res ?: return null
        val name = table.byComponent["${component.packageName}/${component.className}"] ?: table.byPackage[component.packageName] ?: return null
        val id = r.getIdentifier(name, "drawable", loaded).takeIf { it != 0 } ?: r.getIdentifier(name, "mipmap", loaded)
        return if (id == 0) null else try { r.getDrawable(id, null) } catch (_: Exception) { null }
    }
}
