package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test
import tv.myliststv.launcher.ui.FilesScreen

class FilesTest {
    private fun v(path: String, size: Long = 1) = LocalFiles.Video("content://x/" + path.hashCode(), path, path.substringAfterLast('/'), size)

    @Test fun knowsVideosByTheirEnding() {
        assertTrue(VideoFiles.isVideo("Movie.2019.MKV")); assertTrue(VideoFiles.isVideo("a.mp4"))
        assertFalse(VideoFiles.isVideo("poster.jpg")); assertFalse(VideoFiles.isVideo("noextension"))
    }

    @Test fun readsAFileNameAsATitle() {
        assertEquals("The Movie 2019 1080p", VideoFiles.title("The.Movie.2019.1080p.mkv"))
        assertEquals("My Holiday", VideoFiles.title("My_Holiday.mp4"))
    }

    @Test fun sizesInWords() {
        assertEquals("", VideoFiles.sizeText(0)); assertEquals("4 KB", VideoFiles.sizeText(5000)); assertEquals("700 MB", VideoFiles.sizeText(700L shl 20)); assertEquals("1.5 GB", VideoFiles.sizeText(3L shl 29))
    }

    @Test fun episode2ComesBeforeEpisode10() {
        val names = listOf("Show S01E10.mkv", "Show S01E2.mkv", "Show S01E1.mkv")
        assertEquals(listOf("Show S01E1.mkv", "Show S01E2.mkv", "Show S01E10.mkv"), names.sortedWith { a, b -> VideoFiles.natural(a, b) })
    }

    @Test fun foldersComeBeforeVideos() {
        val l = listOf(FileItem("b.mkv", false), FileItem("Zed", true), FileItem("a.mkv", false), FileItem("Alpha", true))
        assertEquals(listOf("Alpha", "Zed", "a.mkv", "b.mkv"), l.sortedWith(VideoFiles.order).map { it.name })
    }

    @Test fun theSameFileKeepsTheSameId() {
        assertEquals(VideoFiles.playId("smb://nas/Movies/a.mkv"), VideoFiles.playId("smb://nas/Movies/a.mkv"))
        assertNotEquals(VideoFiles.playId("smb://nas/Movies/a.mkv"), VideoFiles.playId("smb://nas/Movies/b.mkv"))
        assertTrue(VideoFiles.playId("x").startsWith("file:"))
    }

    // ---- this box and USB drives
    private val videos = listOf(
        v("/storage/emulated/0/Movies/Film One.mkv"), v("/storage/emulated/0/Movies/Kids/Cartoon.mp4"), v("/storage/emulated/0/Movies/Kids/More/Deep.mp4"),
        v("/storage/1234-ABCD/Shows/S1/e1.mkv"), v("/storage/1234-ABCD/Shows/S1/e2.mkv"), v("/storage/1234-ABCD/top.mp4"),
    )

    @Test fun findsTheDrivesAndNamesThem() {
        val roots = LocalFiles.roots(videos, mapOf("/storage/1234-ABCD" to "SanDisk"))
        assertEquals(listOf("Internal storage", "SanDisk"), roots.map { it.name })
        assertEquals(listOf(3, 3), roots.map { it.count })
        assertEquals("USB drive (1234-ABCD)", LocalFiles.rootLabel("/storage/1234-ABCD", emptyMap()))
    }

    @Test fun listsWhatIsDirectlyInAFolder() {
        val inMovies = LocalFiles.children(videos, "/storage/emulated/0/Movies")
        assertEquals(listOf("Kids", "Film One.mkv"), inMovies.map { it.name })
        assertEquals(2, inMovies[0].count); assertTrue(inMovies[0].isDir); assertEquals("/storage/emulated/0/Movies/Kids", inMovies[0].path)
        assertFalse(inMovies[1].isDir); assertTrue(inMovies[1].uri.startsWith("content://"))
        assertEquals(listOf("Shows", "top.mp4"), LocalFiles.children(videos, "/storage/1234-ABCD").map { it.name })
    }

    @Test fun aFolderNamedLikeAnotherIsNotMixedIn() {
        val vs = listOf(v("/storage/x/Movie/a.mkv"), v("/storage/x/Movies/b.mkv"))
        assertEquals(listOf("a.mkv"), LocalFiles.children(vs, "/storage/x/Movie").map { it.name })
    }

    // ---- network shares
    @Test fun readsAnAddress() {
        assertEquals(Triple("nas", 445, ""), SmbShares.parseAddress("nas"))
        assertEquals(Triple("192.168.1.20", 4445, ""), SmbShares.parseAddress("192.168.1.20:4445"))
        assertEquals(Triple("nas", 445, "Movies/Kids"), SmbShares.parseAddress("smb://nas/Movies/Kids/"))
        assertEquals(Triple("nas", 445, "Movies"), SmbShares.parseAddress("""\\nas\Movies"""))
    }

    @Test fun anAddressForThePlayerHasNoPasswordAndEncodesTheName() {
        val s = SmbShare("1", "x", "nas", "Movies", "bob", "secret")
        val u = s.uriFor("Kids/My Film #1.mkv")
        assertEquals("smb://nas/Movies/Kids/My%20Film%20%231.mkv", u)
        assertFalse(u.contains("secret") || u.contains("bob"))
        assertEquals("smb://nas:4445/Movies/a.mkv", s.copy(port = 4445).uriFor("a.mkv"))
    }

    @Test fun explainsAFailureInPlainWords() {
        assertTrue(FilesScreen.friendly(RuntimeException("STATUS_LOGON_FAILURE (0xc000006d)")).contains("username or password"))
        assertTrue(FilesScreen.friendly(RuntimeException("STATUS_BAD_NETWORK_NAME")).contains("share name"))
        assertTrue(FilesScreen.friendly(java.net.ConnectException("Connection refused")).contains("reach that address"))
        assertEquals("That did not work.", FilesScreen.friendly(RuntimeException()))
    }

    // ---- the pages along the top
    @Test fun pagesKeepTheStandardOrderUntilTheOwnerMovesThem() {
        assertEquals(Pages.all, Pages.visible(Pages.all, emptyList(), emptyList()))
        assertEquals(listOf("Home", "Series", "Movies", "Search", "Library", "Apps", "Files"), Pages.visible(Pages.all, listOf("Home", "Series", "Movies"), emptyList()))
    }

    @Test fun anyPageButHomeCanBeHidden() {
        assertEquals(listOf("Home", "Movies", "Series", "Search", "Library", "Apps"), Pages.visible(Pages.all, emptyList(), listOf("Files")))
        assertTrue("Home stays", "Home" in Pages.visible(Pages.all, emptyList(), listOf("Home", "Movies")))
    }

    @Test fun aNewPageAppearsAndAForgottenOneIsIgnored() {
        assertEquals(listOf("Series", "Home", "Movies", "Search", "Library", "Apps", "Files"), Pages.visible(Pages.all, listOf("Series", "Gone", "Home"), emptyList()))
    }

    // ---- reading a file name
    @Test fun readsAFilmsNameAndYear() {
        val g = FileMeta.guess("10 Things I Hate About You (1999) WEBDL-2160p.mkv")!!
        assertEquals("10 Things I Hate About You", g.title); assertEquals(1999, g.year); assertFalse(g.isEpisode)
        val d = FileMeta.guess("The.Dark.Knight.2008.1080p.BluRay.x264.mkv")!!
        assertEquals("The Dark Knight", d.title); assertEquals(2008, d.year)
    }

    @Test fun aYearAtTheStartIsPartOfTheTitle() {
        val g = FileMeta.guess("2012 (2009) 1080p.mkv")!!
        assertEquals("2012", g.title); assertEquals(2009, g.year)
    }

    @Test fun readsAnEpisode() {
        val a = FileMeta.guess("Severance - S02E03 - Who Is Alive.mkv")!!
        assertEquals("Severance", a.title); assertEquals(2, a.season); assertEquals(3, a.episode); assertTrue(a.isEpisode)
        val b = FileMeta.guess("the.office.us.s05e14.720p.hdtv.x264.mkv")!!
        assertEquals("the office us", b.title); assertEquals(5, b.season); assertEquals(14, b.episode)
        val c = FileMeta.guess("Friends 3x07.avi")!!
        assertEquals("Friends", c.title); assertEquals(3, c.season); assertEquals(7, c.episode)
    }

    @Test fun cutsReleaseJunkWhenThereIsNoYear() {
        assertEquals("Some Movie", FileMeta.guess("Some.Movie.1080p.WEB-DL.mkv")!!.title)
        assertNull(FileMeta.guess("1080p.mkv"))
    }

    @Test fun picksTheRightTitleAndYear() {
        fun m(id: String, name: String, year: String?) = Meta(id, "movie", name, year = year)
        val results = listOf(m("tt1", "The Dark Knight Rises", "2012"), m("tt2", "The Dark Knight", "2008"), m("tt3", "The Dark Knight", "1990"))
        assertEquals("tt2", FileMeta.pick(FileMeta.Guess("The Dark Knight", 2008, null, null), results)!!.id)
        assertNull("a different year is not a match", FileMeta.pick(FileMeta.Guess("The Dark Knight", 1975, null, null), results))
        assertEquals("tt2", FileMeta.pick(FileMeta.Guess("the dark knight", null, null, null), results.drop(1))!!.id)
        assertNull(FileMeta.pick(FileMeta.Guess("Nothing Like It", 2000, null, null), results))
    }

    // ---- finding files by name
    @Test fun matchesAllTheWordsInAnyOrder() {
        val t = FileIndex.tokens("dark knight")
        assertTrue(FileIndex.matches("The.Dark.Knight.2008.1080p.mkv", t))
        assertTrue(FileIndex.matches("Knight - Dark Edition.mp4", t))
        assertFalse(FileIndex.matches("The Dark Tower.mkv", t))
        assertFalse(FileIndex.matches("anything.mkv", emptyList()))
    }

    @Test fun namesStartingWithTheQueryComeFirst() {
        val l = listOf(FileItem("The Dark Knight.mkv", false), FileItem("Batman - Dark Knight.mkv", false), FileItem("Dark Knight Rises.mkv", false))
        assertEquals(listOf("Dark Knight Rises.mkv", "Batman - Dark Knight.mkv", "The Dark Knight.mkv"), FileIndex.rank(l, "dark knight").map { it.name })
    }
}
