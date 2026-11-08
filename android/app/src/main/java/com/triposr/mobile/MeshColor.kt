package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Paints a shape-only mesh (TripoSG) with the colors of the picture it was made from.
 * The picture is laid onto the mesh from the camera's side: the object's box in the picture is
 * matched to the mesh's box seen from there. The model does not keep the camera's direction
 * (convert/sg_eval.py axes: a mug, a house and a figure face +Z, a dinosaur in profile faces +X),
 * so the side is found per mesh: the view around the up axis whose outline best matches the
 * picture's outline. Vertices the camera sees take the pixel
 * in front of them; hidden ones (the back, under the arms) take the pixel at the same place,
 * like a print going through, or the pixel of an optional back picture (mirrored).
 */
object MeshColor {
    /** Views tried around the up axis (degrees from +Z toward +X). */
    private const val VIEWS = 12
    /** +Z (the usual side) wins unless another view's outline matches clearly better: a front
     *  and a back outline are mirror images, so near-symmetric objects can't tell them apart. */
    private const val KEEP_FRONT = 0.01f

    private class Picture(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val rgb = IntArray(w * h)
        var x0 = 0; var y0 = 0; var x1 = w - 1; var y1 = h - 1
        val opaque: BooleanArray

        init {
            val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            opaque = BooleanArray(w * h) { Color.alpha(px[it]) > 127 }
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

        /** The object's outline on a g x g grid stretched over its box. */
        fun outline(g: Int) = BooleanArray(g * g) {
            val x = (x0 + ((it % g) + 0.5f) / g * (x1 - x0)).toInt().coerceIn(0, w - 1)
            val y = (y0 + ((it / g) + 0.5f) / g * (y1 - y0)).toInt().coerceIn(0, h - 1)
            opaque[y * w + x]
        }

        /** Color at (u, v) in 0..1 across the object's box, into out[at..at+2]. */
        fun sample(u: Float, v: Float, out: FloatArray, at: Int) {
            val x = (x0 + u.coerceIn(0f, 1f) * (x1 - x0)).toInt().coerceIn(0, w - 1)
            val y = (y0 + v.coerceIn(0f, 1f) * (y1 - y0)).toInt().coerceIn(0, h - 1)
            val c = rgb[y * w + x]
            out[at] = Color.red(c) / 255f; out[at + 1] = Color.green(c) / 255f; out[at + 2] = Color.blue(c) / 255f
        }
    }

    /** The mesh seen from azimuth [deg]: u, v in 0..1 over its box (v down) and depth toward the camera. */
    private class View(mesh: Mesh, val deg: Int) {
        val n = mesh.vertexCount
        val su = FloatArray(n); val sv = FloatArray(n); val depth = FloatArray(n)
        var minD = Float.MAX_VALUE; var maxD = -Float.MAX_VALUE

        init {
            val p = mesh.positions
            val a = Math.toRadians(deg.toDouble())
            // Camera direction d = (sin a, 0, cos a), right r = (cos a, 0, -sin a): 0 = +Z, 90 = +X.
            val s = Math.sin(a).toFloat(); val c = Math.cos(a).toFloat()
            var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
            for (i in 0 until n) {
                val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
                su[i] = c * x - s * z; sv[i] = -y; depth[i] = s * x + c * z
                minU = minOf(minU, su[i]); maxU = maxOf(maxU, su[i]); minV = minOf(minV, sv[i]); maxV = maxOf(maxV, sv[i])
                minD = minOf(minD, depth[i]); maxD = maxOf(maxD, depth[i])
            }
            val spanU = (maxU - minU).coerceAtLeast(1e-6f); val spanV = (maxV - minV).coerceAtLeast(1e-6f)
            for (i in 0 until n) { su[i] = (su[i] - minU) / spanU; sv[i] = (sv[i] - minV) / spanV }
        }

        /** Depth buffer on a g x g grid: the nearest surface in each cell (-MAX where empty). */
        fun zbuffer(mesh: Mesh, g: Int): FloatArray {
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
                // Each corner blocks its own cell (edge-on and sub-cell triangles).
                for (v in intArrayOf(a, b, c)) {
                    val k = (sv[v] * (g - 1)).toInt() * g + (su[v] * (g - 1)).toInt()
                    if (depth[v] > zbuf[k]) zbuf[k] = depth[v]
                }
                if (area == 0f) continue
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
            return zbuf
        }
    }

    /** Per-vertex RGB (0..1) for [mesh]; [back] is an optional picture of the other side. */
    fun paint(mesh: Mesh, front: Bitmap, back: Bitmap? = null): FloatArray {
        val n = mesh.vertexCount
        val frontPic = Picture(front)

        // Which side the picture was taken from: the outline (both stretched over their boxes,
        // as the colors are laid on) that overlaps the picture's outline best.
        val sg = 128
        val target = frontPic.outline(sg)
        val scores = FloatArray(VIEWS) { k ->
            val sil = View(mesh, k * 360 / VIEWS).zbuffer(mesh, sg)
            var inter = 0; var union = 0
            for (i in target.indices) {
                val m = sil[i] > -Float.MAX_VALUE
                if (m && target[i]) inter++
                if (m || target[i]) union++
            }
            inter.toFloat() / union.coerceAtLeast(1)
        }
        var best = 0
        for (k in 1 until VIEWS) if (scores[k] > scores[best]) best = k
        if (scores[0] >= scores[best] - KEEP_FRONT) best = 0
        val view = View(mesh, best * 360 / VIEWS)
        val su = view.su; val sv = view.sv; val depth = view.depth
        val g = 512
        val zbuf = view.zbuffer(mesh, g)

        val backPic = back?.let { Picture(it) }
        val eps = (view.maxD - view.minD) * 0.02f
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
        CrashLog.log("Mesh colors from the picture (camera at ${view.deg}°, outline match " +
            scores.joinToString(" ") { "%.2f".format(it) } + "): " +
            "${seen * 100 / n.coerceAtLeast(1)}% of vertices seen" +
            if (backPic != null) ", back picture for the rest" else "")
        return out
    }
}
