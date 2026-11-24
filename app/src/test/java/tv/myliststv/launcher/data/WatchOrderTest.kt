package tv.myliststv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.App

/** Continue watching: the last thing watched is first, the one before it second. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class WatchOrderTest {
    private fun e(id: String, pos: Long, dur: Long, at: Long) = WatchStore.Entry(id, "series", id, null, null, "$id:1:1", 1, 1, null, pos, dur, at)

    @Test fun lastWatchedIsFirstAndATitleJustOpenedStillCounts() {
        WatchStore.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        WatchStore.all().forEach { WatchStore.remove(it.metaId) }
        WatchStore.save(e("old", 0, 0, 1_000))                    // next up from the account, a day ago
        WatchStore.save(e("half", 600_000, 1_200_000, 2_000))     // watched part way
        WatchStore.save(e("justOpened", 2_000, 1_200_000, 3_000)) // opened, left after 0.2%
        assertEquals(listOf("justOpened", "half", "old"), WatchStore.continueWatching().map { it.metaId })
        assertEquals("shown as next up, not as 0% watched", 0L, WatchStore.forMeta("justOpened")!!.durationMs)
    }
}
