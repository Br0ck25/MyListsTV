package tv.myliststv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Phase 0 spike: prove the app can take the Home button and list apps, using plain Views only.
 * No Compose, no WebView, no AppCompat. See docs/PLAN.md.
 */
class MainActivity : Activity() {

    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(48), dp(32), dp(48), 0)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 28f
        })
        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 6)
            clipToPadding = false
            setPadding(0, dp(24), 0, dp(24))
            setHasFixedSize(true)
            itemAnimator = null
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        list.adapter = AppAdapter(this, AppRepository.load(this)) { entry -> launch(entry) }
    }

    // Home pressed while we are already on top: go back to the first item.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) list.scrollToPosition(0)
    }

    private fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = entry.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            addCategory(if (entry.sideloaded) Intent.CATEGORY_LAUNCHER else Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class AppAdapter(
    private val context: Context,
    private val items: List<AppEntry>,
    private val onClick: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    // Small byte-bounded-ish cache: 64 icons. Icons load only when a tile binds.
    private val icons = LruCache<String, Drawable>(64)
    private val pm: PackageManager = context.packageManager

    class Holder(val root: LinearLayout, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val d = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding((8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt())
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (132 * d).toInt())
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                    setColor(Color.parseColor("#26FFFFFF")); setStroke((3 * d).toInt(), Color.WHITE); cornerRadius = 12 * d
                })
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt())
        }
        val label = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(icon); root.addView(label)
        return Holder(root, icon, label)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val e = items[position]
        h.label.text = e.label
        val key = e.component.flattenToShortString()
        val icon = icons.get(key) ?: try {
            pm.getActivityIcon(e.component).also { icons.put(key, it) }
        } catch (_: Exception) { null }
        h.icon.setImageDrawable(icon)
        h.root.setOnClickListener { onClick(e) }
    }

    override fun getItemCount() = items.size
}
