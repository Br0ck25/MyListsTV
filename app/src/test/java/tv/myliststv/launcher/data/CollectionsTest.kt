package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class CollectionsTest {
    private val body = """{"ok":true,"collections":[
      {"id":"c1","title":"Streaming Services","folders":[
        {"id":"f1","title":"Disney+","tileShape":"LANDSCAPE","coverImageUrl":"https://mylistsaddon.com/api/channel-poster?name=Disney%2B&format=landscape&v=6","catalogSources":[
           {"addonId":"app.my-list.collections","catalogId":"tmdb-chart-disney","name":"Disney+","type":"movie"},
           {"addonId":"app.my-list.collections","catalogId":"tmdb-chart-disney","name":"Disney+","type":"series"}]},
        {"id":"f2","title":"Family","tileShape":"POSTER","hideTitle":true,"coverImageUrl":"https://mylistsaddon.com/api/channel-poster?name=Family&v=6","catalogSources":[{"catalogId":"tmdb-genre-family","name":"Family","type":"movie"}]},
        {"id":"f3","title":"Hidden","hidden":true,"catalogSources":[{"catalogId":"x","name":"x","type":"movie"}]},
        {"id":"f4","title":"Empty","catalogSources":[]},
        {"id":"f5","title":"Mine","coverImageUrl":"https://example.com/mine.jpg","catalogSources":[{"catalogId":"mine","name":"Mine","type":"movie"}]}]},
      {"id":"c2","title":"Off","enabled":false,"folders":[{"id":"z","title":"Z","catalogSources":[{"catalogId":"z","name":"Z","type":"movie"}]}]},
      {"id":"c3","title":"No folders","folders":[]}]}"""

    @Test fun readsTheVisibleCollectionsAndFolders() {
        val c = Collections.parse(body)
        assertEquals(listOf("Streaming Services"), c.map { it.title })
        assertEquals(listOf("Disney+", "Family", "Mine"), c[0].folders.map { it.title })
        assertEquals(2, c[0].folders[0].sources.size)
        assertTrue(c[0].folders[1].hideTitle)
    }

    @Test fun aTallGeneratedPictureIsAskedForWide_andAnOwnersPictureIsLeftAlone() {
        val f = Collections.parse(body)[0].folders
        assertTrue(f[1].cover!!.endsWith("&format=landscape"))
        assertEquals("https://example.com/mine.jpg", f[2].cover)
        assertEquals("https://mylistsaddon.com/x.png", Collections.cover("/x.png"))
        assertNull(Collections.cover(""))
    }

    @Test fun generatedPicturesAlreadyCarryTheName() {
        val f = Collections.parse(body)[0].folders
        assertTrue(f[0].nameIsOnCover); assertFalse(f[2].nameIsOnCover)
    }

    @Test fun movieAndSeriesRowsOfTheSameListAreTold() {
        val s = Collections.parse(body)[0].folders[0].sources
        assertEquals("Disney+ · Movies", Collections.rowName(s[0], s))
        assertEquals("Disney+ · Series", Collections.rowName(s[1], s))
        assertEquals("Solo", Collections.rowName(s[0].copy(name = "Solo"), listOf(s[0].copy(name = "Solo"))))
    }

    @Test fun garbageGivesNothing() { assertTrue(Collections.parse("nope").isEmpty()); assertTrue(Collections.parse("{}").isEmpty()) }

    @Test fun spotsAnSvgPicture() {
        assertTrue(ImageLoader.isSvg("<svg viewBox='0 0 600 338'></svg>".toByteArray()))
        assertTrue(ImageLoader.isSvg("<?xml version='1.0'?><svg></svg>".toByteArray()))
        assertFalse(ImageLoader.isSvg(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)))
    }

    @Test fun onlyTheAccountsOwnCollectionsShow() {
        val all = listOf(Collections.Collection("col_mylists_streaming_services", "Streaming Services", emptyList(), own = true), Collections.Collection("col_mylists_genres", "Genres", emptyList()))
        assertEquals(listOf("Streaming Services"), Collections.own(all, true).map { it.title })
        assertTrue("signed out has none of its own", Collections.own(all, false).isEmpty())
    }

    @Test fun readsTheOwnMark() {
        val c = Collections.parse("""{"collections":[{"id":"a","title":"A","own":true,"folders":[{"id":"f","title":"F","catalogSources":[{"catalogId":"x","name":"X","type":"movie"}]}]},{"id":"b","title":"B","folders":[{"id":"g","title":"G","catalogSources":[{"catalogId":"y","name":"Y","type":"movie"}]}]}]}""")
        assertEquals(listOf(true, false), c.map { it.own })
    }
}
