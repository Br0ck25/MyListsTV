package tv.myliststv.launcher.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import tv.myliststv.launcher.data.Prefs
import tv.myliststv.launcher.data.Profiles
import android.widget.ImageView
import tv.myliststv.launcher.ui.Avatars
import tv.myliststv.launcher.ui.Sheet
import tv.myliststv.launcher.ui.Ui

/**
 * "Who's watching?": the profiles as round avatars across the middle of a whole screen. OK picks one and hands its id back to Home
 * (which does the switching); hold OK on one to edit or remove it; the last circle opens the Create profile screen. Back leaves without a change.
 */
class ProfilesActivity : Activity() {
    private lateinit var row: LinearLayout
    /** True once the profile in use was removed here: Home must reload even though the choice is not a new one. */
    private var reload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(View(c).apply { background = Ui.wash() }, FrameLayout.LayoutParams(-1, -1))
        val page = Ui.vbox(c).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(Ui.dp(c, 48), Ui.dp(c, 64), Ui.dp(c, 48), Ui.dp(c, 32)) }
        page.addView(Ui.text(c, "Who's watching?", 34f, Ui.TEXT, true), Ui.wrap())
        page.addView(Ui.text(c, "Select a profile to continue", 16f, Ui.DIM), Ui.wrap().apply { topMargin = Ui.dp(c, 8) })
        row = Ui.hbox(c, 28).apply { gravity = Gravity.CENTER; clipChildren = false }
        page.addView(HorizontalScrollView(c).apply { isFillViewport = true; isHorizontalScrollBarEnabled = false; clipChildren = false; addView(row, FrameLayout.LayoutParams(-1, -1)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(Ui.text(c, "Hold OK on a profile to edit or remove it", 14f, Ui.DIM), Ui.wrap())
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        build(Profiles.activeId)
    }

    /** Draws the circles. [focusId] is the profile to put focus on (the profile in use when it is gone). */
    private fun build(focusId: String?) {
        val c = this
        row.removeAllViews()
        var focus: View? = null
        Profiles.all().forEach { p ->
            val pic = Avatars.drawable(p.avatar)
            val face: View = if (pic != null) ImageView(c).apply { setImageDrawable(pic) }
                else Ui.text(c, p.name.take(1).uppercase(), 34f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.oval(Ui.avatarColor(p.id)) }
            val sub = if (p.id == Profiles.activeId) "In use" else if (p.kids) "Kids" else null
            val tile = tile(face, p.name, sub, sub == "In use", { done(p.id) }, { manage(p) })
            if (p.id == focusId) focus = tile
            row.addView(tile.parent as View)
        }
        val plus = Ui.text(c, "+", 34f, Ui.DIM, true).apply { gravity = Gravity.CENTER; background = Ui.oval(Ui.FILL) }
        val add = tile(plus, "Add profile", null, false, { addMenu() }, null)
        row.addView(add.parent as View)
        (focus ?: add).requestFocus()
    }

    /** One avatar: a round face with a focus ring, its name under it and an optional small line. Returns the focusable circle. */
    private fun tile(face: View, name: String, sub: String?, subBrand: Boolean, onClick: () -> Unit, onLong: (() -> Unit)?): View {
        val c = this
        val size = 104
        val ring = FrameLayout(c).apply {
            isFocusable = true; isClickable = true; foreground = Ui.ovalFocusBg(c)
            addView(face, FrameLayout.LayoutParams(Ui.dp(c, size - 12), Ui.dp(c, size - 12), Gravity.CENTER))
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        val label = Ui.text(c, name, 16f, Ui.DIM, true, 1)
        ring.setOnFocusChangeListener { _, f -> label.setTextColor(if (f) Ui.TEXT else Ui.DIM) }
        Ui.focusScale(ring, Prefs.lite(), 1.1f)
        val col = Ui.vbox(c).apply { gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false }
        col.addView(ring, LinearLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size)))
        col.addView(label, Ui.wrap().apply { topMargin = Ui.dp(c, 10) })
        // Every circle has the line under its name (empty for most), so the circles all sit at the same height.
        col.addView(Ui.text(c, sub ?: " ", 12f, if (subBrand) Ui.BRAND else Ui.DIM, true), Ui.wrap().apply { topMargin = Ui.dp(c, 2) })
        return ring
    }

    /** Hands the choice back to Home and leaves. */
    private fun done(id: String) { setResult(RESULT_OK, Intent().putExtra("id", id).putExtra("force", reload)); finish() }

    private fun manage(p: Profiles.P) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Edit name and picture" to { edit(p.id) }
        if (Profiles.all().size > 1) items += "Remove" to { remove(p) }
        Sheet.menu(this, p.name, null, items)
    }

    private fun edit(id: String) = startActivityForResult(Intent(this, ProfileEditActivity::class.java).putExtra(ProfileEditActivity.EXTRA_ID, id), EDIT)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT && resultCode == RESULT_OK) build(data?.getStringExtra("id"))
    }

    private fun remove(p: Profiles.P) {
        Sheet.confirm(this, "Remove ${p.name}?", "Its addons, Library and history go with it. This cannot be undone.", "Remove", "Keep") {
            val wasActive = p.id == Profiles.activeId
            Profiles.remove(p.id)
            if (wasActive) { Profiles.switchTo(this, Profiles.activeId); reload = true }
            build(Profiles.activeId)
        }
    }

    /** A whole Create profile screen (name, kids, pictures), not a pop-up. */
    private fun addMenu() = startActivityForResult(Intent(this, ProfileEditActivity::class.java), EDIT)

    private companion object { const val EDIT = 21 }

    @Deprecated("Back leaves; if the profile in use was removed, Home still has to reload.")
    override fun onBackPressed() { if (reload) setResult(RESULT_OK, Intent().putExtra("id", Profiles.activeId).putExtra("force", true)); super.onBackPressed() }
}
