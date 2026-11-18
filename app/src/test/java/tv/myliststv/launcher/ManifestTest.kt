package tv.myliststv.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every activity and service named in the manifest must exist, so moving a class cannot break the app only on a real TV. */
class ManifestTest {
    @Test fun everyManifestClassExists() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CLAUDE.md").exists() }
        val xml = File(root, "app/src/main/AndroidManifest.xml").readText()
        val missing = Regex("""android:name="(\.[\w.]+)"""").findAll(xml).map { "tv.myliststv.launcher" + it.groupValues[1] }
            .filter { try { Class.forName(it); false } catch (_: ClassNotFoundException) { true } }.toList()
        assertEquals("classes named in the manifest that do not exist", emptyList<String>(), missing)
    }
}
