package tv.myliststv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import tv.myliststv.launcher.data.IconPacks
import tv.myliststv.launcher.data.Pictures
import tv.myliststv.launcher.data.Prefs

/**
 * Lists launchable apps. TV apps come from the LEANBACK_LAUNCHER query and phone-style apps
 * from the LAUNCHER query, as two separate lookups, then merge by package with TV entries first.
 * (Idea taken from reading FLauncher; no code copied.)
 */
object AppRepository {
    fun load(context: Context): List<AppEntry> {
        IconPacks.prepare(context)
        val pm = context.packageManager
        val own = context.packageName
        val tv = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, sideloaded = false)
        val phone = query(pm, Intent.CATEGORY_LAUNCHER, sideloaded = true)
        val seen = HashSet<String>()
        val names = Prefs.appNames
        return (tv + phone)
            .filter { it.component.packageName != own && seen.add(it.component.packageName) }
            .map { a -> names[a.component.packageName]?.let { a.copy(label = it) } ?: a }
            .sortedBy { it.label.lowercase() }
    }

    /** The apps for the Home row: the owner's order, without the ones they hid. */
    fun forHome(context: Context): List<AppEntry> {
        val order = Prefs.appOrder; val hidden = Prefs.appHidden
        return load(context).filter { it.component.packageName !in hidden }.sortedBy { order.indexOf(it.component.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    /** What to draw for an app: the picture the owner chose for it, else the icon pack's, else its TV banner if it has one (a wide picture), else its icon. */
    fun look(ctx: Context, c: ComponentName): Pair<android.graphics.drawable.Drawable?, Boolean> {
        val own = Pictures.iconFile(ctx, c.packageName)
        if (own.exists()) android.graphics.BitmapFactory.decodeFile(own.path)?.let { return android.graphics.drawable.BitmapDrawable(ctx.resources, it) to (it.width > it.height * 1.3) }
        IconPacks.drawable(c)?.let { return it to false }
        val pm = ctx.packageManager
        val banner = try { pm.getActivityBanner(c) } catch (_: Exception) { null }
        if (banner != null) return banner to true
        return (try { pm.getActivityIcon(c) } catch (_: Exception) { null }) to false
    }

    private fun query(pm: PackageManager, category: String, sideloaded: Boolean): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0).map { ri ->
            AppEntry(
                label = ri.loadLabel(pm).toString(),
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                sideloaded = sideloaded,
            )
        }
    }
}
