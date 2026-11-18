package tv.myliststv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Lists launchable apps. TV apps come from the LEANBACK_LAUNCHER query and phone-style apps
 * from the LAUNCHER query, as two separate lookups, then merge by package with TV entries first.
 * (Idea taken from reading FLauncher; no code copied.)
 */
object AppRepository {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val own = context.packageName
        val tv = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, sideloaded = false)
        val phone = query(pm, Intent.CATEGORY_LAUNCHER, sideloaded = true)
        val seen = HashSet<String>()
        return (tv + phone)
            .filter { it.component.packageName != own && seen.add(it.component.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    /** What to draw for an app: its TV banner if it has one (a wide picture), else its icon. */
    fun look(pm: PackageManager, c: ComponentName): Pair<android.graphics.drawable.Drawable?, Boolean> {
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
