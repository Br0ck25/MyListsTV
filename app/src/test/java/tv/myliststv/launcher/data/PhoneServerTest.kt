package tv.myliststv.launcher.data

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class PhoneServerTest {
    private var server: PhoneServer? = null
    @After fun stop() { server?.stop() }

    private fun call(port: Int, path: String, body: ByteArray? = null): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
    }
    private fun start(port: Int, onPicture: ((ByteArray) -> String)? = null, onAdd: (String, (String) -> Unit) -> Unit = { _, r -> r("ok") }): PhoneServer {
        val s = PhoneServer(port, onPicture, onAdd); s.start(); server = s
        repeat(50) { try { call(port, "/logo-none"); return s } catch (_: Exception) { Thread.sleep(40) } }
        return s
    }

    @Test fun aPictureArrivesByteForByteAndTheReplyIsShown() {
        var got = ByteArray(0)
        start(18771, onPicture = { got = it; "Done." })
        val bytes = ByteArray(300_000) { (it * 7).toByte() }
        assertEquals(200 to "Done.", call(18771, "/upload", bytes))
        assertArrayEquals(bytes, got)
    }

    @Test fun thePicturePageIsServedAndAnOversizedOrUnexpectedUploadIsRefused() {
        start(18772)
        assertTrue(call(18772, "/picture").second.contains("Send a picture"))
        assertEquals("That picture is too big.", call(18772, "/upload", ByteArray(10)).second)
    }

    @Test fun pastingAnAddonLinkStillWorks() {
        var link = ""
        start(18773, onAdd = { l, r -> link = l; r("Installed.") })
        assertTrue(call(18773, "/", "link=https%3A%2F%2Fx.test%2Fmanifest.json".toByteArray()).second.contains("Installed."))
        assertEquals("https://x.test/manifest.json", link)
    }
}
