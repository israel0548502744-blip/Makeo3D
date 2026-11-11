package com.triposr.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The touch-up tools on a small scene: an eight-sided body on the left, a loose four-sided crumb on the right. */
class MeshEditTest {
    private fun scene(): Mesh {
        val p = floatArrayOf(
            -0.5f, 0.3f, 0f, -0.5f, -0.3f, 0f, -0.8f, 0f, 0f, -0.2f, 0f, 0f, -0.5f, 0f, 0.3f, -0.5f, 0f, -0.3f,   // body 0..5
            0.5f, 0.1f, 0f, 0.4f, -0.1f, 0f, 0.6f, -0.1f, 0f, 0.5f, 0f, 0.1f)                                      // crumb 6..9
        val t = intArrayOf(
            0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 4, 2, 1, 3, 4, 1, 5, 3, 1, 2, 5,
            6, 7, 9, 6, 9, 8, 6, 8, 7, 7, 8, 9)
        return Mesh(p, MeshTools.normals(p, t), FloatArray(p.size) { 0.5f }, t)
    }

    // A camera that looks straight on: screen x = (x + 1) / 2 · width, depth 1 everywhere.
    private val camera = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    @Test fun eraseTakesWhatIsUnderTheBrushAndUndoBringsItBack() {
        val e = MeshEditor(scene())
        val under = e.pick(camera, 1000, 1000, 750f, 500f, 80f, through = true)
        assertTrue("the brush over the crumb finds its vertices", under.isNotEmpty() && under.all { it >= 6 })
        e.erase(under); e.endStroke()
        assertEquals("the crumb is gone, the body is whole", 8 * 3, e.indices.size)
        assertTrue(e.undo())
        assertEquals(12 * 3, e.indices.size)
        assertTrue("nothing more to undo", !e.undo())
    }

    @Test fun erasingPartOfABodyLeavesItClosed() {
        val e = MeshEditor(scene())
        e.erase(intArrayOf(4)); e.endStroke()   // the four triangles around vertex 4 go
        val m = e.result()
        val edges = HashSet<Long>()
        for (t in 0 until m.triangleCount) for (k in 0..2)
            edges.add((m.indices[t * 3 + k].toLong() shl 32) or m.indices[t * 3 + (k + 1) % 3].toLong())
        assertTrue("every edge has its opposite: no hole is left",
            edges.all { ((it and 0xFFFFFFFFL) shl 32) or (it ushr 32) in edges })
        assertEquals("the hole is patched with one triangle per rim edge", 12, m.triangleCount)
        assertTrue(e.undo())
        assertEquals("undo removes the patch and brings the part back", 12 * 3, e.indices.size)
        assertEquals(10, e.result().vertexCount)
    }

    /** A ball of 2048 triangles: an eight-sided shape with every triangle split in four, four times. */
    private fun ball(): Mesh {
        var p = arrayListOf(0f, 1f, 0f, 0f, -1f, 0f, -1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f)
        var t = intArrayOf(0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 4, 2, 1, 3, 4, 1, 5, 3, 1, 2, 5)
        repeat(4) {
            val mid = HashMap<Long, Int>()
            fun m(a: Int, b: Int): Int = mid.getOrPut((minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()) {
                val x = p[a * 3] + p[b * 3]; val y = p[a * 3 + 1] + p[b * 3 + 1]; val z = p[a * 3 + 2] + p[b * 3 + 2]
                val l = kotlin.math.sqrt(x * x + y * y + z * z)
                p.add(x / l); p.add(y / l); p.add(z / l)
                p.size / 3 - 1
            }
            val out = ArrayList<Int>()
            for (k in 0 until t.size / 3) {
                val a = t[k * 3]; val b = t[k * 3 + 1]; val c = t[k * 3 + 2]
                val ab = m(a, b); val bc = m(b, c); val ca = m(c, a)
                out.addAll(listOf(a, ab, ca, ab, b, bc, ca, bc, c, ab, bc, ca))
            }
            t = out.toIntArray()
        }
        val pos = p.toFloatArray()
        return Mesh(pos, MeshTools.normals(pos, t), FloatArray(pos.size) { 0.5f }, t)
    }

    @Test fun aBigBiteOutOfABallIsSkinnedOverSmoothly() {
        val src = ball()
        val e = MeshEditor(src)
        // Bite off the cap of the ball that faces +z (everything above z = 0.8).
        val cap = (0 until src.vertexCount).filter { src.positions[it * 3 + 2] > 0.8f }.toIntArray()
        e.erase(cap); e.endStroke()
        val m = e.result()
        val edges = HashSet<Long>()
        for (t in 0 until m.triangleCount) for (k in 0..2)
            edges.add((m.indices[t * 3 + k].toLong() shl 32) or m.indices[t * 3 + (k + 1) % 3].toLong())
        assertTrue("the ball is closed again",
            edges.all { ((it and 0xFFFFFFFFL) shl 32) or (it ushr 32) in edges })
        var top = -1f
        for (v in 0 until m.vertexCount) top = maxOf(top, m.positions[v * 3 + 2])
        assertTrue("the bite is a flat face, not a tent or a dent: top at ${top}", top < 0.82f && top > 0.6f)
        assertTrue("the face is made of many small triangles", m.triangleCount > src.triangleCount - cap.size * 6 + 40)
        assertTrue(e.undo())
        assertEquals(src.triangleCount * 3, e.indices.size)
    }

    @Test fun loosePiecesGoAndTheBiggestStays() {
        val e = MeshEditor(scene())
        assertEquals(1, e.removeLoose())
        val m = e.result()
        assertEquals(8, m.triangleCount)
        assertEquals("unused vertices are dropped", 6, m.vertexCount)
        assertTrue(m.indices.all { it in 0 until m.vertexCount })
        assertEquals(m.positions.size, m.normals.size)
    }

    @Test fun smoothingPullsASpikeInAndUndoRestoresIt() {
        val src = scene()
        src.positions[4 * 3 + 2] = 2f   // vertex 4 sticks far out
        val e = MeshEditor(src)
        e.smooth(intArrayOf(4)); e.endStroke()
        assertTrue("the spike is lower", e.positions[4 * 3 + 2] < 1f)
        assertEquals("smoothing removes nothing", 12 * 3, e.indices.size)
        assertTrue(e.undo())
        assertEquals(2f, e.positions[4 * 3 + 2], 0f)
    }

    @Test fun onlyTheNearSurfaceIsTakenUnlessAskedToReachThrough() {
        // The same camera, but depth grows with -z: vertex 4 (z = 0.3) is nearer than vertex 5 (z = -0.3).
        val cam = camera.copyOf().also { it[11] = -1f; it[15] = 2f }
        val e = MeshEditor(scene())
        fun at(v: Int): Pair<Float, Float> {
            val w = 2f - e.positions[v * 3 + 2]
            return (e.positions[v * 3] / w * 0.5f + 0.5f) * 1000f to (0.5f - e.positions[v * 3 + 1] / w * 0.5f) * 1000f
        }
        val (x, y) = at(4)
        val near = e.pick(cam, 1000, 1000, x, y, 45f, through = false)
        val all = e.pick(cam, 1000, 1000, x, y, 45f, through = true)
        assertTrue("the front vertex is picked", 4 in near)
        assertTrue("the back vertex is left alone", 5 !in near)
        assertTrue("reaching through takes both", 4 in all && 5 in all)
    }
}
