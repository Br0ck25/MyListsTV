package tv.myliststv.launcher.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The pictures a profile can wear. Each one is drawn here from a few shapes (nothing to download, always sharp at any size),
 * on a 100 x 100 canvas, inside a round frame.
 */
object Avatars {
    data class Avatar(val id: String, val name: String, val group: String, val bgHue: Float, val art: Art.() -> Unit)

    const val ANIMALS = "Animals"
    const val CHARACTERS = "Characters"
    val groups = listOf(ANIMALS, CHARACTERS)

    fun byId(id: String?): Avatar? = all.firstOrNull { it.id == id }

    /** A round picture for [id], or null when the profile has none (it then shows its first letter). */
    fun drawable(id: String?): Drawable? = byId(id)?.let { AvatarDrawable(it) }

    /** Nothing but a stable pick: the same seed always gives the same picture, so a new profile starts with a face. */
    fun pick(seed: Int): Avatar = all[(seed and 0x7fffffff) % all.size]

    /** The shapes an avatar is made of. Everything is in the 100 x 100 space. */
    class Art(val canvas: Canvas) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        fun hsv(h: Float, s: Float, v: Float) = Color.HSVToColor(floatArrayOf(h, s, v))
        val white = hsv(0f, 0f, 1f)
        val ink = hsv(240f, 0.3f, 0.16f)

        fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) { p.style = Paint.Style.FILL; p.color = color; r.set(cx - rx, cy - ry, cx + rx, cy + ry); canvas.drawOval(r, p) }
        fun dot(cx: Float, cy: Float, rad: Float, color: Int) = oval(cx, cy, rad, rad, color)
        fun box(l: Float, t: Float, rt: Float, b: Float, round: Float, color: Int) { p.style = Paint.Style.FILL; p.color = color; r.set(l, t, rt, b); canvas.drawRoundRect(r, round, round, p) }
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
            p.style = Paint.Style.FILL; p.color = color
            canvas.drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }, p)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) { p.style = Paint.Style.STROKE; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.color = color; canvas.drawLine(x1, y1, x2, y2, p) }
        /** A smile: the lower half of an ellipse, as a line. */
        fun smile(cx: Float, cy: Float, rx: Float, ry: Float, w: Float, color: Int = ink) {
            p.style = Paint.Style.STROKE; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.color = color
            r.set(cx - rx, cy - ry, cx + rx, cy + ry); canvas.drawArc(r, 20f, 140f, false, p)
        }
        /** Two eyes (white with a pupil), [gap] apart around the middle. */
        fun eyes(y: Float, gap: Float, rad: Float, pupil: Float = rad * 0.55f) {
            for (s in listOf(-1f, 1f)) { dot(50 + s * gap, y, rad, white); dot(50 + s * gap, y, pupil, ink) }
        }
        fun beady(y: Float, gap: Float, rad: Float) { for (s in listOf(-1f, 1f)) dot(50 + s * gap, y, rad, ink) }
        /** The shoulders at the bottom: clothes. */
        fun shoulders(color: Int) = oval(50f, 104f, 36f, 22f, color)
    }

    private fun a(id: String, name: String, group: String, hue: Float, art: Art.() -> Unit) = Avatar(id, name, group, hue, art)

    val all: List<Avatar> = listOf(
        a("bear", "Bear", ANIMALS, 335f) {
            shoulders(hsv(245f, 0.5f, 0.35f))
            dot(27f, 31f, 11f, hsv(25f, 0.6f, 0.5f)); dot(73f, 31f, 11f, hsv(25f, 0.6f, 0.5f)); dot(27f, 31f, 5.5f, hsv(20f, 0.4f, 0.8f)); dot(73f, 31f, 5.5f, hsv(20f, 0.4f, 0.8f))
            dot(50f, 55f, 28f, hsv(25f, 0.6f, 0.58f)); oval(50f, 66f, 13f, 10f, hsv(30f, 0.3f, 0.88f))
            oval(50f, 61f, 5f, 3.5f, ink); line(50f, 63f, 50f, 68f, 1.6f, ink); smile(50f, 66f, 6f, 4f, 1.6f); beady(48f, 11f, 3.2f)
        },
        a("cat", "Cat", ANIMALS, 85f) {
            shoulders(hsv(280f, 0.5f, 0.5f))
            tri(24f, 18f, 40f, 34f, 22f, 46f, hsv(215f, 0.1f, 0.5f)); tri(76f, 18f, 60f, 34f, 78f, 46f, hsv(215f, 0.1f, 0.5f))
            tri(27f, 26f, 36f, 36f, 26f, 42f, hsv(350f, 0.35f, 0.9f)); tri(73f, 26f, 64f, 36f, 74f, 42f, hsv(350f, 0.35f, 0.9f))
            dot(50f, 56f, 28f, hsv(215f, 0.1f, 0.5f)); oval(50f, 66f, 14f, 10f, hsv(0f, 0f, 0.92f))
            for (s in listOf(-1f, 1f)) { oval(50 + s * 11, 50f, 5.5f, 4.2f, hsv(55f, 0.8f, 0.95f)); oval(50 + s * 11, 50f, 1.6f, 4f, ink) }
            tri(46.5f, 60f, 53.5f, 60f, 50f, 64f, hsv(350f, 0.45f, 0.9f)); smile(45f, 66f, 5f, 3f, 1.4f); smile(55f, 66f, 5f, 3f, 1.4f)
            for (s in listOf(-1f, 1f)) { line(50 + s * 18, 63f, 50 + s * 34, 60f, 1.2f, white); line(50 + s * 18, 67f, 50 + s * 34, 69f, 1.2f, white) }
        },
        a("fox", "Fox", ANIMALS, 165f) {
            shoulders(hsv(190f, 0.7f, 0.5f))
            tri(22f, 14f, 42f, 34f, 20f, 48f, hsv(20f, 0.85f, 0.95f)); tri(78f, 14f, 58f, 34f, 80f, 48f, hsv(20f, 0.85f, 0.95f))
            tri(24f, 20f, 34f, 32f, 24f, 38f, ink); tri(76f, 20f, 66f, 32f, 76f, 38f, ink)
            dot(50f, 54f, 28f, hsv(20f, 0.85f, 0.95f))
            tri(22f, 58f, 50f, 84f, 40f, 64f, white); tri(78f, 58f, 50f, 84f, 60f, 64f, white); oval(50f, 69f, 9f, 8f, white)
            oval(50f, 78f, 4.5f, 3.2f, ink); beady(52f, 12f, 3.2f)
        },
        a("owl", "Owl", ANIMALS, 175f) {
            shoulders(hsv(20f, 0.4f, 0.3f))
            tri(24f, 18f, 42f, 32f, 24f, 46f, hsv(28f, 0.55f, 0.45f)); tri(76f, 18f, 58f, 32f, 76f, 46f, hsv(28f, 0.55f, 0.45f))
            dot(50f, 54f, 30f, hsv(28f, 0.55f, 0.5f)); oval(50f, 72f, 15f, 11f, hsv(30f, 0.25f, 0.85f))
            dot(36f, 50f, 13f, hsv(35f, 0.2f, 0.92f)); dot(64f, 50f, 13f, hsv(35f, 0.2f, 0.92f))
            dot(37f, 50f, 8f, hsv(40f, 0.9f, 0.95f)); dot(63f, 50f, 8f, hsv(40f, 0.9f, 0.95f)); dot(37f, 50f, 4.5f, ink); dot(63f, 50f, 4.5f, ink)
            tri(45f, 58f, 55f, 58f, 50f, 70f, hsv(30f, 0.9f, 0.95f))
        },
        a("panda", "Panda", ANIMALS, 25f) {
            shoulders(hsv(0f, 0f, 0.25f))
            dot(27f, 29f, 11f, ink); dot(73f, 29f, 11f, ink)
            dot(50f, 54f, 29f, hsv(0f, 0f, 0.97f))
            oval(38f, 50f, 8f, 10.5f, ink); oval(62f, 50f, 8f, 10.5f, ink); dot(38f, 49f, 3f, white); dot(62f, 49f, 3f, white)
            oval(50f, 62f, 5f, 3.5f, ink); smile(50f, 64f, 6f, 5f, 1.6f)
        },
        a("bunny", "Bunny", ANIMALS, 15f) {
            shoulders(hsv(210f, 0.5f, 0.6f))
            oval(36f, 24f, 8f, 22f, hsv(30f, 0.05f, 0.95f)); oval(64f, 24f, 8f, 22f, hsv(30f, 0.05f, 0.95f)); oval(36f, 26f, 3.5f, 15f, hsv(350f, 0.3f, 0.95f)); oval(64f, 26f, 3.5f, 15f, hsv(350f, 0.3f, 0.95f))
            dot(50f, 60f, 26f, hsv(30f, 0.05f, 0.95f)); beady(56f, 11f, 3.2f)
            oval(50f, 65f, 4f, 3f, hsv(350f, 0.45f, 0.9f)); smile(50f, 67f, 5f, 4f, 1.5f)
            for (s in listOf(-1f, 1f)) { line(50 + s * 14, 64f, 50 + s * 28, 62f, 1.1f, hsv(0f, 0f, 0.7f)); line(50 + s * 14, 68f, 50 + s * 28, 70f, 1.1f, hsv(0f, 0f, 0.7f)) }
        },
        a("lion", "Lion", ANIMALS, 60f) {
            shoulders(hsv(215f, 0.75f, 0.7f))
            dot(50f, 52f, 38f, hsv(22f, 0.75f, 0.6f)); dot(50f, 54f, 26f, hsv(42f, 0.7f, 0.97f))
            dot(30f, 34f, 7f, hsv(30f, 0.6f, 0.9f)); dot(70f, 34f, 7f, hsv(30f, 0.6f, 0.9f))
            oval(50f, 65f, 12f, 9f, hsv(45f, 0.25f, 0.98f)); oval(50f, 60f, 5f, 3.5f, ink); smile(50f, 65f, 7f, 4f, 1.6f); beady(50f, 10f, 3f)
        },
        a("frog", "Frog", ANIMALS, 130f) {
            shoulders(hsv(45f, 0.8f, 0.9f))
            oval(50f, 62f, 32f, 26f, hsv(110f, 0.65f, 0.75f)); oval(50f, 74f, 20f, 10f, hsv(90f, 0.4f, 0.92f))
            dot(33f, 37f, 12f, hsv(110f, 0.65f, 0.75f)); dot(67f, 37f, 12f, hsv(110f, 0.65f, 0.75f)); dot(33f, 37f, 8f, white); dot(67f, 37f, 8f, white); dot(34f, 38f, 4.2f, ink); dot(66f, 38f, 4.2f, ink)
            smile(50f, 62f, 20f, 11f, 2f); dot(44f, 56f, 1.2f, ink); dot(56f, 56f, 1.2f, ink)
        },
        a("penguin", "Penguin", ANIMALS, 200f) {
            oval(50f, 60f, 32f, 38f, hsv(220f, 0.3f, 0.22f)); oval(50f, 68f, 22f, 28f, white); oval(50f, 40f, 24f, 18f, hsv(220f, 0.3f, 0.22f))
            oval(50f, 46f, 18f, 12f, white); beady(42f, 9f, 3f); dot(41f, 41f, 1f, white); dot(59f, 41f, 1f, white)
            tri(43f, 50f, 57f, 50f, 50f, 60f, hsv(32f, 0.9f, 1f)); oval(30f, 92f, 10f, 4f, hsv(32f, 0.9f, 1f)); oval(70f, 92f, 10f, 4f, hsv(32f, 0.9f, 1f))
        },
        a("dog", "Dog", ANIMALS, 45f) {
            shoulders(hsv(5f, 0.6f, 0.75f))
            oval(24f, 50f, 10f, 20f, hsv(25f, 0.6f, 0.4f)); oval(76f, 50f, 10f, 20f, hsv(25f, 0.6f, 0.4f))
            dot(50f, 54f, 27f, hsv(33f, 0.5f, 0.85f)); oval(50f, 66f, 14f, 11f, hsv(35f, 0.2f, 0.97f))
            oval(50f, 60f, 5.5f, 4f, ink); line(50f, 62f, 50f, 68f, 1.5f, ink); smile(50f, 66f, 7f, 5f, 1.6f); oval(50f, 74f, 4f, 5f, hsv(350f, 0.55f, 0.92f)); beady(48f, 11f, 3.2f)
        },
        a("robot", "Robot", CHARACTERS, 210f) {
            shoulders(hsv(215f, 0.2f, 0.5f))
            line(50f, 14f, 50f, 26f, 2.5f, hsv(215f, 0.15f, 0.7f)); dot(50f, 13f, 4f, hsv(0f, 0.8f, 0.95f))
            box(22f, 26f, 78f, 78f, 12f, hsv(215f, 0.12f, 0.82f)); box(28f, 38f, 72f, 60f, 8f, hsv(220f, 0.4f, 0.2f))
            dot(40f, 49f, 5f, hsv(175f, 0.8f, 1f)); dot(60f, 49f, 5f, hsv(175f, 0.8f, 1f))
            box(36f, 66f, 64f, 72f, 3f, hsv(220f, 0.3f, 0.3f)); for (x in listOf(42f, 50f, 58f)) line(x, 66f, x, 72f, 1.2f, hsv(215f, 0.12f, 0.82f))
            box(16f, 42f, 22f, 60f, 3f, hsv(215f, 0.15f, 0.65f)); box(78f, 42f, 84f, 60f, 3f, hsv(215f, 0.15f, 0.65f))
        },
        a("alien", "Alien", CHARACTERS, 270f) {
            shoulders(hsv(300f, 0.4f, 0.4f))
            oval(50f, 52f, 29f, 34f, hsv(105f, 0.55f, 0.85f))
            for (s in listOf(-1f, 1f)) { oval(50 + s * 14, 54f, 9f, 13f, ink); dot(50 + s * 12, 49f, 2.4f, white) }
            smile(50f, 72f, 7f, 3.5f, 1.5f, hsv(105f, 0.7f, 0.3f)); dot(46f, 66f, 1f, hsv(105f, 0.7f, 0.3f)); dot(54f, 66f, 1f, hsv(105f, 0.7f, 0.3f))
        },
        a("ghost", "Ghost", CHARACTERS, 250f) {
            val body = hsv(210f, 0.08f, 0.98f)
            oval(50f, 46f, 28f, 28f, body)
            box(22f, 46f, 78f, 90f, 0f, body)
            for (i in 0..3) dot(29f + i * 14f, 90f, 7f, body)
            oval(40f, 46f, 4.5f, 6.5f, ink); oval(60f, 46f, 4.5f, 6.5f, ink); oval(50f, 62f, 4f, 5f, ink)
            dot(30f, 56f, 4f, hsv(350f, 0.25f, 1f)); dot(70f, 56f, 4f, hsv(350f, 0.25f, 1f))
        },
        a("astro", "Astronaut", CHARACTERS, 225f) {
            shoulders(hsv(0f, 0f, 0.93f)); box(40f, 88f, 60f, 96f, 3f, hsv(0f, 0.7f, 0.9f))
            dot(50f, 50f, 32f, hsv(0f, 0f, 0.95f)); oval(50f, 52f, 24f, 20f, hsv(225f, 0.6f, 0.22f))
            oval(42f, 45f, 8f, 4f, hsv(200f, 0.4f, 0.65f)); dot(56f, 56f, 2f, hsv(200f, 0.4f, 0.5f))
            dot(78f, 40f, 3f, hsv(0f, 0.7f, 0.9f))
        },
        a("monster", "Monster", CHARACTERS, 35f) {
            shoulders(hsv(265f, 0.6f, 0.45f))
            tri(28f, 12f, 40f, 30f, 24f, 34f, hsv(48f, 0.2f, 0.95f)); tri(72f, 12f, 60f, 30f, 76f, 34f, hsv(48f, 0.2f, 0.95f))
            dot(50f, 56f, 32f, hsv(280f, 0.55f, 0.8f)); dot(50f, 46f, 14f, white); dot(50f, 46f, 7f, hsv(120f, 0.7f, 0.7f)); dot(50f, 46f, 3.5f, ink)
            box(33f, 66f, 67f, 78f, 6f, ink); for (x in listOf(38f, 47f, 56f)) tri(x, 66f, x + 8f, 66f, x + 4f, 73f, white)
        },
        a("ninja", "Ninja", CHARACTERS, 355f) {
            shoulders(hsv(240f, 0.2f, 0.2f))
            dot(50f, 52f, 30f, hsv(240f, 0.2f, 0.22f)); box(24f, 40f, 76f, 62f, 9f, hsv(28f, 0.35f, 0.95f))
            beady(51f, 11f, 4f); dot(39f, 50f, 1.3f, white); dot(61f, 50f, 1.3f, white)
            box(20f, 30f, 80f, 37f, 2f, hsv(0f, 0.85f, 0.85f)); tri(78f, 31f, 96f, 22f, 90f, 40f, hsv(0f, 0.85f, 0.85f))
        },
    )

    /** The round picture: the frame colour, then the art, all clipped to a circle. */
    class AvatarDrawable(private val a: Avatar) : Drawable() {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(floatArrayOf(a.bgHue, 0.55f, 0.95f)) }
        private val clip = Path().apply { addCircle(50f, 50f, 50f, Path.Direction.CW) }

        override fun draw(canvas: Canvas) {
            val b = bounds; if (b.isEmpty) return
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat()); canvas.scale(b.width() / 100f, b.height() / 100f)
            canvas.clipPath(clip); canvas.drawPaint(bg)
            Art(canvas).apply(a.art)
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { bg.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { bg.colorFilter = cf }
        @Deprecated("Required by Drawable") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
