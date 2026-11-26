package tv.myliststv.launcher.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tv.myliststv.launcher.data.Prefs
import tv.myliststv.launcher.data.Profiles
import tv.myliststv.launcher.ui.Avatars
import tv.myliststv.launcher.ui.Ui

/**
 * A whole screen for making a profile (or changing one): a big preview that follows what you pick, the name, a kids switch,
 * and every picture to choose from, grouped, with the focused one named. Create works even with no name typed (the picture's
 * name is used), and "Surprise me" picks a picture for you. Replaces the small pop-up the old Add profile used.
 */
class ProfileEditActivity : Activity() {
    companion object {
        const val EXTRA_ID = "id"        // present: change this profile; absent: make a new one
        const val EXTRA_KIDS = "kids"    // a new profile starts as a kids profile
    }

    private var editing: Profiles.P? = null
    private var chosen = Avatars.all.first().id
    private var kids = false
    private var group: String? = null

    private lateinit var preview: ImageView
    private lateinit var nameField: EditText
    private lateinit var kidsBtn: TextView
    private lateinit var heading: TextView
    private lateinit var focusedName: TextView
    private lateinit var chips: LinearLayout
    private lateinit var grid: LinearLayout
    private val checks = HashMap<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        editing = intent.getStringExtra(EXTRA_ID)?.let { id -> Profiles.all().firstOrNull { it.id == id } }
        kids = editing?.kids ?: intent.getBooleanExtra(EXTRA_KIDS, false)
        chosen = editing?.avatar?.takeIf { Avatars.byId(it) != null } ?: Avatars.pick(System.currentTimeMillis().toInt()).id

        val root = FrameLayout(c).apply { setBackgroundColor(Ui.BG) }
        root.addView(View(c).apply { background = Ui.wash() }, FrameLayout.LayoutParams(-1, -1))
        val page = Ui.hbox(c, 40).apply { setPadding(Ui.dp(c, 56), Ui.dp(c, 24), Ui.dp(c, 56), Ui.dp(c, 16)); gravity = Gravity.TOP }
        page.addView(left(), LinearLayout.LayoutParams(Ui.dp(c, 300), -1))
        page.addView(right(), LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        nameField.setText(editing?.name ?: ""); nameField.setSelection(nameField.text.length)
        showPicture(); showGroup(null)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        // The picture grid is where people start: focus the chosen one, so OK and Create are a short way off.
        checks[chosen]?.let { (it.parent as View).requestFocus() }
    }

    private fun left(): View {
        val c = this
        val col = Ui.vbox(c).apply { gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false }
        heading = Ui.text(c, if (editing == null) "Create profile" else "Edit profile", 30f, Ui.TEXT, true)
        col.addView(heading, Ui.wrap().apply { gravity = Gravity.START; bottomMargin = Ui.dp(c, 12) })
        preview = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        col.addView(preview, LinearLayout.LayoutParams(Ui.dp(c, 116), Ui.dp(c, 116)))
        focusedName = Ui.text(c, " ", 16f, Ui.DIM, true).apply { gravity = Gravity.CENTER }
        col.addView(focusedName, Ui.wrap().apply { topMargin = Ui.dp(c, 6); bottomMargin = Ui.dp(c, 10) })

        nameField = EditText(c).apply {
            hint = "Name"; setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); setSingleLine(true); Ui.size(this, 16f); gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12))
            background = Ui.rounded(c, Ui.BG, Ui.R_CARD, Ui.FILL_STRONG); foreground = Ui.focusBg(c, Ui.R_CARD, Ui.CLEAR)
            setOnEditorActionListener { v, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0); v.clearFocus(); true } else false }
        }
        col.addView(nameField, Ui.matchWrap())
        kidsBtn = Ui.button(c, "", false) { kids = !kids; showKids() }
        col.addView(kidsBtn, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
        col.addView(Ui.button(c, "Surprise me") { pick(Avatars.pick(System.nanoTime().toInt())) }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
        col.addView(Ui.button(c, if (editing == null) "Create" else "Save", primary = true) { save() }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 14) })
        col.addView(Ui.button(c, "Cancel") { finish() }, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })
        showKids()
        return col
    }

    private fun right(): View {
        val c = this
        val col = Ui.vbox(c).apply { clipChildren = false }
        col.addView(Ui.text(c, "Choose a picture", 20f, Ui.TEXT, true), Ui.wrap())
        chips = Ui.hbox(c, 10).apply { clipChildren = false }
        col.addView(chips, Ui.wrap().apply { topMargin = Ui.dp(c, 14); bottomMargin = Ui.dp(c, 8) })
        grid = Ui.vbox(c).apply { clipChildren = false; setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 28)) }
        col.addView(ScrollView(c).apply { isVerticalScrollBarEnabled = false; clipToPadding = false;   // clips at its own edge, so scrolled rows never draw over the chips
                 addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return col
    }

    private fun showKids() { kidsBtn.text = if (kids) "Kids profile: On" else "Kids profile: Off" }

    private fun showPicture() { preview.setImageDrawable(Avatars.drawable(chosen)); focusedName.text = Avatars.byId(chosen)?.name ?: " " }

    private fun pick(a: Avatars.Avatar) {
        chosen = a.id; showPicture()
        checks.forEach { (id, v) -> v.visibility = if (id == a.id) View.VISIBLE else View.GONE }
        checks[a.id]?.let { (it.parent as View).requestFocus() }
    }

    /** The filter chips ("All" and each group) and the picture grid under them. */
    private fun showGroup(g: String?) {
        val c = this
        group = g
        chips.removeAllViews()
        (listOf<String?>(null) + Avatars.groups).forEach { name ->
            val on = name == g
            chips.addView(Ui.button(c, name ?: "All", primary = on) { showGroup(name) }.apply { if (on) tag = "on" })
        }
        grid.removeAllViews(); checks.clear()
        val list = Avatars.all.filter { g == null || it.group == g }
        list.chunked(5).forEach { rowItems ->
            val row = Ui.hbox(c, 14).apply { clipChildren = false }
            rowItems.forEach { row.addView(tile(it)) }
            grid.addView(row, Ui.wrap().apply { bottomMargin = Ui.dp(c, 14) })
        }
    }

    private fun tile(a: Avatars.Avatar): View {
        val c = this
        val size = 84
        val frame = FrameLayout(c).apply {
            isFocusable = true; isClickable = true; foreground = Ui.ovalFocusBg(c)
            addView(ImageView(c).apply { setImageDrawable(Avatars.drawable(a.id)) }, FrameLayout.LayoutParams(Ui.dp(c, size - 10), Ui.dp(c, size - 10), Gravity.CENTER))
            setOnClickListener { pick(a) }
            setOnFocusChangeListener { _, f -> if (f) focusedName.text = a.name else focusedName.text = Avatars.byId(chosen)?.name ?: " " }
        }
        val tick = Ui.text(c, "✓", 13f, Ui.TEXT, true).apply { gravity = Gravity.CENTER; background = Ui.oval(Ui.BRAND_FILL); visibility = if (a.id == chosen) View.VISIBLE else View.GONE }
        frame.addView(tick, FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24), Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.dp(c, 4); bottomMargin = Ui.dp(c, 4) })
        checks[a.id] = tick
        Ui.focusScale(frame, Prefs.lite(), 1.1f)
        return frame.also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size)) }
    }

    private fun save() {
        val typed = nameField.text.toString().trim()
        val name = typed.ifEmpty { Avatars.byId(chosen)?.name ?: "Profile" }   // no name typed: use the picture's
        val id = editing?.let { Profiles.update(it.id, name, kids, chosen); it.id } ?: Profiles.add(name, kids, chosen)
        setResult(RESULT_OK, Intent().putExtra("id", id)); finish()
    }
}
