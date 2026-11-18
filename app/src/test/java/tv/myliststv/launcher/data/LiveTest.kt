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

class UpdaterTest {
    @Test fun picksTheNewestReleaseThatHasAnApk() {
        val json = """[{"tag_name":"v0.1.7","assets":[{"name":"MyListsTV.apk","browser_download_url":"https://x/7.apk"}]},
          {"tag_name":"v0.1.9","assets":[]},
          {"tag_name":"v0.1.12","assets":[{"name":"MyListsTV.apk","browser_download_url":"https://x/12.apk"}]}]"""
        val r = Updater.parse(json)!!
        assertEquals(12, r.build); assertEquals("https://x/12.apk", r.apkUrl)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgTest {
    @Test fun readsGuideForOurChannelsOnly() {
        val now = 1_795_000_000_000L
        fun x(t: Long) = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US).format(java.util.Date(t))
        val xml = """<tv><programme start="${x(now - 600_000)}" stop="${x(now + 600_000)}" channel="a"><title>News Now</title><desc>x</desc></programme>
          <programme start="${x(now + 600_000)}" stop="${x(now + 2_400_000)}" channel="a"><title lang="en">Weather</title></programme>
          <programme start="${x(now)}" stop="${x(now + 1000)}" channel="other"><title>Ignored</title></programme></tv>"""
        val g = Epg.parse(xml.byteInputStream(), setOf("a"), now, now + 12 * 3600_000L)
        assertEquals(listOf("News Now", "Weather"), g["a"]!!.map { it.title })
        assertNull(g["other"])
    }

    @Test fun readsPlaylistGuideLinksAndChannelIds() {
        val m3u = "#EXTM3U url-tvg=\"https://g/x.xml.gz,https://g/y.xml\"\n#EXTINF:-1 tvg-id=\"a\" group-title=\"G\",A\nhttp://h/a"
        assertEquals(listOf("https://g/x.xml.gz", "https://g/y.xml"), M3u.epgUrls(m3u))
        assertEquals("a", M3u.parse(m3u)[0].tvgId)
    }
}
