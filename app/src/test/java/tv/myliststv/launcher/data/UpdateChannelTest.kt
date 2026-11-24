package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class UpdateChannelTest {
    private fun rel(tag: String, pre: Boolean, draft: Boolean = false, apk: Boolean = true) =
        """{"tag_name":"$tag","prerelease":$pre,"draft":$draft,"assets":[${if (apk) """{"name":"MyListsTV.apk","browser_download_url":"https://x/$tag.apk"}""" else ""}]}"""
    private fun list(vararg r: String) = "[" + r.joinToString(",") + "]"

    @Test fun productionOffersOnlyPromotedReleasesBetaOffersTheNewest() {
        val json = list(rel("v0.1.46", true), rel("v0.1.45", false), rel("v0.1.44", true))
        assertEquals("v0.1.45", Updater.parse(json, includeBeta = false)!!.tag)
        val b = Updater.parse(json, includeBeta = true)!!
        assertEquals("v0.1.46", b.tag); assertTrue(b.beta)
        assertFalse(Updater.parse(json, false)!!.beta)
    }

    @Test fun nothingPromotedYetMeansNoProductionRelease() {
        assertNull(Updater.parse(list(rel("v0.1.46", true), rel("v0.1.44", true)), includeBeta = false))
    }

    @Test fun draftsAndReleasesWithoutAnApkNeverCount() {
        val json = list(rel("v0.1.50", false, draft = true), rel("v0.1.49", false, apk = false), rel("v0.1.48", false))
        assertEquals("v0.1.48", Updater.parse(json, true)!!.tag); assertEquals("v0.1.48", Updater.parse(json, false)!!.tag)
    }

    @Test fun aPromotedBuildIsOfferedOnBothChannels() {
        val json = list(rel("v0.2.0", false), rel("v0.1.99", true))
        assertEquals("v0.2.0", Updater.parse(json, true)!!.tag); assertEquals("v0.2.0", Updater.parse(json, false)!!.tag)
    }
}
