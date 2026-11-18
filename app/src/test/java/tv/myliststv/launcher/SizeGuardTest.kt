package tv.myliststv.launcher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CLAUDE.md rule 12: no source file over 2,000 lines. Split a big file by job before it gets there. */
class SizeGuardTest {
    @Test fun noFileIsOver2000Lines() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CLAUDE.md").exists() }
        val skip = setOf("build", ".gradle", ".git", "node_modules")
        val big = root.walkTopDown().onEnter { it.name !in skip }
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "yml", "yaml") }
            .map { it to it.useLines { l -> l.count() } }.filter { it.second > 2000 }.toList()
        assertTrue("Files over 2,000 lines: " + big.joinToString { "${it.first.name} (${it.second})" }, big.isEmpty())
    }
}
