package tv.myliststv.launcher.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = tv.myliststv.launcher.App::class)
class IconPacksTest {
    private fun parse(xml: String) = IconPacks.parse(android.util.Xml.newPullParser().apply { setInput(StringReader(xml)) })

    @Test fun readsAnAppfilterTheWayPacksWriteIt() {
        val t = parse("""<?xml version="1.0" encoding="utf-8"?>
            <resources>
              <iconback img1="back" />
              <item component="ComponentInfo{com.netflix.ninja/com.netflix.ninja.MainActivity}" drawable="netflix" />
              <item component="ComponentInfo{com.netflix.ninja/.Other}" drawable="netflix_two" />
              <item component="ComponentInfo{org.videolan.vlc/org.videolan.vlc.gui.MainActivity}" drawable="vlc" />
              <item component="garbage" drawable="x" />
              <item drawable="nothing" />
            </resources>""")
        assertEquals("netflix", t.byComponent["com.netflix.ninja/com.netflix.ninja.MainActivity"])
        assertEquals("netflix_two", t.byComponent["com.netflix.ninja/com.netflix.ninja.Other"])
        assertEquals("vlc", t.byComponent["org.videolan.vlc/org.videolan.vlc.gui.MainActivity"])
        assertEquals("the first listed picture stands for the whole app", "netflix", t.byPackage["com.netflix.ninja"])
        assertEquals(3, t.byComponent.size)
    }

    @Test fun anEmptyOrBrokenFileGivesNothing() {
        assertTrue(parse("<resources/>").byComponent.isEmpty())
    }
}
