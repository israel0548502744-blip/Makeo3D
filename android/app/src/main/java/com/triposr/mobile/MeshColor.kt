package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Paints a shape-only mesh (TripoSG) with the colors of the picture it was made from.
 *
 * 1. Which camera took the picture. The model does not keep the camera's direction (a mug, a
 *    house and a figure face +Z, a dinosaur in profile faces +X), so it is searched: 12 directions
 *    around the up axis, then finer steps, a camera level or a little above, and how close it was
 *    (a phone close to the object sees its near parts bigger). The camera whose view of the model
 *    overlaps the picture's outline best wins. A camera from below is not tried: its outline is
 *    nearly the same as one from above, and guessing wrong colored the model worse than a level
 *    camera does (measured, MeshColorTest and convert/ lab notes in HANDOFF).
 * 2. Where each point lands in the picture: through that camera, the model's outline box laid
 *    on the picture's outline box, pixels blended.
 * 3. What is trusted. Only surface the camera sees face-on takes a pixel; the back, the sides
 *    seen edge-on and what hides under the arms take the color of the trusted surface next to
 *    them (or of an optional back picture).
 * 4. Sharpness. Besides one color per vertex, [bake] fills a texture: every triangle gets its own
 *    patch, sampled from the full-size picture, so faces, prints and writing stay sharp.
 */
object MeshColor {
    /** Directions tried around the up axis first (degrees from +Z toward +X). */
    private const val VIEWS = 12
    /** +Z (the usual side) wins unless another view's outline matches clearly better: a front
     *  and a back outline are mirror images, so near-symmetric objects can't tell them apart. */
    private const val KEEP_FRONT = 0.01f
    /** How much a surface must face the camera (cosine) for its pixel to be trusted. */
    private const val FACING = 0.35f
    /** Outline grid for the search and the bending. */
    private const val G = 128
    /** Depth grid for what the camera sees. */
    private const val Z = 1024
    /** The picture is used at most this size: far more detail than the model or texture holds. */
    private const val MAX_PICTURE = 2048

    // ------------------------------------------------------------------ the picture

    class Picture(src: Bitmap) {
        val w: Int
        val h: Int
        val rgb: IntArray
        val opaque: BooleanArray
        var x0 = 0; var y0 = 0; var x1 = 0; var y1 = 0

        init {
            val s = min(1f, MAX_PICTURE.toFloat() / max(src.width, src.height))
            val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true) else src
            w = bmp.width; h = bmp.height
            rgb = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
            opaque = BooleanArray(w * h) { Color.alpha(rgb[it]) > 127 }
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) if (opaque[y * w + x]) {
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            if (maxX >= 0) { x0 = minX; y0 = minY; x1 = maxX; y1 = maxY } else { x1 = w - 1; y1 = h - 1 }
            // Spread the object's colors outward, so samples at the edge never take the
            // (removed) background.
            var known = opaque
            repeat(16) {
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

        /** The object's outline on a g x g grid over its box (cell centres). */
        fun outline(g: Int) = BooleanArray(g * g) {
            val x = (x0 + ((it % g) + 0.5f) / g * (x1 - x0 + 1)).toInt().coerceIn(0, w - 1)
            val y = (y0 + ((it / g) + 0.5f) / g * (y1 - y0 + 1)).toInt().coerceIn(0, h - 1)
            opaque[y * w + x]
        }

        /** Bilinear color at (u, v), 0..1 across the object's box, into out[at..at+2]. */
        fun sample(u: Float, v: Float, out: FloatArray, at: Int) {
            val fx = (x0 + u.coerceIn(0f, 1f) * (x1 - x0 + 1) - 0.5f).coerceIn(0f, w - 1f)
            val fy = (y0 + v.coerceIn(0f, 1f) * (y1 - y0 + 1) - 0.5f).coerceIn(0f, h - 1f)
            val ix = fx.toInt().coerceAtMost(w - 2).coerceAtLeast(0); val iy = fy.toInt().coerceAtMost(h - 2).coerceAtLeast(0)
            val ax = (fx - ix).coerceIn(0f, 1f); val ay = (fy - iy).coerceIn(0f, 1f)
            val x2 = min(ix + 1, w - 1); val y2 = min(iy + 1, h - 1)
            val c00 = rgb[iy * w + ix]; val c10 = rgb[iy * w + x2]; val c01 = rgb[y2 * w + ix]; val c11 = rgb[y2 * w + x2]
            for (k in 0..2) {
                val sh = 16 - k * 8
                fun ch(c: Int) = ((c shr sh) and 0xFF) / 255f
                val top = ch(c00) + ax * (ch(c10) - ch(c00))
                val bot = ch(c01) + ax * (ch(c11) - ch(c01))
                out[at + k] = top + ay * (bot - top)
            }
        }
    }

    // ------------------------------------------------------------------ the camera

    /**
     * A camera at azimuth [az] (degrees from +Z toward +X) and elevation [el] (degrees, + = from
     * above), [dist] model units from the middle; 0 = far away (no perspective).
     */
    class Cam(val az: Float, val el: Float, val dist: Float = 0f) {
        private val sa = sin(Math.toRadians(az.toDouble())).toFloat(); private val ca = cos(Math.toRadians(az.toDouble())).toFloat()
        private val se = sin(Math.toRadians(el.toDouble())).toFloat(); private val ce = cos(Math.toRadians(el.toDouble())).toFloat()
        /** Toward the camera. */
        val dir = floatArrayOf(sa * ce, se, ca * ce)
        fun d(x: Float, y: Float, z: Float) = (sa * x + ca * z) * ce + y * se
        /** Screen position (u right, v down) into out[0], out[1]. */
        fun uv(x: Float, y: Float, z: Float, out: FloatArray) {
            val f = sa * x + ca * z
            var u = ca * x - sa * z; var v = -(y * ce - f * se)
            if (dist > 0f) { val k = dist / max(dist - (f * ce + y * se), 1e-3f); u *= k; v *= k }
            out[0] = u; out[1] = v
        }
        override fun toString() = "%.1f° around, %.0f° up, %s".format(az, el, if (dist > 0f) "%.1f away".format(dist) else "far")
    }

    /** The mesh seen by [cam]: u, v in 0..1 over its box (v down) and depth toward the camera. */
    internal class Frame(mesh: Mesh, val cam: Cam) {
        val n = mesh.vertexCount
        val su = FloatArray(n); val sv = FloatArray(n); val depth = FloatArray(n)
        var minU = Float.MAX_VALUE; var spanU = 1f; var minV = Float.MAX_VALUE; var spanV = 1f
        var minD = Float.MAX_VALUE; var maxD = -Float.MAX_VALUE

        private val t = FloatArray(2)

        init {
            val p = mesh.positions
            var maxU = -Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
            for (i in 0 until n) {
                val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
                cam.uv(x, y, z, t); su[i] = t[0]; sv[i] = t[1]; depth[i] = cam.d(x, y, z)
                minU = min(minU, su[i]); maxU = max(maxU, su[i]); minV = min(minV, sv[i]); maxV = max(maxV, sv[i])
                minD = min(minD, depth[i]); maxD = max(maxD, depth[i])
            }
            spanU = (maxU - minU).coerceAtLeast(1e-6f); spanV = (maxV - minV).coerceAtLeast(1e-6f)
            for (i in 0 until n) { su[i] = (su[i] - minU) / spanU; sv[i] = (sv[i] - minV) / spanV }
        }

        /** u, v in 0..1 over the box, into out[0], out[1] (not thread-safe: one frame per thread). */
        fun uv(x: Float, y: Float, z: Float, out: FloatArray) {
            cam.uv(x, y, z, out); out[0] = (out[0] - minU) / spanU; out[1] = (out[1] - minV) / spanV
        }

        /** The outline on a g x g grid (cell centres), from the vertices: cheap, for the search. */
        fun outline(g: Int): BooleanArray {
            val out = BooleanArray(g * g)
            for (i in 0 until n) {
                val x = (su[i] * g).toInt().coerceIn(0, g - 1); val y = (sv[i] * g).toInt().coerceIn(0, g - 1)
                out[y * g + x] = true
            }
            // Close the small gaps between vertex splats.
            val grown = out.copyOf()
            for (y in 0 until g) for (x in 0 until g) if (out[y * g + x]) {
                if (x + 1 < g) grown[y * g + x + 1] = true
                if (y + 1 < g) grown[(y + 1) * g + x] = true
                if (x + 1 < g && y + 1 < g) grown[(y + 1) * g + x + 1] = true
            }
            return grown
        }

        /** Depth buffer on a g x g grid: the nearest surface in each cell (-MAX where empty). */
        fun zbuffer(mesh: Mesh, g: Int): FloatArray {
            val zbuf = FloatArray(g * g) { -Float.MAX_VALUE }
            val idx = mesh.indices
            for (t in 0 until idx.size / 3) {
                val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
                val ax = su[a] * g; val ay = sv[a] * g
                val bx = su[b] * g; val by = sv[b] * g
                val cx = su[c] * g; val cy = sv[c] * g
                val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
                for (v in intArrayOf(a, b, c)) {
                    val k = (sv[v] * g).toInt().coerceIn(0, g - 1) * g + (su[v] * g).toInt().coerceIn(0, g - 1)
                    if (depth[v] > zbuf[k]) zbuf[k] = depth[v]
                }
                if (area == 0f) continue
                val lx = (min(ax, min(bx, cx)) - 0.5f).toInt().coerceAtLeast(0); val hx = (max(ax, max(bx, cx)) - 0.5f).toInt().coerceAtMost(g - 1)
                val ly = (min(ay, min(by, cy)) - 0.5f).toInt().coerceAtLeast(0); val hy = (max(ay, max(by, cy)) - 0.5f).toInt().coerceAtMost(g - 1)
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

    private fun iou(a: BooleanArray, b: BooleanArray): Float {
        var inter = 0; var union = 0
        for (i in a.indices) { if (a[i] && b[i]) inter++; if (a[i] || b[i]) union++ }
        return inter.toFloat() / union.coerceAtLeast(1)
    }

    // ------------------------------------------------------------------ putting it together

    /** Where the picture lands on the mesh: the camera, the bending and what the camera sees. */
    class Projection internal constructor(
        private val mesh: Mesh, val picture: Picture, private val back: Picture?,
        private val frame: Frame, private val zbuf: FloatArray,
    ) {
        val cam get() = frame.cam
        private val eps = (frame.maxD - frame.minD) * 0.01f
        private val m = FloatArray(2)

        /** Whether the camera sees the point ([x], [y], [z]) facing it ([nx], [ny], [nz]). */
        fun sees(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Boolean {
            val d = cam.dir
            if (nx * d[0] + ny * d[1] + nz * d[2] <= FACING) return false
            frame.uv(x, y, z, m)
            val cx = (m[0] * Z).toInt(); val cy = (m[1] * Z).toInt()
            var near = -Float.MAX_VALUE
            for (oy in -1..1) for (ox in -1..1) {
                val px = cx + ox; val py = cy + oy
                if (px in 0 until Z && py in 0 until Z) near = max(near, zbuf[py * Z + px])
            }
            return cam.d(x, y, z) >= near - eps
        }

        /** Whether the point is behind (hidden from the camera), for the back picture. */
        private fun hidden(x: Float, y: Float, z: Float): Boolean {
            frame.uv(x, y, z, m)
            val cx = (m[0] * Z).toInt().coerceIn(0, Z - 1); val cy = (m[1] * Z).toInt().coerceIn(0, Z - 1)
            return cam.d(x, y, z) < zbuf[cy * Z + cx] - eps
        }

        /** The picture's color at the point, into out[at..at+2]. */
        fun sample(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
            frame.uv(x, y, z, m)
            picture.sample(m[0], m[1], out, at)
        }

        /** The back picture's color for a hidden point (mirrored), or false when there is none. */
        fun sampleBack(x: Float, y: Float, z: Float, out: FloatArray, at: Int): Boolean {
            val b = back ?: return false
            if (!hidden(x, y, z)) return false
            frame.uv(x, y, z, m)
            b.sample(1f - m[0], m[1], out, at)
            return true
        }
    }

    /**
     * Finds the camera for [mesh] seen in [front]. [search] false keeps a level, far camera (only
     * the direction around the up axis is searched), as before.
     */
    fun project(mesh: Mesh, front: Bitmap, back: Bitmap? = null, search: Boolean = true): Projection {
        val pic = Picture(front)
        val target = pic.outline(G)
        // The outline needs only part of a dense mesh's vertices.
        val step = (mesh.vertexCount / 60_000).coerceAtLeast(1)
        val few = if (step == 1) mesh else Mesh(FloatArray(mesh.vertexCount / step * 3) { mesh.positions[(it / 3) * step * 3 + it % 3] },
            FloatArray(0), FloatArray(0), IntArray(0))
        val scores = HashMap<Triple<Float, Float, Float>, Float>()
        fun score(c: Cam) = scores.getOrPut(Triple(c.az, c.el, c.dist)) { iou(Frame(few, c).outline(G), target) }
        // A level, far camera wins unless another one matches clearly better.
        fun worth(c: Cam) = score(c) - if (c.el == 0f && c.dist == 0f) 0f else 0.01f

        var best = Cam(0f, 0f)
        for (k in 1 until VIEWS) { val c = Cam(k * 360f / VIEWS, 0f); if (score(c) > score(best)) best = c }
        if (search) {
            val around = setOf(best.az, 0f)
            for (az0 in around) for (daz in floatArrayOf(-15f, -7.5f, 0f, 7.5f, 15f))
                for (el in floatArrayOf(0f, 10f, 20f, 30f)) for (d in floatArrayOf(0f, 8f, 5f, 3.5f, 2.5f)) {
                    val c = Cam(az0 + daz, el, d)
                    if (worth(c) > worth(best)) best = c
                }
            // Finer, around the best.
            val b0 = best
            for (daz in floatArrayOf(-5f, 0f, 5f)) for (del in floatArrayOf(-5f, 0f, 5f)) for (f in floatArrayOf(0.8f, 1f, 1.25f)) {
                val el = b0.el + del
                if (el < 0f) continue
                val c = Cam(b0.az + daz, el, b0.dist * f)
                if (worth(c) > worth(best)) best = c
            }
        }
        // +Z (the usual side) wins unless another direction matches clearly better: a front and a
        // back outline are mirror images, so near-symmetric objects can't tell them apart.
        val front0 = scores.entries.filter { it.key.first == 0f }.maxByOrNull { it.value - if (it.key.second == 0f && it.key.third == 0f) 0f else 0.01f }
        if (best.az != 0f && front0 != null && front0.value >= score(best) - KEEP_FRONT)
            best = Cam(0f, front0.key.second, front0.key.third)
        val frame = Frame(mesh, best)
        CrashLog.log("Mesh colors: camera $best, outline match %.3f (level front %.3f), ${scores.size} cameras tried"
            .format(score(best), score(Cam(0f, 0f))))
        return Projection(mesh, pic, back?.let { Picture(it) }, frame, frame.zbuffer(mesh, Z))
    }

    /** Per-vertex RGB (0..1) for [mesh]; [back] is an optional picture of the other side. */
    fun paint(mesh: Mesh, front: Bitmap, back: Bitmap? = null, search: Boolean = true): FloatArray =
        paint(mesh, project(mesh, front, back, search))

    fun paint(mesh: Mesh, proj: Projection): FloatArray {
        val n = mesh.vertexCount
        val p = mesh.positions; val nrm = mesh.normals
        val out = FloatArray(n * 3)
        val sure = BooleanArray(n)
        var seen = 0
        for (i in 0 until n) {
            val x = p[i * 3]; val y = p[i * 3 + 1]; val z = p[i * 3 + 2]
            when {
                nrm.size == n * 3 && proj.sees(x, y, z, nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2]) -> {
                    proj.sample(x, y, z, out, i * 3); sure[i] = true; seen++
                }
                proj.sampleBack(x, y, z, out, i * 3) -> sure[i] = true
                else -> proj.sample(x, y, z, out, i * 3)  // kept only where nothing sure is connected
            }
        }
        spread(mesh, out, sure)
        CrashLog.log("Mesh colors: ${seen * 100 / n.coerceAtLeast(1)}% of vertices seen face-on")
        return out
    }

    /**
     * A texture for [mesh] (its vertex colors already painted): every triangle gets its own patch,
     * and each texel the camera sees face-on takes the picture's color there; the rest take the
     * vertex colors. Null when the mesh is too dense for a texture of at most [maxSize]².
     */
    fun bake(mesh: Mesh, proj: Projection, maxSize: Int = 3072): Mesh? {
        var atlas: TextureAtlas? = null
        for (size in intArrayOf(2048, 3072, 4096)) {
            if (size > maxSize) break
            val a = TextureAtlas.build(mesh, size) ?: continue
            atlas = a
            if (a.cellSize >= 6) break
        }
        val a = atlas ?: return null
        val size = a.size
        val px = IntArray(size * size)
        val ids = IntArray(3); val w = FloatArray(3); val c = FloatArray(3)
        val pos = mesh.positions; val nrm = mesh.normals; val col = mesh.colors
        var face = 0
        for (i in 0 until a.texelCount) {
            val pix = a.texelAt(i, ids, w)
            var x = 0f; var y = 0f; var z = 0f; var nx = 0f; var ny = 0f; var nz = 0f
            for (k in 0..2) {
                val v = ids[k] * 3; val wk = w[k]
                x += pos[v] * wk; y += pos[v + 1] * wk; z += pos[v + 2] * wk
                nx += nrm[v] * wk; ny += nrm[v + 1] * wk; nz += nrm[v + 2] * wk
            }
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            if (proj.sees(x, y, z, nx / l, ny / l, nz / l)) { proj.sample(x, y, z, c, 0); face++ }
            else if (!proj.sampleBack(x, y, z, c, 0)) for (k in 0..2) {
                c[k] = col[ids[0] * 3 + k] * w[0] + col[ids[1] * 3 + k] * w[1] + col[ids[2] * 3 + k] * w[2]
            }
            fun ch(k: Int) = (c[k].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            px[pix] = (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
        }
        CrashLog.log("Mesh texture ${size}px, cell ${a.cellSize}: ${face * 100L / a.texelCount.coerceAtLeast(1)}% of texels from the picture")
        return Mesh(mesh.positions, mesh.normals, mesh.colors, a.indices).also {
            it.uvs = a.uvs; it.texture = Texture(size, px); it.sizeMm = mesh.sizeMm
        }
    }

    /**
     * Gives every vertex that is not [sure] the color of the sure surface nearest to it along
     * the mesh: ring by ring outward from the painted part, then evened out.
     */
    private fun spread(mesh: Mesh, rgb: FloatArray, sure: BooleanArray) {
        val n = mesh.vertexCount; val idx = mesh.indices
        if (sure.none { it } || sure.all { it }) return
        val start = IntArray(n + 1)
        for (v in idx) start[v + 1] += 2
        for (v in 0 until n) start[v + 1] += start[v]
        val fill = start.copyOf(n)
        val list = IntArray(idx.size * 2)
        for (t in 0 until idx.size / 3) for (e in 0..2) {
            val v = idx[t * 3 + e]
            list[fill[v]++] = idx[t * 3 + (e + 1) % 3]
            list[fill[v]++] = idx[t * 3 + (e + 2) % 3]
        }
        val done = sure.copyOf()
        var front = IntList().also { r -> for (v in 0 until n) if (done[v]) r.add(v) }.toArray()
        val queued = BooleanArray(n)
        while (front.isNotEmpty()) {
            val next = IntList()
            for (v in front) for (k in start[v] until start[v + 1]) {
                val u = list[k]
                if (!done[u] && !queued[u]) { queued[u] = true; next.add(u) }
            }
            val ring = next.toArray()
            // A new ring is colored from what was there before it, then joins it.
            for (u in ring) {
                var r = 0f; var g = 0f; var b = 0f; var c = 0
                for (k in start[u] until start[u + 1]) {
                    val w = list[k]
                    if (done[w]) { r += rgb[w * 3]; g += rgb[w * 3 + 1]; b += rgb[w * 3 + 2]; c++ }
                }
                if (c > 0) { rgb[u * 3] = r / c; rgb[u * 3 + 1] = g / c; rgb[u * 3 + 2] = b / c }
            }
            for (u in ring) done[u] = true
            front = ring
        }
        // Even out the filled part, so the rings leave no streaks.
        val f = IntList().also { l -> for (v in 0 until n) if (done[v] && !sure[v]) l.add(v) }.toArray()
        val tmp = FloatArray(f.size * 3)
        repeat(8) {
            for ((i, u) in f.withIndex()) {
                var r = 0f; var g = 0f; var b = 0f; var c = 0
                for (k in start[u] until start[u + 1]) { val w = list[k]; r += rgb[w * 3]; g += rgb[w * 3 + 1]; b += rgb[w * 3 + 2]; c++ }
                if (c == 0) { tmp[i * 3] = rgb[u * 3]; tmp[i * 3 + 1] = rgb[u * 3 + 1]; tmp[i * 3 + 2] = rgb[u * 3 + 2] }
                else { tmp[i * 3] = r / c; tmp[i * 3 + 1] = g / c; tmp[i * 3 + 2] = b / c }
            }
            for ((i, u) in f.withIndex()) { rgb[u * 3] = tmp[i * 3]; rgb[u * 3 + 1] = tmp[i * 3 + 1]; rgb[u * 3 + 2] = tmp[i * 3 + 2] }
        }
    }
}
