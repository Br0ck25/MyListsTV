package tv.myliststv.launcher.ui

import android.app.Activity
import android.net.Uri
import tv.myliststv.launcher.data.Http
import tv.myliststv.launcher.data.PhoneServer
import tv.myliststv.launcher.data.Pictures
import java.io.File

/** For a TV with no file chooser: the phone opens a page on the TV's address (shown as a code), picks a picture, and it lands in [target]. */
object PhonePicture {
    const val PORT = 8766

    fun receive(act: Activity, title: String, target: File, w: Int, h: Int, png: Boolean, done: () -> Unit) {
        lateinit var sheet: Sheet
        val server = PhoneServer(PORT, onPicture = { bytes ->
            val tmp = File.createTempFile("sent", ".img", act.cacheDir)
            val ok = try { tmp.writeBytes(bytes); Pictures.save(act, Uri.fromFile(tmp), target, w, h, png) } finally { tmp.delete() }
            if (ok) Http.post { sheet.dismiss(); done() }
            if (ok) "Done. Look at the TV." else "That file is not a picture."
        }) { _, r -> r("Nothing to add here.") }
        val addr = server.address()?.plus("/picture")
        if (addr == null) { Pop.toast(act, "Connect the TV to Wi-Fi first.", true); return }
        server.start()
        sheet = Sheet.message(act, title, "On your phone (on the same Wi-Fi) scan the code, or open $addr, then choose a picture.", Sheet.Act("Cancel"), image = Qr.bitmap(addr, 400))
        sheet.onDismiss { server.stop() }
    }
}
