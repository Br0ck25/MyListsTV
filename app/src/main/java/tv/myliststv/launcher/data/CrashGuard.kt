package tv.myliststv.launcher.data

import android.content.Context

/** Counts recent crashes. Three in two minutes means safe mode: addons stay off so a bad addon cannot keep the home screen down. */
object CrashGuard {
    private const val WINDOW_MS = 120_000L
    private const val LIMIT = 3

    fun isSafe(times: List<Long>, now: Long) = times.count { now - it in 0..WINDOW_MS } >= LIMIT

    private fun sp(c: Context) = c.getSharedPreferences("crash", Context.MODE_PRIVATE)
    private fun times(c: Context) = (sp(c).getString("t", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }

    fun install(c: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // commit(), not apply(): the process is about to die.
            try { sp(c).edit().putString("t", (times(c) + System.currentTimeMillis()).takeLast(10).joinToString(",")).commit() } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun safeMode(c: Context) = isSafe(times(c), System.currentTimeMillis())
    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
