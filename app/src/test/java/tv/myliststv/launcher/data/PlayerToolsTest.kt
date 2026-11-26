package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test
import tv.myliststv.launcher.ui.ExternalPlayer
import tv.myliststv.launcher.ui.PlayerEngine

class PlayerToolsTest {

    // ---- subtitle delay
    @Test fun movesSubRipTimesAndLeavesTheWordsAlone() {
        val src = listOf("1", "00:00:01,000 --> 00:00:02,500", "It is 10:30 now", "", "2", "00:59:59,900 --> 01:00:00,100", "Bye").joinToString("\n")
        val out = SubtitleTools.shiftCues(src, 1500).lines()
        assertEquals("00:00:02,500 --> 00:00:04,000", out[1])
        assertEquals("It is 10:30 now", out[2])
        assertEquals("01:00:01,400 --> 01:00:01,600", out[5])
        assertEquals("Bye", out[6])
    }

    @Test fun neverGoesBelowZero() {
        assertEquals("00:00:00,000 --> 00:00:00,500", SubtitleTools.shiftCues("00:00:01,000 --> 00:00:02,000", -1500))
    }

    @Test fun webVttWithoutHoursStaysThatWay() {
        assertEquals("00:02.000 --> 00:03.500 line:90%", SubtitleTools.shiftCues("00:01.000 --> 00:02.500 line:90%", 1000))
        assertEquals("a shifted time that needs hours gets them", "01:00:00.000 --> 01:00:00.900", SubtitleTools.shiftCues("59:59.000 --> 59:59.900", 1000))
    }

    @Test fun movesSubStationLines() {
        val src = "[Events]\nDialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,Hello, world\nComment: 0,0:00:01.00,0:00:02.00,x"
        val out = SubtitleTools.shiftAss(src, 2000).lines()
        assertEquals("Dialogue: 0,0:00:03.00,0:00:05.50,Default,,0,0,0,,Hello, world", out[1])
        assertEquals("Comment: 0,0:00:01.00,0:00:02.00,x", out[2])
    }

    @Test fun picksTheFormatFromTheLinkAndZeroChangesNothing() {
        assertEquals("x", SubtitleTools.shift("x", 0, "https://a/b.srt"))
        assertTrue(SubtitleTools.shift("Dialogue: 0,0:00:01.00,0:00:02.00,a", 1000, "https://a/b.ass?x=1").contains("0:00:02.00,0:00:03.00"))
    }

    @Test fun labelsTheDelay() {
        assertEquals("No delay", SubtitleTools.label(0))
        assertEquals("+0.5 s", SubtitleTools.label(500))
        assertEquals("-1.2 s", SubtitleTools.label(-1200))
    }

    // ---- the report
    @Test fun redactsLinks() {
        assertEquals("https://cdn.example.com/dl/movie.mkv", PlayerReport.redact("https://user:pw@cdn.example.com/dl/movie.mkv?token=abc&x=1#f").replace("user:pw@", ""))
        assertEquals("https://host/d/…/file.mkv", PlayerReport.redact("https://host/d/AbCdEfGhIjKlMnOpQrStUvWxYz0123/file.mkv?sig=1"))
        assertFalse(PlayerReport.redact("https://host/a?token=SECRET").contains("SECRET"))
    }

    @Test fun reportSkipsEmptyLines() = assertEquals("A: 1\nC: 3", PlayerReport.build(listOf("A" to "1", "B" to "", "C" to "3")))

    // ---- device-aware ranking
    private fun st(name: String) = StreamItem("a", "https://a", "https://a/$name", name, "", null, null, 2160, 0, false, false, null)

    @Test fun readsHowAFileIsEncoded() {
        val t = StreamTraits.of("Movie.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos")
        assertTrue(t.dolbyVision); assertTrue(t.hdr10); assertTrue(t.hevc); assertTrue(t.remux); assertTrue(t.hdr)
        assertFalse(StreamTraits.of("Movie 1080p WEB-DL x264").hdr)
        assertTrue(StreamTraits.of("Movie [AV1] 1080p").av1)
        assertFalse("DVD is not Dolby Vision", StreamTraits.of("Movie DVDRip").dolbyVision)
    }

    @Test fun onlyFilesThatWontPlayMoveDown() {
        val plain = DeviceCaps(hevc = true, av1 = false, dolbyVision = false, displayHdr = false)
        val dv = st("Show 2160p DV HEVC"); val dvHdr = st("Show 2160p DV HDR10 HEVC"); val av1 = st("Show 1080p AV1"); val ok = st("Show 1080p x265")
        val sorted = DeviceRank.sorted(listOf(dv, av1, dvHdr, ok), plain)
        assertEquals("the DV file with an HDR10 layer and the normal file keep the addons' order", listOf(dvHdr, ok, dv, av1), sorted)
    }

    @Test fun aBoxThatCanDoItKeepsEverythingInPlace() {
        val strong = DeviceCaps(hevc = true, av1 = true, dolbyVision = true, displayHdr = true)
        val l = listOf(st("A 2160p DV"), st("B 1080p AV1"), st("C 720p"))
        assertEquals(l, DeviceRank.sorted(l, strong))
    }

    // ---- display and buffer
    private fun m(id: Int, rate: Float) = PlayerEngine.Mode(id, 3840, 2160, rate)

    @Test fun picksARefreshRateThatFitsTheFilm() {
        val modes = listOf(m(1, 60f), m(2, 59.94f), m(3, 50f), m(4, 24f), m(5, 23.976f))
        assertEquals(5, PlayerEngine.chooseMode(modes, m(1, 60f), 23.976f)?.id)
        assertEquals(4, PlayerEngine.chooseMode(modes, m(1, 60f), 24f)?.id)
        assertEquals(3, PlayerEngine.chooseMode(modes, m(1, 60f), 25f)?.id)
        assertNull("60 Hz already shows 30 fps evenly", PlayerEngine.chooseMode(modes, m(1, 60f), 30f))
        assertNull("nothing fits 23.976 here", PlayerEngine.chooseMode(listOf(m(1, 60f), m(3, 50f)), m(1, 60f), 23.976f))
    }

    @Test fun anotherResolutionIsNotAChoice() {
        val other = PlayerEngine.Mode(9, 1920, 1080, 24f)
        assertNull(PlayerEngine.chooseMode(listOf(m(1, 60f), other), m(1, 60f), 24f))
    }

    @Test fun aWeakBoxKeepsTheSmallBuffer() {
        assertEquals(50_000 to 32 * 1024 * 1024, PlayerEngine.bufferPlan(180, true))
        assertEquals(50_000, PlayerEngine.bufferPlan(0, false).first)
        assertEquals(180_000, PlayerEngine.bufferPlan(180, false).first)
    }

    // ---- the other app
    @Test fun readsWhereTheOtherAppStopped() {
        assertEquals(125_000L, ExternalPlayer.stoppedAt(mapOf("position" to 125_000)))
        assertEquals(90_000L, ExternalPlayer.stoppedAt(mapOf("extra_position" to 90_000L, "position" to 5)))
        assertNull(ExternalPlayer.stoppedAt(mapOf("position" to 0)))
        assertNull(ExternalPlayer.stoppedAt(emptyMap()))
    }

    // ---- manual zoom
    @Test fun zoomRoundTripsAndStaysInRange() {
        val z = Zoom(120, 110, -5, 8)
        assertEquals(z, Zoom.decode(z.encode()))
        assertEquals(Zoom(), Zoom.decode(null)); assertEquals(Zoom(), Zoom.decode("garbage")); assertEquals(Zoom(), Zoom.decode("1,2,3"))
        assertEquals(Zoom(250, 50, 50, -50), Zoom(900, 1, 80, -80).clamped())
        assertTrue(Zoom().isDefault); assertFalse(Zoom(101, 100, 0, 0).isDefault)
    }

    @Test fun fillScalesBothSidesTheSame() {
        val scope = Zoom.fill(2.39f, 16f / 9f)    // a wide film on a 16:9 screen has bars top and bottom
        assertEquals(scope.w, scope.h)
        assertEquals(134, scope.w)
        assertEquals(Zoom(), Zoom.fill(0f, 1.78f))
        assertEquals(Zoom.fill(1.33f, 1.78f).w, 133)    // a 4:3 picture has bars at the sides
    }
}
