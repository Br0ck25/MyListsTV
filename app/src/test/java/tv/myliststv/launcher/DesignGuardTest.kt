package tv.myliststv.launcher

import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.myliststv.launcher.ui.Card
import tv.myliststv.launcher.ui.CardKind
import java.io.File

/**
 * Keeps the look in one place (docs/DESIGN.md): colours, radii and text sizes come from `ui/Ui.kt`, never from a screen,
 * so a new screen cannot introduce a one-off colour or size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class DesignGuardTest {
    private val scale = setOf(11, 12, 13, 14, 15, 16, 20, 22, 24, 26, 30, 34)
    private val root = File("src/main/java/tv/myliststv/launcher")
    private fun screens() = root.walkTopDown().filter { it.extension == "kt" && it.name != "Ui.kt" }.toList()

    private fun offenders(rule: Regex) = screens().flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (rule.containsMatchIn(l.substringBefore("//"))) "${f.name}:${i + 1}: ${l.trim()}" else null } }

    @Test fun sourcesAreFound() = assertTrue("run from the app module", screens().size > 20)

    @Test fun noScreenWritesAColour() = assertEquals("use a Ui token", emptyList<String>(), offenders(Regex("""0x[0-9a-fA-F]{6,8}|Color\.(WHITE|BLACK|RED|GREEN|BLUE|GRAY|TRANSPARENT|parseColor|argb|rgb)""")))

    @Test fun noScreenWritesARadiusOrRawTextSize() {
        assertEquals("use Ui.rounded / Ui.rowStyle", emptyList<String>(), offenders(Regex("""cornerRadius""")))
        assertEquals("use Ui.text / Ui.size", emptyList<String>(), offenders(Regex("""setTextSize\(|\btextSize\s*=""")))
    }

    @Test fun radiiAreTokens() = assertEquals("pass a Ui.R_* radius", emptyList<String>(), offenders(Regex("""Ui\.rounded\([^,]+,[^,]+,(?!\s*Ui\.R_)""")))

    @Test fun textSizesAreOnTheScale() {
        val sizes = Regex("""(?:Ui\.text\([^,]+,[^,]+,\s*|Ui\.size\([^,]+,\s*)(\d+)f""")
        val bad = screens().flatMap { f -> f.readLines().flatMap { l -> sizes.findAll(l).map { it.groupValues[1].toInt() }.filter { it !in scale }.map { "${f.name}: ${it}f in ${l.trim()}" }.toList() } }
        assertEquals("sizes must be one of $scale", emptyList<String>(), bad)
    }

    /** Every menu, question, message and text entry is a [tv.myliststv.launcher.ui.Sheet]; the right-hand panels are SidePanel. Nothing builds its own pop-up. */
    @Test fun popUpsAreSheetsOrSidePanels() {
        val own = setOf("Sheet.kt", "SidePanel.kt")
        val bad = screens().filter { it.name !in own }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (Regex("""AlertDialog\b|android\.app\.Dialog\b|\bDialog\(|PopupMenu|ListPopupWindow|Toast\.makeText""").containsMatchIn(l.substringBefore("//")) && !l.trimStart().startsWith("import ")) "${f.name}:${i + 1}: ${l.trim()}" else null }
        }
        assertEquals("use Sheet (menu, message, ask, confirm, busy) or Pop.toast, see docs/DESIGN.md section 9", emptyList<String>(), bad.filter { !it.startsWith("Pop.kt") })
    }

    @Test fun everyCardKindIsRoundedAndClipped() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        for (kind in CardKind.values()) {
            val c = Card(ctx, kind)
            assertTrue("$kind clips its picture to the corners", c.clipToOutline)
            assertTrue("$kind has rounded corners", (c.background as GradientDrawable).cornerRadius > 0f)
        }
    }
}
