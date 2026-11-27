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
        assertEquals("a.b", VideoFiles.title("a.b"))   // not a video: left alone
    }

    @Test fun sizesInWords() {
        assertEquals("", VideoFiles.sizeText(0)); assertEquals("5 KB", VideoFiles.sizeText(5000)); assertEquals("700 MB", VideoFiles.sizeText(700L shl 20)); assertEquals("1.5 GB", VideoFiles.sizeText(3L shl 29))
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
        assertEquals(Triple("nas", 445, "Movies"), SmbShares.parseAddress("\\nas\Movies"))
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
}
