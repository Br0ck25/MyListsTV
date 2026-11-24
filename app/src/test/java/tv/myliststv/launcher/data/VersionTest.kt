package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class VersionTest {
    @Test fun versionRollsOverFrom099To020() {
        assertEquals("0.1.1", Version.name(1)); assertEquals("0.1.44", Version.name(44)); assertEquals("0.1.99", Version.name(99))
        assertEquals("0.2.0", Version.name(100)); assertEquals("0.2.1", Version.name(101)); assertEquals("0.2.99", Version.name(199)); assertEquals("0.3.0", Version.name(200))
    }

    @Test fun aTagGivesBackItsBuildNumberAndNamesAreOrderedLikeBuilds() {
        assertEquals(44, Version.build("v0.1.44")); assertEquals(99, Version.build("v0.1.99")); assertEquals(100, Version.build("v0.2.0")); assertEquals(205, Version.build("v0.3.5"))
        for (b in 1..450) assertEquals(b, Version.build("v" + Version.name(b)))
        assertNull(Version.build("v1.0.0")); assertNull(Version.build("nightly")); assertNull(Version.build("v0.1.100"))
    }

    @Test fun theUpdaterPicksTheNewestAcrossTheRollover() {
        fun rel(tag: String) = """{"tag_name":"$tag","assets":[{"name":"MyListsTV.apk","browser_download_url":"https://x/$tag.apk"}]}"""
        val r = Updater.parse("[" + listOf("v0.1.98", "v0.2.1", "v0.1.99", "v0.2.0").joinToString(",") { rel(it) } + "]")!!
        assertEquals("v0.2.1", r.tag); assertEquals(101, r.build)
    }
}
