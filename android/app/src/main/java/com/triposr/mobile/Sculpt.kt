package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Shapes to start sculpting from: a cube, a ball or a cylinder, or a drawing made with a finger
 * and given thickness. They are made of many small, even triangles, so the touch tools
 * (pull, raise, cut...) have something to take hold of everywhere.
 */
object Sculpt {
    enum class Shape { CUBE, BALL, CYLINDER }

    /** Unit ball: an eight-sided shape whose triangles are split in four [levels] times, pushed out to the sphere. */
    private fun ball(levels: Int): Pair<FloatArray, IntArray> {
        val p = FloatList()
        for (x in floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f)) p.add(x)
        var count = 6
        var t = intArrayOf(0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 4, 2, 1, 3, 4, 1, 5, 3, 1, 2, 5)
        repeat(levels) {
            val mid = HashMap<Long, Int>()
            fun m(a: Int, b: Int): Int = mid.getOrPut((minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()) {
                val x = p[a * 3] + p[b * 3]; val y = p[a * 3 + 1] + p[b * 3 + 1]; val z = p[a * 3 + 2] + p[b * 3 + 2]
                val l = sqrt(x * x + y * y + z * z)
                p.add(x / l); p.add(y / l); p.add(z / l)
                count++
            }
            val out = IntArray(t.size * 4)
            var k = 0
            for (i in 0 until t.size / 3) {
                val a = t[i * 3]; val b = t[i * 3 + 1]; val c = t[i * 3 + 2]
                val ab = m(a, b); val bc = m(b, c); val ca = m(c, a)
                for (v in intArrayOf(a, ab, ca, ab, b, bc, ca, bc, c, ab, bc, ca)) out[k++] = v
            }
            t = out
        }
        return p.toArray() to t
    }

    /** A cube, ball or cylinder, [sizeMm] across, about 32,000 triangles. */
    fun shape(s: Shape, sizeMm: Float): Pair<Mesh, Bitmap> {
        val (p, t) = ball(6)
        for (v in 0 until p.size / 3) {
            val x = p[v * 3]; val y = p[v * 3 + 1]; val z = p[v * 3 + 2]
            // The ball is pressed out onto the surface of the shape, along the same line from the middle.
            val k = when (s) {
                Shape.BALL -> 1f
                Shape.CUBE -> 1f / max(abs(x), max(abs(y), abs(z)))
                Shape.CYLINDER -> 1f / max(sqrt(x * x + z * z), abs(y))
            }
            p[v * 3] = x * k * 0.8f; p[v * 3 + 1] = y * k * 0.8f; p[v * 3 + 2] = z * k * 0.8f
        }
        val mesh = Mesh(p, MeshTools.normals(p, t), FloatArray(p.size) { 0.82f }, t).also { it.sizeMm = sizeMm }
        return mesh to icon(s)
    }

    /**
     * A finger drawing given thickness: [mask] is what was drawn, [sizeMm] its longest side and
     * [thickMm] how thick it stands. Lines that close a shape can be [filled] in.
     */
    fun drawing(mask: Templates.Mask, sizeMm: Float, thickMm: Float, filled: Boolean): Pair<Mesh, Bitmap> {
        var m = mask.cropped()
        if (m.count() < 20) throw IllegalStateException(tr("צייר משהו קודם"))
        if (filled) m = Templates.fillHoles(m)
        m = m.padded(2)
        val cell = sizeMm / maxOf(m.w, m.h)
        val top = FloatArray(m.w * m.h) { if (m.cells[it]) thickMm else 0f }
        val mesh = Heightfield.shaped(top, m.w, m.h, cell)
        val px = IntArray(m.w * m.h) { if (m.cells[it]) Color.rgb(124, 92, 255) else Color.rgb(236, 236, 240) }
        return mesh to Bitmap.createBitmap(px, m.w, m.h, Bitmap.Config.ARGB_8888)
    }

    /** Strokes ([strokes] of x, y points in a [w]×[h] area, [widths] in the same units) as a mask [samples] across. */
    fun strokes(strokes: List<FloatArray>, widths: List<Float>, w: Float, h: Float, samples: Int = 220): Templates.Mask {
        val s = samples / max(w, h)
        val bw = (w * s).toInt().coerceAtLeast(8); val bh = (h * s).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint().apply {
            isAntiAlias = false; color = Color.BLACK; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        for ((i, pts) in strokes.withIndex()) {
            paint.strokeWidth = widths[i] * s
            if (pts.size == 2) {
                paint.style = Paint.Style.FILL
                c.drawCircle(pts[0] * s, pts[1] * s, widths[i] * s / 2, paint)
                paint.style = Paint.Style.STROKE
                continue
            }
            val path = android.graphics.Path()
            path.moveTo(pts[0] * s, pts[1] * s)
            for (k in 1 until pts.size / 2) path.lineTo(pts[k * 2] * s, pts[k * 2 + 1] * s)
            c.drawPath(path, paint)
        }
        val px = IntArray(bw * bh).also { bmp.getPixels(it, 0, bw, 0, 0, bw, bh) }
        return Templates.Mask(bw, bh, BooleanArray(bw * bh) { Color.alpha(px[it]) > 127 })
    }

    /** The gallery picture of a shape: its outline seen from the front. */
    private fun icon(s: Shape): Bitmap {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(236, 236, 240))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(124, 92, 255) }
        when (s) {
            Shape.CUBE -> c.drawRect(24f, 24f, 104f, 104f, p)
            Shape.BALL -> c.drawCircle(64f, 64f, 42f, p)
            Shape.CYLINDER -> {
                c.drawRect(30f, 30f, 98f, 98f, p)
                p.color = Color.rgb(160, 140, 255)
                c.drawOval(30f, 18f, 98f, 42f, p)
            }
        }
        return bmp
    }
}
