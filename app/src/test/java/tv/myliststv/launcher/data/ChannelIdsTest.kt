package tv.myliststv.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelIdsTest {
    private val sync = """{"ok":true,"channels":[
      {"id":"ch23est7srcpo7","name":"The Complete Arrowverse Timeline","catalogId":"channel_ch23est7srcpo7"},
      {"id":"chk3ea8kq2dwr5","name":"Saturday Classics Cartoons","catalogId":"channel_chk3ea8kq2dwr5"}]}"""

    @Test fun findsTheDetailsIdOfAChannelByItsName() {
        assertEquals("channel_ch23est7srcpo7", ChannelIds.byName(sync, "The Complete Arrowverse Timeline"))
        assertEquals("the name is matched without regard to case", "channel_chk3ea8kq2dwr5", ChannelIds.byName(sync, "saturday classics cartoons"))
    }

    @Test fun saysNothingForAnUnknownChannelOrABrokenAnswer() {
        assertNull(ChannelIds.byName(sync, "Something Else"))
        assertNull(ChannelIds.byName("not json", "The Complete Arrowverse Timeline"))
        assertNull(ChannelIds.byName("""{"channels":[{"name":"X","catalogId":"tt123"}]}""", "X"))
    }
}
