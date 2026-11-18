package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test

class LiveTest {
    @Test fun parsesM3uChannels() {
        val text = """#EXTM3U
#EXTINF:-1 tvg-id="a" tvg-logo="https://l/a.png" group-title="News",Channel A
#EXTVLCOPT:http-user-agent=x
http://h/a.m3u8
#EXTINF:-1 group-title="Sports",Channel B
https://h/b.ts
#EXTINF:-1,No Group
not-a-url
"""
        val l = M3u.parse(text)
        assertEquals(2, l.size)
        assertEquals("Channel A", l[0].name); assertEquals("News", l[0].group); assertEquals("https://l/a.png", l[0].logo)
        assertEquals("Sports", l[1].group); assertNull(l[1].logo)
    }

    @Test fun capsHugePlaylists() {
        val text = (1..50).joinToString("\n") { "#EXTINF:-1,C$it\nhttp://h/$it" }
        assertEquals(10, M3u.parse(text, max = 10).size)
    }

    @Test fun routesLinksToAddonOrPlaylist() {
        assertTrue(Links.isAddon("stremio://x.example/manifest.json"))
        assertTrue(Links.isAddon("https://x.example/a/manifest.json"))
        assertFalse(Links.isAddon("https://iptv.example/get.php?username=u&password=p&type=m3u_plus"))
    }
}
