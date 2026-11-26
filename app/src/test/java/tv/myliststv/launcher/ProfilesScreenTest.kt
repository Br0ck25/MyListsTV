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

    @Test fun addProfileOffersProfileOrKids() {
        val a = Robolectric.buildActivity(ProfilesActivity::class.java).setup().get(); idle()
        click(a, "+")
        val d = ShadowDialog.getLatestDialog()
        val t = views(d.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Profile" in t && "Kids profile" in t)
    }
}
