package com.triposr.mobile

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every shape the sculpt screen starts from is a closed, printable body that the touch tools can work. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SculptTest {
    @Test fun startingShapesAreClosedAndFine() {
        for (s in Sculpt.Shape.entries) {
            val (m, thumb) = Sculpt.shape(s, 60f)
            val r = MeshCheck.inspect(m)
            assertTrue("$s: ${r.problems}", r.ok)
            assertTrue("$s has enough triangles to sculpt: ${m.triangleCount}", m.triangleCount > 20_000)
            assertTrue(thumb.width > 0)
            // Pull a part out: still closed.
            val e = MeshEditor(m)
            val top = (0 until m.vertexCount).filter { m.positions[it * 3 + 1] > 0.7f }.toIntArray()
            e.grab(MeshEditor.Pick(top, FloatArray(top.size) { 1f }, 0.2f)); e.drag(0f, 0.3f, 0f); e.endStroke()
            assertTrue("$s pulled: ${MeshCheck.inspect(e.result()).problems}", MeshCheck.inspect(e.result()).ok)
        }
    }

    @Test fun aDrawnRingBecomesADiscOrARing() {
        // A circle drawn with a finger, as points around a 300 x 300 area.
        val pts = FloatArray(80) { i -> val a = (i / 2) * Math.PI * 2 / 39; (150 + 100 * if (i % 2 == 0) Math.cos(a) else Math.sin(a)).toFloat() }
        val mask = Sculpt.strokes(listOf(pts), listOf(14f), 300f, 300f)
        val (disc, _) = Sculpt.drawing(mask, 60f, 5f, filled = true)
        val (ring, _) = Sculpt.drawing(mask, 60f, 5f, filled = false)
        val rd = MeshCheck.inspect(disc); val rr = MeshCheck.inspect(ring)
        assertTrue("disc: ${rd.problems}", rd.ok)
        assertTrue("ring: ${rr.problems}", rr.ok)
        assertTrue("the filled one holds more material (${rd.volumeMm3} vs ${rr.volumeMm3})", rd.volumeMm3 > rr.volumeMm3 * 2)
    }
}
