package tv.myliststv.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** A small QR code maker for short links (byte mode, error correction L, versions 1 to 5, so up to 106 bytes). The platform has none. */
object Qr {
    private val DATA = intArrayOf(19, 34, 55, 80, 108)      // data codewords, versions 1..5 at level L (one block each)
    private val EC = intArrayOf(7, 10, 15, 20, 26)

    /** The code as rows of modules, true = dark. Throws if [text] is too long. */
    fun matrix(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val v = (1..5).firstOrNull { bytes.size <= DATA[it - 1] - 2 } ?: throw IllegalArgumentException("too long for a QR code")
        val size = 17 + 4 * v
        val data = codewords(bytes, DATA[v - 1])
        val all = data + remainder(data, divisor(EC[v - 1]))
        val fn = Array(size) { BooleanArray(size) }
        val base = Array(size) { BooleanArray(size) }
        fun set(x: Int, y: Int, dark: Boolean) { base[y][x] = dark; fn[y][x] = true }
        for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) for (dy in -4..4) for (dx in -4..4) {
            val x = cx + dx; val y = cy + dy
            if (x in 0 until size && y in 0 until size) { val d = maxOf(Math.abs(dx), Math.abs(dy)); set(x, y, d != 2 && d != 4) }
        }
        if (v >= 2) for (dy in -2..2) for (dx in -2..2) set(size - 7 + dx, size - 7 + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        for (i in 0 until 9) { fn[8][i] = true; fn[i][8] = true }              // format areas, filled below
        for (i in 0 until 8) { fn[8][size - 1 - i] = true; fn[size - 1 - i][8] = true }
        set(8, size - 8, true)
        // Data bits, in the zigzag the standard describes.
        var i = 0; var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j; val y = if (((right + 1) and 2) == 0) size - 1 - vert else vert
                if (!fn[y][x] && i < all.size * 8) { base[y][x] = ((all[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0; i++ }
            }
            right -= 2
        }
        var best: Array<BooleanArray>? = null; var bestScore = Int.MAX_VALUE
        for (mask in 0..7) {
            val m = Array(size) { base[it].clone() }
            for (y in 0 until size) for (x in 0 until size) if (!fn[y][x] && masked(mask, x, y)) m[y][x] = !m[y][x]
            val fmt = format(mask)
            for (k in 0..5) m[k][8] = bit(fmt, k)
            m[7][8] = bit(fmt, 6); m[8][8] = bit(fmt, 7); m[8][7] = bit(fmt, 8)
            for (k in 9..14) m[8][14 - k] = bit(fmt, k)
            for (k in 0..7) m[8][size - 1 - k] = bit(fmt, k)
            for (k in 8..14) m[size - 15 + k][8] = bit(fmt, k)
            val s = penalty(m)
            if (s < bestScore) { bestScore = s; best = m }
        }
        return best!!
    }

    /** Draws the code black on white with the quiet zone the standard asks for, [px] pixels square. */
    fun bitmap(text: String, px: Int): Bitmap {
        val m = matrix(text); val n = m.size + 8
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Ui.QR_LIGHT)
        val p = Paint().apply { color = Ui.QR_DARK; isAntiAlias = false }
        for (y in m.indices) for (x in m.indices) if (m[y][x]) c.drawRect((x + 4) * px / n.toFloat(), (y + 4) * px / n.toFloat(), (x + 5) * px / n.toFloat(), (y + 5) * px / n.toFloat(), p)
        return bmp
    }

    private fun bit(v: Int, i: Int) = ((v ushr i) and 1) != 0

    private fun masked(mask: Int, x: Int, y: Int) = when (mask) {
        0 -> (x + y) % 2 == 0; 1 -> y % 2 == 0; 2 -> x % 3 == 0; 3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0; 5 -> x * y % 2 + x * y % 3 == 0; 6 -> (x * y % 2 + x * y % 3) % 2 == 0
        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
    }

    /** The 15 format bits for error correction L and [mask]. */
    private fun format(mask: Int): Int {
        val d = (1 shl 3) or mask
        var r = d shl 10
        for (i in 14 downTo 10) if (bit(r, i)) r = r xor (0x537 shl (i - 10))
        return ((d shl 10) or r) xor 0x5412
    }

    private fun codewords(bytes: ByteArray, count: Int): ByteArray {
        val bits = ArrayList<Boolean>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += bit(v, i) }
        put(4, 4); put(bytes.size, 8); bytes.forEach { put(it.toInt() and 0xFF, 8) }
        put(0, minOf(4, count * 8 - bits.size))
        while (bits.size % 8 != 0) bits += false
        var pad = 0xEC
        while (bits.size < count * 8) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
        return ByteArray(count) { i -> (0 until 8).fold(0) { a, k -> (a shl 1) or if (bits[i * 8 + k]) 1 else 0 }.toByte() }
    }

    private fun mul(x: Int, y: Int): Int { var z = 0; for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((y ushr i) and 1) * x) }; return z and 0xFF }

    private fun divisor(degree: Int): IntArray {
        val r = IntArray(degree); r[degree - 1] = 1; var root = 1
        repeat(degree) { for (j in r.indices) { r[j] = mul(r[j], root); if (j + 1 < r.size) r[j] = r[j] xor r[j + 1] }; root = mul(root, 2) }
        return r
    }

    private fun remainder(data: ByteArray, div: IntArray): ByteArray {
        val r = IntArray(div.size)
        for (b in data) { val f = (b.toInt() and 0xFF) xor r[0]; for (i in 0 until r.size - 1) r[i] = r[i + 1]; r[r.size - 1] = 0; for (i in r.indices) r[i] = r[i] xor mul(div[i], f) }
        return ByteArray(r.size) { r[it].toByte() }
    }

    /** A simple badness score (long runs, 2x2 blocks, dark share) to pick the mask that scans best. */
    private fun penalty(m: Array<BooleanArray>): Int {
        val n = m.size; var s = 0; var dark = 0
        for (a in 0 until n) for (dir in 0..1) {
            var run = 1
            for (b in 1 until n) {
                val cur = if (dir == 0) m[a][b] else m[b][a]; val prev = if (dir == 0) m[a][b - 1] else m[b - 1][a]
                if (cur == prev) { run++; if (run == 5) s += 3 else if (run > 5) s++ } else run = 1
            }
        }
        for (y in 0 until n - 1) for (x in 0 until n - 1) if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) s += 3
        for (row in m) for (c in row) if (c) dark++
        return s + Math.abs(dark * 100 / (n * n) - 50) / 5 * 10
    }
}
