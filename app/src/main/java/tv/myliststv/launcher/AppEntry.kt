package tv.myliststv.launcher

import android.content.ComponentName

/** One launchable app. Holds no icon: icons load on bind so memory stays small. */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    /** True for phone-style apps that have no TV launcher entry. */
    val sideloaded: Boolean,
)
