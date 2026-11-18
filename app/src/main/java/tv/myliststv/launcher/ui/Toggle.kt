package tv.myliststv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** An on/off switch: a pill with a white knob, brand blue when on. Only draws; the row it sits in handles OK. */
class Toggle(c: Context, var on: Boolean) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(Ui.dp(context, 40), Ui.dp(context, 22))

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        paint.color = if (on) Ui.BRAND else Ui.TOGGLE_OFF
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        paint.color = Ui.TEXT
        canvas.drawCircle(if (on) width - r else r, r, r - Ui.dpf(context, 2f), paint)
    }
}
