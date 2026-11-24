package tv.myliststv.launcher.ui

import tv.myliststv.launcher.*

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

/** The apps of the Apps panel: a banner (or the icon on a dark tile) with the app's name under it. */
class AppGridAdapter(
    private val act: Activity,
    private val apps: List<AppEntry>,
    private val onOpen: (AppEntry) -> Unit,
    private val onMenu: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppGridAdapter.H>() {
    class H(val box: LinearLayout, val tile: FrameLayout, val img: ImageView, val name: android.widget.TextView) : RecyclerView.ViewHolder(box)

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): H {
        val c = act
        val box = Ui.vbox(c).apply { layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5)) } }
        val tile = FrameLayout(c).apply {
            background = Ui.rounded(c, Ui.BG, Ui.R_CARD); clipToOutline = true; isFocusable = true; isClickable = true
            foreground = Ui.focusBg(c, Ui.R_CARD, Ui.CLEAR)
        }
        val img = ImageView(c)
        tile.addView(img, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val name = Ui.text(c, "", 13f, Ui.TEXT2, false, 1).apply { setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), 0) }
        box.addView(tile, LinearLayout.LayoutParams(-1, Ui.dp(c, 84)))
        box.addView(name, Ui.matchWrap())
        return H(box, tile, img, name)
    }

    override fun onBindViewHolder(h: H, position: Int) {
        val a = apps[position]
        h.name.text = a.label
        val (art, banner) = AppRepository.look(act.packageManager, a.component)
        h.img.setImageDrawable(art)
        h.img.scaleType = if (banner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        h.img.setPadding(if (banner) 0 else Ui.dp(act, 20), if (banner) 0 else Ui.dp(act, 12), if (banner) 0 else Ui.dp(act, 20), if (banner) 0 else Ui.dp(act, 12))
        h.tile.setOnClickListener { onOpen(a) }
        h.tile.setOnLongClickListener { onMenu(a); true }
    }
}
