package tv.myliststv.launcher

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.myliststv.launcher.data.Profiles
import tv.myliststv.launcher.screens.ProfilesActivity

/** The "who is watching" screen: a whole screen of round avatars instead of a pop-up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = App::class)
class ProfilesScreenTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> { out += v; if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out); return out }
    private fun texts(a: android.app.Activity) = views(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    private fun click(a: android.app.Activity, label: String) {
        var v: View? = views(a.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == label }
        while (v != null && !v.isClickable) v = v.parent as? View
        v!!.performClick(); idle()
    }

    @Test fun showsEveryProfileAndAnAddCircle() {
        Profiles.add("Jenn", false); Profiles.add("Kid", true)
        val a = Robolectric.buildActivity(ProfilesActivity::class.java).setup().get(); idle()
        val t = texts(a)
        assertTrue("Who's watching?" in t && "Select a profile to continue" in t)
        assertTrue(listOf("Main", "Jenn", "Kid", "Add profile", "In use", "Kids").all { it in t })
    }

    @Test fun choosingAProfileHandsItsIdBackAndLeaves() {
        Profiles.add("Jenn", false)
        val a = Robolectric.buildActivity(ProfilesActivity::class.java).setup().get(); idle()
        click(a, "J")
        assertTrue(a.isFinishing)
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(a).resultCode)
        assertEquals(Profiles.all().first { it.name == "Jenn" }.id, shadowOf(a).resultIntent.getStringExtra("id"))
    }

    @Test fun addProfileOpensTheCreateScreenNotAPopUp() {
        val a = Robolectric.buildActivity(ProfilesActivity::class.java).setup().get(); idle()
        click(a, "+")
        assertNull("no dialog", ShadowDialog.getLatestDialog())
        val next = shadowOf(a).nextStartedActivity
        assertEquals(tv.myliststv.launcher.screens.ProfileEditActivity::class.java.name, next.component!!.className)
    }

    @Test fun createScreenMakesAProfileWithThePickedPicture() {
        val a = Robolectric.buildActivity(tv.myliststv.launcher.screens.ProfileEditActivity::class.java).setup().get(); idle()
        assertTrue("Create profile" in texts(a) && "Choose a picture" in texts(a) && "Animals" in texts(a) && "Characters" in texts(a))
        val before = Profiles.all().size
        click(a, "Create")   // no name typed: the picture's name is used
        assertEquals(before + 1, Profiles.all().size)
        val p = Profiles.all().last()
        assertNotNull(tv.myliststv.launcher.ui.Avatars.byId(p.avatar))
        assertEquals(tv.myliststv.launcher.ui.Avatars.byId(p.avatar)!!.name, p.name)
        assertTrue(a.isFinishing)
    }

    @Test fun everyPictureHasAUniqueIdAndDraws() {
        val all = tv.myliststv.launcher.ui.Avatars.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        all.forEach { tv.myliststv.launcher.ui.Avatars.drawable(it.id)!!.apply { setBounds(0, 0, 96, 96); draw(android.graphics.Canvas(bmp)) } }
    }
}
