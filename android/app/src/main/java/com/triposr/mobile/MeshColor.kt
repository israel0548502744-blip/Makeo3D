package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Paints a shape-only mesh (TripoSG) with the colors of the picture it was made from.
 * The picture is laid onto the mesh from the camera's side ([FRONT]): the object's box in the
 * picture is matched to the mesh's box seen from there. Vertices the camera sees take the pixel
 * in front of them; hidden ones (the back, under the arms) take the pixel at the same place,
 * like a print going through, or the pixel of an optional back picture (mirrored).
 */
object MeshColor {
    /** The view the input picture corresponds to: the camera sits on this side, Y is up.
     *  Checked with convert/sg_eval.py axes (silhouette of the shape against the picture). */
    const val FRONT = "+Z"

    private class Picture(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val rgb = IntArray(w * h)
        var x0 = 0; var y0 = 0; var x1 = w - 1; var y1 = h - 1

        init {
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            val opaque = BooleanArray(w * h) { Color.alpha(px[it]) > 127 }
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) if (opaque[y * w + x]) {
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            if (maxX >= 0) { x0 = minX; y0 = minY; x1 = maxX; y1 = maxY }
            for (i in px.indices) rgb[i] = px[i]
            // Spread the object's colors a few pixels outward, so samples at the edge never take
            // the (removed) background.
            var known = opaque
            repeat(12) {
                val next = known.copyOf()
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    if (known[i]) continue
                    val n = when {
                        x > 0 && known[i - 1] -> i - 1
                        x < w - 1 && known[i + 1] -> i + 1
                        y > 0 && known[i - w] -> i - w
                        y < h - 1 && known[i + w] -> i + w
                        else -> -1
                    }
                    if (n >= 0) { rgb[i] = rgb[n]; next[i] = true }
                }
                known = next
            }
        }

        /** Color at (u, v) in 0..1 across the object's box, into out[at..at+2]. */
        fun sample(u: Float, v: Float, out: FloatArray, at: Int) {
            val x = (x0 + u.coerceIn(0f, 1f) * (x1 - x0)).toInt().coerceIn(0, w - 1)
            val y = (y0 + v.coerceIn(0f, 1f) * (y1 - y0)).toInt().coerceIn(0, h - 1)
            val c = rgb[y * w + x]
            out[at] = Color.red(c) / 255f; out[at + 1] = Color.green(c) / 255f; out[at + 2] = Color.blue(c) / 255f
        }
    }

    /** Per-vertex RGB (0..1) for [mesh]; [back] is an optional picture of the other side. */
    fun paint(mesh: Mesh, front: Bitmap, back: Bitmap? = null): FloatArray {
        val n = mesh.vertexCount
        val p = mesh.positions
        // Camera axes: right (r), up (Y), toward the camera (d).
        val (rAxis, rSign, dAxis, dSign) = when (FRONT) {
            "-Z" -> listOf(0, -1, 2, -1)
            "+X" -> listOf(2, -1, 0, 1)
            "-X" -> listOf(2, 1, 0, -1)
            else -> listOf(0, 1, 2, 1)
        }
        val su = FloatArray(n) { p[it * 3 + rAxis] * rSign }
        val sv = FloatArray(n) { -p[it * 3 + 1] }
        val depth = FloatArray(n) { p[it * 3 + dAxis] * dSign }
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        var minD = Float.MAX_VALUE; var maxD = -Float.MAX_VALUE
        for (i in 0 until n) {
            minU = minOf(minU, su[i]); maxU = maxOf(maxU, su[i]); minV = minOf(minV, sv[i]); maxV = maxOf(maxV, sv[i])
            minD = minOf(minD, depth[i]); maxD = maxOf(maxD, depth[i])
        }
        val spanU = (maxU - minU).coerceAtLeast(1e-6f); val spanV = (maxV - minV).coerceAtLeast(1e-6f)
        for (i in 0 until n) { su[i] = (su[i] - minU) / spanU; sv[i] = (sv[i] - minV) / spanV }

        // Depth buffer seen from the camera: the nearest surface in each cell.
        val g = 512
        val zbuf = FloatArray(g * g) { -Float.MAX_VALUE }
        val idx = mesh.indices
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            val ax = su[a] * (g - 1); val ay = sv[a] * (g - 1)
            val bx = su[b] * (g - 1); val by = sv[b] * (g - 1)
            val cx = su[c] * (g - 1); val cy = sv[c] * (g - 1)
            val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            val lx = minOf(ax, bx, cx).toInt().coerceAtLeast(0); val hx = maxOf(ax, bx, cx).toInt().coerceAtMost(g - 1)
            val ly = minOf(ay, by, cy).toInt().coerceAtLeast(0); val hy = maxOf(ay, by, cy).toInt().coerceAtMost(g - 1)
            if (area == 0f) {
                // Edge-on triangle: its corners still block the cells they fall in.
                for (v in intArrayOf(a, b, c)) {
                    val k = (sv[v] * (g - 1)).toInt() * g + (su[v] * (g - 1)).toInt()
                    if (depth[v] > zbuf[k]) zbuf[k] = depth[v]
                }
                continue
            }
            for (y in ly..hy) for (x in lx..hx) {
                val px = x + 0.5f; val py = y + 0.5f
                val w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area
                val w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area
                val w2 = 1f - w0 - w1
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val z = w0 * depth[a] + w1 * depth[b] + w2 * depth[c]
                val k = y * g + x
                if (z > zbuf[k]) zbuf[k] = z
            }
        }

        val frontPic = Picture(front)
        val backPic = back?.let { Picture(it) }
        val eps = (maxD - minD) * 0.02f
        val out = FloatArray(n * 3)
        var seen = 0
        for (i in 0 until n) {
            // The nearest surface around the vertex (its own cell and the neighbours).
            val cx = (su[i] * (g - 1)).toInt(); val cy = (sv[i] * (g - 1)).toInt()
            var near = -Float.MAX_VALUE
            for (dy in -1..1) for (dx in -1..1) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until g && y in 0 until g) near = maxOf(near, zbuf[y * g + x])
            }
            val visible = depth[i] >= near - eps
            if (visible) seen++
            when {
                visible || backPic == null -> frontPic.sample(su[i], sv[i], out, i * 3)
                else -> backPic.sample(1f - su[i], sv[i], out, i * 3)  // the back picture is mirrored
            }
        }
        CrashLog.log("Mesh colors from the picture (front $FRONT): ${seen * 100 / n.coerceAtLeast(1)}% of vertices seen" +
            if (backPic != null) ", back picture for the rest" else "")
        return out
    }
}
