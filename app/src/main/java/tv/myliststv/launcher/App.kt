package tv.myliststv.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import tv.myliststv.launcher.data.*

class App : Application() {
    companion object { lateinit var instance: App; private set }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        CrashGuard.install(this)
        Http.init(this)
        ImageLoader.init(cacheDir, Prefs.memoryCacheBytes())
        AddonStore.init(this)
        SmbShares.init(this)
        FileIndex.init(this)
        Profiles.init(this)
        WatchStore.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) ImageLoader.trim()
    }
}
