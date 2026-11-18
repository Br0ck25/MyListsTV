package tv.myliststv.launcher.data

import tv.myliststv.launcher.*

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.App
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class SecurityTest {
    private val soft: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @After fun reset() { Secure.keyProvider = { null } }

    @Test fun wrappedValuesAreNotReadableAndComeBack() {
        Secure.keyProvider = { soft }
        val w = Secure.wrap("https://addon.example/realdebrid=SECRETKEY/manifest.json")
        assertTrue(w.startsWith("enc1:")); assertFalse(w.contains("SECRETKEY"))
        assertEquals("https://addon.example/realdebrid=SECRETKEY/manifest.json", Secure.unwrap(w))
        assertNotEquals("each wrap uses a fresh iv", w, Secure.wrap("https://addon.example/realdebrid=SECRETKEY/manifest.json"))
    }

    @Test fun plainOldValuesStillReadAndALostKeyIsReportedNotCrashed() {
        Secure.keyProvider = { soft }
        assertEquals("plain", Secure.unwrap("plain"))
        val w = Secure.wrap("secret")
        Secure.keyProvider = { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertNull("a different key cannot open it", Secure.unwrap(w))
        Secure.keyProvider = { null }
        assertNull("no key at all", Secure.unwrap(w))
    }

    @Test fun aBoxWithoutAWorkingKeystoreKeepsValuesPlainSoNothingIsLost() {
        Secure.keyProvider = { null }
        assertEquals("keep-me", Secure.wrap("keep-me"))
        assertEquals("", Secure.wrap(""))
    }

    @Test fun storesWrapTheirSecretsOnDisk() {
        Secure.keyProvider = { soft }
        val ctx = ApplicationProvider.getApplicationContext<App>()
        AddonStore.init(ctx); LiveStore.init(ctx)
        AddonStore.add("https://addon.example/key=ABC123SECRET/manifest.json")
        LiveStore.add("http://iptv.example/get.php?username=bob&password=hunter2")
        Prefs.backupCode = "ABCD-EFGH-JKLM-NPQR"
        fun raw(file: String, key: String) = ctx.getSharedPreferences(file, 0).getString(key, "")!!
        for ((f, k, secret) in listOf(Triple("addons", "list", "ABC123SECRET"), Triple("live", "list", "hunter2"), Triple("settings", "backupCode", "ABCD-EFGH"))) {
            assertTrue("$f is wrapped", raw(f, k).startsWith("enc1:")); assertFalse("$f hides the secret", raw(f, k).contains(secret))
        }
        assertTrue(AddonStore.saved().any { it.url.contains("ABC123SECRET") })
        assertEquals(listOf("http://iptv.example/get.php?username=bob&password=hunter2"), LiveStore.playlists())
        assertEquals("ABCD-EFGH-JKLM-NPQR", Prefs.backupCode)
    }

    @Test fun anOlderPlainAddonListIsWrappedOnTheNextStart() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        Secure.keyProvider = { null }
        ctx.getSharedPreferences("addons", 0).edit().putString("list", """[{"url":"https://old.example/manifest.json","on":true}]""").commit()
        Secure.keyProvider = { soft }
        AddonStore.init(ctx)
        assertTrue(ctx.getSharedPreferences("addons", 0).getString("list", "")!!.startsWith("enc1:"))
        assertEquals("https://old.example/manifest.json", AddonStore.saved().single().url)
    }

    @Test fun aWrappedListWhoseKeyIsGoneFallsBackToTheDefaultAddon() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        Secure.keyProvider = { soft }
        AddonStore.init(ctx); AddonStore.add("https://x.example/manifest.json")
        Secure.keyProvider = { null }
        assertEquals(listOf(AddonStore.DEFAULT_URL), AddonStore.saved().map { it.url })
    }

    @Test fun pairingParsesTheCodeAndThePollAnswer() {
        val s = Pairing.parseStart("""{"ok":true,"deviceCode":"d-1","userCode":"ABC-123","verificationUrl":"x"}""")!!
        assertEquals("ABC-123", s.userCode); assertEquals("mylistsaddon.com/link?code=ABC-123", Pairing.linkUrl(s))
        assertNull(Pairing.parseStart("""{"ok":false}"""))
        assertNull("still waiting", Pairing.parsePoll("""{"ok":true,"status":"pending"}"""))
        assertEquals("https://mylistsaddon.com/alex/tv/manifest.json", Pairing.parsePoll("""{"ok":true,"status":"authenticated","manifestUrl":"https://mylistsaddon.com/alex/tv/manifest.json"}"""))
        assertEquals("alex", Pairing.userOf("https://mylistsaddon.com/alex/tv/manifest.json"))
    }

    @Test fun signingInSwapsTheDefaultAddonForTheOwnersLinkAndSigningOutPutsItBack() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        Secure.keyProvider = { null }
        AddonStore.init(ctx); AddonStore.reset(); Prefs.accountLink = ""
        AddonStore.add("https://stream.example/manifest.json")
        Pairing.signIn("https://mylistsaddon.com/alex/tv/manifest.json")
        var urls = AddonStore.saved().map { it.url }
        assertFalse(urls.contains(AddonStore.DEFAULT_URL)); assertTrue(urls.contains("https://mylistsaddon.com/alex/tv/manifest.json")); assertTrue(urls.contains("https://stream.example/manifest.json"))
        Pairing.signOut()
        urls = AddonStore.saved().map { it.url }
        assertTrue(urls.contains(AddonStore.DEFAULT_URL)); assertFalse(urls.any { it.contains("/alex/") }); assertTrue(urls.contains("https://stream.example/manifest.json"))
        assertEquals("", Prefs.accountLink)
    }

    @Test fun skipTimesComeFromIntroDbForSeriesEpisodesOnly() {
        assertEquals("https://api.introdb.app/segments?imdb_id=tt0944947&season=1&episode=2", Segments.url("tt0944947:1:2"))
        assertNull("a movie has no episode", Segments.url("tt0133093")); assertNull(Segments.url("live"))
        val segs = Segments.parse("""{"intro":{"start_ms":6000,"end_ms":105000},"recap":null,"outro":{"start_ms":3252000,"end_ms":3320000}}""")
        assertEquals(listOf("intro", "credits"), segs.map { it.kind })
        assertNull(Segments.at(segs, 3000)); assertEquals("intro", Segments.at(segs, 6000)!!.kind)
        assertEquals("intro", Segments.at(segs, 103_000)!!.kind); assertNull("not in the last second", Segments.at(segs, 104_500))
        assertEquals("credits", Segments.at(segs, 3_300_000)!!.kind)
        assertTrue(Segments.parse("""{"intro":{"start_ms":null,"end_ms":null}}""").isEmpty())
        assertEquals("a null start means the very beginning", 0L, Segments.parse("""{"intro":{"end_ms":40000}}""").single().startMs)
    }
}
