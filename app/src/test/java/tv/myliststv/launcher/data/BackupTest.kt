package tv.myliststv.launcher.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.App

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class BackupTest {
    @Test fun recoveryCodeHasTheShownShape() {
        val c = Backup.newCode()
        assertTrue(c, Backup.isCode(c))
        assertNotEquals(c, Backup.newCode())
    }

    @Test fun onlyTheRightCodeOpensTheBackup() {
        val code = Backup.newCode()
        val blob = Backup.encrypt("""{"secret":"debrid-key-123"}""", code)
        assertFalse("the blob must not contain the plain text", blob.contains("debrid"))
        assertEquals("""{"secret":"debrid-key-123"}""", Backup.decrypt(blob, code))
        assertEquals("a typed code in lower case with spaces still opens it", """{"secret":"debrid-key-123"}""", Backup.decrypt(blob, code.lowercase().replace("-", " - ")))
        assertNull(Backup.decrypt(blob, Backup.newCode()))
        assertNull("a changed blob is refused", Backup.decrypt(blob.dropLast(6) + "AAAAAA", code))
    }

    @Test fun serverIdDoesNotRevealTheCodeAndIsStable() {
        val code = "ABCD-EFGH-JKLM-NPQR"
        assertEquals(32, Backup.idOf(code).length)
        assertEquals(Backup.idOf(code), Backup.idOf(" abcd-efgh-jklm-npqr "))
        assertFalse(Backup.idOf(code).contains("abcd", ignoreCase = true))
    }

    @Test fun backupRestoresAddonsPlaylistsProfilesAndSettings() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        AddonStore.add("https://stream.example/manifest.json")
        LiveStore.add("https://iptv.example/list.m3u")
        Profiles.add("Kids room", true)
        Prefs.maxResolution = 720
        val json = Backup.snapshot()
        assertTrue(json.contains("stream.example"))
        assertFalse("the recovery code never goes into the backup", json.contains("backupCode"))
        // Wipe, then restore.
        AddonStore.reset(); LiveStore.restore(emptyList()); Prefs.maxResolution = 0
        val summary = Backup.apply(ctx, json)
        assertNotNull(summary)
        assertTrue(AddonStore.saved().any { it.url == "https://stream.example/manifest.json" })
        assertEquals(listOf("https://iptv.example/list.m3u"), LiveStore.playlists())
        assertTrue(Profiles.all().any { it.name == "Kids room" && it.kids })
        assertEquals(720, Prefs.maxResolution)
    }

    @Test fun garbageIsNotABackup() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        assertNull(Backup.apply(ctx, "not json"))
        assertNull(Backup.apply(ctx, """{"v":2}"""))
    }
}
