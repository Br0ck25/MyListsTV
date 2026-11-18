package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class StreamRankTest {

    private fun s(addon: String, name: String, title: String, file: String? = null, size: Long = 0, cached: Boolean = false) =
        StreamItem(addon, "https://$addon.example", "https://cdn.example/$name/$title", name, title, file, null,
            StreamRank.resolution("$name $title"), if (size > 0) size else StreamRank.sizeBytes("$name $title"), cached, StreamRank.hasHdr("$name $title"), null)

    @Test fun readsResolutionFromText() {
        assertEquals(2160, StreamRank.resolution("Movie 2160p HDR"))
        assertEquals(2160, StreamRank.resolution("Movie 4K"))
        assertEquals(1080, StreamRank.resolution("Movie.1080p.WEB-DL"))
        assertEquals(720, StreamRank.resolution("Movie 720p"))
        assertEquals(0, StreamRank.resolution("Movie"))
    }

    @Test fun readsSizeFromText() {
        assertEquals(1_073_741_824L, StreamRank.sizeBytes("size 1 GB"))
        assertEquals((4.2 * 1_073_741_824).toLong(), StreamRank.sizeBytes("💾 4.2 GB"))
        assertEquals(500L * 1_048_576, StreamRank.sizeBytes("500 MB"))
        assertEquals(0L, StreamRank.sizeBytes("no size"))
    }

    @Test fun detectsCachedMarkers() {
        assertTrue(StreamRank.looksCached("[RD+] Torrentio", "x"))
        assertTrue(StreamRank.looksCached("TB ⚡", "x"))
        assertFalse(StreamRank.looksCached("[RD download]", "x"))
    }

    @Test fun sortsBestFirstWithCachedBeforeUncached() {
        val a = s("a", "1080p", "x", cached = false, size = 5_000_000_000)
        val b = s("b", "1080p", "y", cached = true, size = 2_000_000_000)
        val c = s("c", "2160p", "z", cached = true, size = 20_000_000_000)
        val sorted = StreamRank.sorted(listOf(a, b, c), maxResolution = 2160)
        assertEquals(listOf(c, b, a), sorted)
    }

    @Test fun capPutsTooHighResolutionLastButKeepsIt() {
        val hi = s("a", "2160p", "x", cached = true, size = 30_000_000_000)
        val mid = s("b", "1080p", "y", cached = false, size = 4_000_000_000)
        val sorted = StreamRank.sorted(listOf(hi, mid), maxResolution = 1080)
        assertEquals(listOf(mid, hi), sorted)
    }

    @Test fun playableDropsTooHighUnlessNothingElse() {
        val hi = s("a", "2160p", "x")
        val mid = s("b", "1080p", "y")
        assertEquals(listOf(mid), StreamRank.playable(listOf(hi, mid), 1080))
        assertEquals(listOf(hi), StreamRank.playable(listOf(hi), 1080))
    }

    @Test fun fallbackTriesNextBestAndNeverRepeats() {
        val a = s("a", "1080p", "one", file = "f1")
        val b = s("b", "1080p", "two", file = "f2")
        val c = s("c", "720p", "three", file = "f3")
        val fb = SourceFallback(listOf(a, b, c), maxExtraTries = 2)
        assertEquals(b, fb.next(a))
        assertEquals(c, fb.next(b))
        assertNull(fb.next(c))
    }

    @Test fun fallbackStopsAtTheLimit() {
        val list = (1..5).map { s("a$it", "1080p", "t$it", file = "f$it") }
        val fb = SourceFallback(list, maxExtraTries = 2)
        assertNotNull(fb.next(list[0]))
        assertNotNull(fb.next(list[1]))
        assertNull(fb.next(list[2]))
        assertEquals(2, fb.attempts)
    }

    @Test fun fallbackTreatsSameFileFromSameAddonAsOneSource() {
        val a = s("a", "1080p", "x", file = "same.mkv")
        val dup = a.copy(url = "https://other/link")
        val b = s("b", "1080p", "y", file = "other.mkv")
        val fb = SourceFallback(listOf(a, dup, b), maxExtraTries = 3)
        assertEquals(b, fb.next(a))
    }
}
