package com.triposr.mobile

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Coloring a shape-only model from its picture, measured: a figure with known colors (yellow
 * head, red shirt with fine white stripes, green arms, blue trousers) is photographed with a
 * perspective camera from a little above, as a phone would, and painted back from that photo.
 * Scores are the share of the surface the photo shows that gets the right color.
 * Writes build/qa/color_report.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MeshColorTest {
    private val out = File("build/qa").apply { mkdirs() }

    // ------------------------------------------------------------------ the figure

    private fun blob(x: Float, y: Float, z: Float, cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float): Float {
        val a = (x - cx) / rx; val b = (y - cy) / ry; val c = (z - cz) / rz
        return 1f - (a * a + b * b + c * c)
    }

    private fun figure(): Mesh {
        val res = 128; val r = 1.2f
        val step = 2 * r / (res - 1)
        val field = FloatArray(res * res * res)
        for (i in 0 until res) for (j in 0 until res) for (k in 0 until res) {
            val x = -r + i * step; val y = -r + j * step; val z = -r + k * step
            field[(i * res + j) * res + k] = maxOf(
                blob(x, y, z, 0f, -0.05f, 0f, 0.34f, 0.42f, 0.24f),       // body
                blob(x, y, z, 0f, 0.55f, 0f, 0.2f, 0.2f, 0.2f),            // head
                blob(x, y, z, -0.5f, 0.15f, 0f, 0.24f, 0.09f, 0.09f),      // arms
                blob(x, y, z, 0.5f, 0.15f, 0f, 0.24f, 0.09f, 0.09f),
                maxOf(blob(x, y, z, -0.15f, -0.65f, 0f, 0.11f, 0.32f, 0.11f),  // legs
                    blob(x, y, z, 0.15f, -0.65f, 0f, 0.11f, 0.32f, 0.11f)))
        }
        return SurfaceNets.extract(field, res, r)
    }

    /** Fine stripes on the front of the shirt: narrower than the space between two vertices. */
    private fun striped(x: Float, y: Float, z: Float) = z > 0.05f && abs(x) < 0.2f && y > -0.05f && y < 0.25f

    /** The true color at a point of the figure. */
    private fun truth(x: Float, y: Float, z: Float, o: FloatArray) {
        val c = when {
            y > 0.36f -> floatArrayOf(0.95f, 0.8f, 0.15f)                       // head
            abs(x) > 0.3f && y > -0.05f -> floatArrayOf(0.15f, 0.7f, 0.25f)     // arms
            y < -0.22f -> floatArrayOf(0.15f, 0.25f, 0.85f)                      // trousers
            striped(x, y, z) && floor(x * 40f).toInt() % 2 == 0 -> floatArrayOf(0.95f, 0.95f, 0.95f)
            else -> floatArrayOf(0.85f, 0.12f, 0.12f)                            // shirt
        }
        System.arraycopy(c, 0, o, 0, 3)
    }

    // ------------------------------------------------------------------ the photo

    /** A perspective camera in front, [elevation] degrees above, looking at the middle. */
    private class Camera(elevation: Float, val size: Int) {
        val e = Math.toRadians(elevation.toDouble()).toFloat()
        val dist = 4f
        val f = size / 2f / tan(Math.toRadians(14.0)).toFloat()
        /** Screen x, y and distance of a point. */
        fun project(x: Float, y: Float, z: Float, o: FloatArray) {
            // Camera at (0, dist sin e, dist cos e) looking at the origin.
            val zc = dist - (z * cos(e) + y * sin(e))
            val yc = y * cos(e) - z * sin(e)
            o[0] = size / 2f + f * x / zc
            o[1] = size / 2f - f * yc / zc
            o[2] = zc
        }
        val toward = floatArrayOf(0f, sin(e), cos(e))
    }

    private class Photo(val bitmap: Bitmap, val zbuf: FloatArray, val cam: Camera)

    private fun photograph(mesh: Mesh, cam: Camera): Photo {
        val n = cam.size
        val zbuf = FloatArray(n * n) { Float.MAX_VALUE }
        val px = IntArray(n * n)
        val p = mesh.positions; val idx = mesh.indices
        val a = FloatArray(3); val b = FloatArray(3); val c = FloatArray(3); val col = FloatArray(3)
        for (t in 0 until mesh.triangleCount) {
            val ia = idx[t * 3]; val ib = idx[t * 3 + 1]; val ic = idx[t * 3 + 2]
            cam.project(p[ia * 3], p[ia * 3 + 1], p[ia * 3 + 2], a)
            cam.project(p[ib * 3], p[ib * 3 + 1], p[ib * 3 + 2], b)
            cam.project(p[ic * 3], p[ic * 3 + 1], p[ic * 3 + 2], c)
            val area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
            if (area == 0f) continue
            val lx = max(0, min(a[0], min(b[0], c[0])).toInt()); val hx = min(n - 1, max(a[0], max(b[0], c[0])).toInt() + 1)
            val ly = max(0, min(a[1], min(b[1], c[1])).toInt()); val hy = min(n - 1, max(a[1], max(b[1], c[1])).toInt() + 1)
            for (y in ly..hy) for (x in lx..hx) {
                val sx = x + 0.5f; val sy = y + 0.5f
                val w0 = ((b[0] - sx) * (c[1] - sy) - (c[0] - sx) * (b[1] - sy)) / area
                val w1 = ((c[0] - sx) * (a[1] - sy) - (a[0] - sx) * (c[1] - sy)) / area
                val w2 = 1f - w0 - w1
                if (w0 < 0 || w1 < 0 || w2 < 0) continue
                val z = w0 * a[2] + w1 * b[2] + w2 * c[2]
                val k = y * n + x
                if (z >= zbuf[k]) continue
                zbuf[k] = z
                val qx = w0 * p[ia * 3] + w1 * p[ib * 3] + w2 * p[ic * 3]
                val qy = w0 * p[ia * 3 + 1] + w1 * p[ib * 3 + 1] + w2 * p[ic * 3 + 1]
                val qz = w0 * p[ia * 3 + 2] + w1 * p[ib * 3 + 2] + w2 * p[ic * 3 + 2]
                truth(qx, qy, qz, col)
                px[k] = (0xFF shl 24) or ((col[0] * 255).toInt() shl 16) or ((col[1] * 255).toInt() shl 8) or (col[2] * 255).toInt()
            }
        }
        return Photo(Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888), zbuf, cam)
    }

    /** Whether the photo shows the point face-on (well away from grazing). */
    private fun shown(ph: Photo, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Boolean {
        val t = ph.cam.toward
        if (nx * t[0] + ny * t[1] + nz * t[2] < 0.5f) return false
        val o = FloatArray(3); ph.cam.project(x, y, z, o)
        val n = ph.cam.size
        val ix = o[0].toInt(); val iy = o[1].toInt()
        if (ix !in 0 until n || iy !in 0 until n) return false
        return o[2] <= ph.zbuf[iy * n + ix] + 0.02f
    }

    private fun close(a: FloatArray, ai: Int, b: FloatArray) = (0..2).all { abs(a[ai + it] - b[it]) < 0.2f }

    /** Share of the shown, unstriped vertices whose painted color is right. */
    private fun vertexScore(mesh: Mesh, ph: Photo, colors: FloatArray): Float {
        var ok = 0; var all = 0
        val gt = FloatArray(3); val p = mesh.positions; val nr = mesh.normals
        for (v in 0 until mesh.vertexCount) {
            val x = p[v * 3]; val y = p[v * 3 + 1]; val z = p[v * 3 + 2]
            if (striped(x, y, z) || !shown(ph, x, y, z, nr[v * 3], nr[v * 3 + 1], nr[v * 3 + 2])) continue
            truth(x, y, z, gt); all++
            if (close(colors, v * 3, gt)) ok++
        }
        return ok.toFloat() / max(all, 1)
    }

    /**
     * Share of points on shown triangles (six inside each) whose color is right, from the texture
     * when [textured], else from the vertex colors blended across the triangle. [stripes] picks
     * the striped part or the rest.
     */
    private fun surfaceScore(m: Mesh, ph: Photo, textured: Boolean, stripes: Boolean): Float {
        val tex = m.texture; val uv = m.uvs
        val p = m.positions; val nr = m.normals; val idx = m.indices
        val gt = FloatArray(3); val c = FloatArray(3)
        val bary = arrayOf(floatArrayOf(0.6f, 0.2f, 0.2f), floatArrayOf(0.2f, 0.6f, 0.2f), floatArrayOf(0.2f, 0.2f, 0.6f),
            floatArrayOf(0.45f, 0.45f, 0.1f), floatArrayOf(0.1f, 0.45f, 0.45f), floatArrayOf(0.45f, 0.1f, 0.45f))
        var ok = 0; var all = 0
        for (t in 0 until m.triangleCount) {
            val v = IntArray(3) { idx[t * 3 + it] }
            for (w in bary) {
                var x = 0f; var y = 0f; var z = 0f; var nx = 0f; var ny = 0f; var nz = 0f
                for (k in 0..2) {
                    x += p[v[k] * 3] * w[k]; y += p[v[k] * 3 + 1] * w[k]; z += p[v[k] * 3 + 2] * w[k]
                    nx += nr[v[k] * 3] * w[k]; ny += nr[v[k] * 3 + 1] * w[k]; nz += nr[v[k] * 3 + 2] * w[k]
                }
                if (striped(x, y, z) != stripes) continue
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (!shown(ph, x, y, z, nx / l, ny / l, nz / l)) continue
                if (textured) {
                    var u = 0f; var vv = 0f
                    for (k in 0..2) { u += uv!![(t * 3 + k) * 2] * w[k]; vv += uv[(t * 3 + k) * 2 + 1] * w[k] }
                    val px = tex!!.pixels[(vv * tex.size).toInt().coerceIn(0, tex.size - 1) * tex.size + (u * tex.size).toInt().coerceIn(0, tex.size - 1)]
                    c[0] = ((px shr 16) and 0xFF) / 255f; c[1] = ((px shr 8) and 0xFF) / 255f; c[2] = (px and 0xFF) / 255f
                } else for (k in 0..2) c[k] = m.colors[v[0] * 3 + k] * w[0] + m.colors[v[1] * 3 + k] * w[1] + m.colors[v[2] * 3 + k] * w[2]
                truth(x, y, z, gt); all++
                if ((0..2).all { abs(c[it] - gt[it]) < 0.2f }) ok++
            }
        }
        return ok.toFloat() / max(all, 1)
    }

    @Test fun colorsLandOnTheRightPartsAndTheTextureKeepsFineDetail() {
        val mesh = figure()
        val rows = ArrayList<String>()
        rows += "# Coloring from the picture"
        rows += ""
        rows += "Test figure: ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles. Photo: perspective, 768 px."
        rows += ""
        rows += "| photo taken from | old (box match) | new (outline match) |"
        rows += "|---|---|---|"
        val scores = HashMap<Float, Pair<Float, Float>>()
        for (el in floatArrayOf(0f, 15f)) {
            val ph = photograph(mesh, Camera(el, 768))
            if (el == 15f) ph.bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(out, "color_photo.png").outputStream())
            val old = vertexScore(mesh, ph, MeshColor.paint(mesh, ph.bitmap, align = false))
            val new = vertexScore(mesh, ph, MeshColor.paint(mesh, ph.bitmap))
            scores[el] = old to new
            rows += "| %.0f° above | %.1f%% | %.1f%% |".format(el, old * 100, new * 100)
        }

        // The texture, from the photo taken from above.
        val ph = photograph(mesh, Camera(15f, 768))
        val proj = MeshColor.project(mesh, ph.bitmap)
        val painted = Mesh(mesh.positions, mesh.normals, MeshColor.paint(mesh, proj), mesh.indices)
        val baked = MeshColor.bake(painted, proj)
        assertTrue("a texture was made", baked != null)
        val vertStripes = surfaceScore(painted, ph, textured = false, stripes = true)
        val texStripes = surfaceScore(baked!!, ph, textured = true, stripes = true)
        val vertRest = surfaceScore(painted, ph, textured = false, stripes = false)
        val texRest = surfaceScore(baked, ph, textured = true, stripes = false)
        rows += ""
        rows += "| fine stripes on the shirt | vertex colors | texture (${baked.texture!!.size} px) |"
        rows += "|---|---|---|"
        rows += "| stripes | %.1f%% | %.1f%% |".format(vertStripes * 100, texStripes * 100)
        rows += "| everything else shown | %.1f%% | %.1f%% |".format(vertRest * 100, texRest * 100)
        File(out, "color_report.md").writeText(rows.joinToString("\n") + "\n")
        println(rows.joinToString("\n"))

        val (old0, new0) = scores[0f]!!; val (old15, new15) = scores[15f]!!
        assertTrue("straight on, the new match is as good: old %.3f new %.3f".format(old0, new0), new0 >= old0 - 0.01f && new0 > 0.9f)
        assertTrue("from above, the new match is clearly better: old %.3f new %.3f".format(old15, new15), new15 > old15 + 0.03f && new15 > 0.9f)
        assertTrue("the texture keeps the stripes: texture %.3f, vertex colors %.3f".format(texStripes, vertStripes), texStripes > vertStripes + 0.15f && texStripes > 0.8f)
        assertTrue("the texture is right elsewhere too: %.3f".format(texRest), texRest > 0.9f)
    }
}
