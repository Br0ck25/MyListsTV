package tv.myliststv.launcher.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Opens the device's own picture chooser (Files, Photos) and hands back what was picked. The activity passes its `onActivityResult` on to [result]. */
class PicturePicker(private val act: Activity, private val code: Int) {
    private var then: ((Uri) -> Unit)? = null

    fun pick(onPicked: (Uri) -> Unit) {
        then = onPicked
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try { act.startActivityForResult(open, code) } catch (_: Exception) {
            try { act.startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), null), code) }
            catch (_: Exception) { then = null; Pop.toast(act, "This TV has no app for choosing pictures. Install a file manager from the Play Store.", true) }
        }
    }

    fun result(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != code) return
        val cb = then; then = null
        data?.data?.takeIf { resultCode == Activity.RESULT_OK }?.let { cb?.invoke(it) }
    }
}
