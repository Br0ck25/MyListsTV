package tv.myliststv.launcher.data

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Single, double and hold presses of the buttons the owner mapped (see [ButtonMap]). One implementation for both places keys arrive:
 * the Home button fallback service (every app) and the launcher's own window. [rules] says which rules this listener answers to.
 * A mapped button is consumed whatever the press, so it never also does its normal job.
 */
class ButtonGestures(private val rules: () -> List<ButtonMap.Rule>, private val run: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var longDone = false
    private var waitingSecond: Runnable? = null

    /** True when the key belongs to a mapped button and was used. */
    fun onKey(e: KeyEvent): Boolean {
        val mine = rules().filter { it.key == e.keyCode }
        if (mine.isEmpty()) return false
        val single = mine.firstOrNull { it.press == "single" }; val double = mine.firstOrNull { it.press == "double" }; val long = mine.firstOrNull { it.press == "long" }
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) longDone = false
            if (long != null && e.repeatCount >= 1 && !longDone) { longDone = true; run(long.action) }
            return true
        }
        if (longDone) { longDone = false; return true }
        val waiting = waitingSecond
        if (double == null) single?.let { run(it.action) }
        else if (waiting != null) { handler.removeCallbacks(waiting); waitingSecond = null; run(double.action) }
        else waitingSecond = Runnable { waitingSecond = null; single?.let { run(it.action) } }.also { handler.postDelayed(it, 300) }
        return true
    }
}
