package com.triposr.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Makes every print product for real (the same code the buttons run), with each of its options
 * changed in turn, and judges the result: geometry exists, closed, faces outward, the expected
 * number of pieces and size, each option changes the shape, and the STL comes out complete.
 * Writes build/qa/report.md and a picture of every product; fails when anything is wrong.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CraftsQaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("build/qa").apply { mkdirs() }
    private val rows = ArrayList<String>()
    private val failures = ArrayList<String>()

    @Before fun setUp() {
        // A stand-in for the depth extra: nearer toward the middle of the picture.
        Crafts.depthHook = { _, w, h -> FloatArray(w * h) { i -> 1f - hypot(i % w - w / 2f, i / w - h / 2f) / hypot(w / 2f, h / 2f) } }
    }

    // ------------------------------------------------------------------ test pictures

    /** A figure on a transparent background (what the app has after removing the background). */
    private fun figure(): Bitmap {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFE07A3F.toInt()
        c.drawOval(RectF(206f, 70f, 306f, 170f), p)
        p.color = 0xFF7C5CFF.toInt()
        c.drawRoundRect(RectF(186f, 160f, 326f, 340f), 50f, 50f, p)
        c.drawRoundRect(RectF(196f, 320f, 246f, 470f), 25f, 25f, p)
        c.drawRoundRect(RectF(266f, 320f, 316f, 470f), 25f, 25f, p)
        return bmp
    }

    /** A dark line drawing on white: a star outline with a dot inside. */
    private fun drawing(): Bitmap {
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 14f }
        val path = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) 170f else 75f; val a = Math.PI * k / 5 - Math.PI / 2
            val x = 200 + r * cos(a).toFloat(); val y = 200 + r * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); c.drawPath(path, p)
        p.style = Paint.Style.FILL; c.drawCircle(200f, 200f, 22f, p)
        return bmp
    }

    /** An ordinary photo: no transparency, smooth light and dark areas, not square. */
    private fun photo(): Bitmap {
        val w = 480; val h = 360
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val v = (128 + 100 * sin(x / 40.0) * cos(y / 30.0)).toInt().coerceIn(0, 255)
            Color.rgb(v, (v * 0.8).toInt(), 255 - v)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** A closed 3D model with a hollow and parts hiding other parts: a torus with a ball on top. */
    private fun model(): Mesh {
        val res = 56; val r = TripoEngine.RADIUS; val step = 2 * r / (res - 1)
        val f = FloatArray(res * res * res) { idx ->
            val x = -r + idx / (res * res) * step; val y = -r + idx / res % res * step; val z = -r + idx % res * step
            val torus = 0.12f - hypot(hypot(x, z) - 0.42f, y * 1.2f)
            val ball = 0.2f - sqrt(x * x + (y - 0.5f) * (y - 0.5f) + z * z)
            max(torus, ball)
        }
        return SurfaceNets.extract(f, res, r)
    }

    // ------------------------------------------------------------------ judging one result

    private class Made(val check: MeshCheck.Result, val print: PrintReport, val stlBytes: Int, val signature: String)

    /** Oblique picture of a print-space mesh (mm, Z up), shaded by its faces. */
    private fun render(m: Mesh, file: File) {
        val n = 420; val p = m.positions
        val ca = cos(Math.toRadians(35.0)).toFloat(); val sa = sin(Math.toRadians(35.0)).toFloat()   // turn about Z
        val ce = cos(Math.toRadians(50.0)).toFloat(); val se = sin(Math.toRadians(50.0)).toFloat()   // tilt down
        val sx = FloatArray(m.vertexCount); val sy = FloatArray(m.vertexCount); val sz = FloatArray(m.vertexCount)
        var lox = Float.MAX_VALUE; var hix = -Float.MAX_VALUE; var loy = Float.MAX_VALUE; var hiy = -Float.MAX_VALUE
        for (v in 0 until m.vertexCount) {
            val x = p[v * 3] * ca - p[v * 3 + 1] * sa; val y = p[v * 3] * sa + p[v * 3 + 1] * ca; val z = p[v * 3 + 2]
            sx[v] = x; sy[v] = -(y * ce + z * se); sz[v] = -y * se + z * ce
            lox = min(lox, sx[v]); hix = max(hix, sx[v]); loy = min(loy, sy[v]); hiy = max(hiy, sy[v])
        }
        val s = (n - 20) / max(hix - lox, hiy - loy).coerceAtLeast(1e-6f)
        val zb = FloatArray(n * n) { -Float.MAX_VALUE }; val img = IntArray(n * n) { Color.rgb(24, 20, 44) }
        val idx = m.indices
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            val ax = 10 + (sx[a] - lox) * s; val ay = 10 + (sy[a] - loy) * s
            val bx = 10 + (sx[b] - lox) * s; val by = 10 + (sy[b] - loy) * s
            val cx = 10 + (sx[c] - lox) * s; val cy = 10 + (sy[c] - loy) * s
            val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if (area == 0f) continue
            // Face normal in print space, lit from above and the side.
            val ux = p[b * 3] - p[a * 3]; val uy = p[b * 3 + 1] - p[a * 3 + 1]; val uz = p[b * 3 + 2] - p[a * 3 + 2]
            val vx = p[c * 3] - p[a * 3]; val vy = p[c * 3 + 1] - p[a * 3 + 1]; val vz = p[c * 3 + 2] - p[a * 3 + 2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f); nx /= l; ny /= l; nz /= l
            val light = (0.35f + 0.65f * max(0f, nx * 0.35f - ny * 0.45f + nz * 0.82f)).coerceIn(0f, 1f)
            // Faces pointing away from the viewer are drawn red: an inside-out surface shows up at once.
            val toward = -(nx * sa + ny * ca) * se + nz * ce
            val col = m.colors
            val r0 = (col[a * 3] + col[b * 3] + col[c * 3]) / 3; val g0 = (col[a * 3 + 1] + col[b * 3 + 1] + col[c * 3 + 1]) / 3
            val b0 = (col[a * 3 + 2] + col[b * 3 + 2] + col[c * 3 + 2]) / 3
            val color = if (toward < -0.05f) Color.rgb((200 * light).toInt(), 0, 0)
                else Color.rgb((255 * r0 * light).toInt(), (255 * g0 * light).toInt(), (255 * b0 * light).toInt())
            for (y in max(0, min(ay, min(by, cy)).toInt())..min(n - 1, max(ay, max(by, cy)).toInt()))
                for (x in max(0, min(ax, min(bx, cx)).toInt())..min(n - 1, max(ax, max(bx, cx)).toInt())) {
                    val w0 = ((bx - x) * (cy - y) - (cx - x) * (by - y)) / area
                    val w1 = ((cx - x) * (ay - y) - (ax - x) * (cy - y)) / area
                    val w2 = 1f - w0 - w1
                    if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                    val z = w0 * sz[a] + w1 * sz[b] + w2 * sz[c]
                    if (z > zb[y * n + x]) { zb[y * n + x] = z; img[y * n + x] = color }
                }
        }
        file.outputStream().use { Bitmap.createBitmap(img, n, n, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Checks one product. [parts]: how many separate pieces it must have. [longMm]: its longest
     * side when the product defines it. Returns null when it could not even be built.
     */
    private fun judge(name: String, parts: Int, longMm: Float? = null, build: () -> Mesh): Made? {
        val bad = ArrayList<String>()
        var made: Made? = null
        try {
            val mesh = build()
            val c = MeshCheck.inspect(mesh)
            bad += c.problems
            if (c.parts != parts) bad += "pieces: ${c.parts}, expected $parts"
            if (longMm != null && abs(c.sizeMm.max() - longMm) > max(2f, longMm * 0.04f)) bad += "longest side %.1f mm, expected %.1f".format(c.sizeMm.max(), longMm)
            // What the user gets from "export": the print-ready copy, written as STL.
            val pm = Printability.prepare(mesh, targetMm = mesh.sizeMm ?: Printability.TARGET_MM)
            val r = pm.report
            if (!r.watertight) bad += "export not watertight (open ${r.openEdgesAfter}, non-manifold ${r.nonManifoldAfter})"
            if (r.floatersRemoved > 0) bad += "export dropped ${r.floatersRemoved} piece(s)"
            if (r.holesFilled > 0) bad += "export had to fill ${r.holesFilled} hole(s)"
            val exported = MeshCheck.inspect(pm.mesh.also { it.sizeMm = r.sizeMm.max() }.let { printToViewer(it) })
            if (exported.parts != parts) bad += "pieces after export: ${exported.parts}, expected $parts"
            if (abs(r.volumeCm3 * 1000 - c.volumeMm3) > max(50.0, c.volumeMm3 * 0.02)) bad += "volume changed on export: %.1f -> %.1f cm3".format(c.volumeMm3 / 1000, r.volumeCm3)
            val stl = ByteArrayOutputStream().also { MeshTools.write(mesh, ExportFormat.STL, it) }.toByteArray()
            val expected = 84 + 50 * r.triangles
            if (stl.size != expected) bad += "STL is ${stl.size} bytes, expected $expected"
            render(pm.mesh, File(out, "$name.png"))
            val sig = "${c.triangles}/%.2f/%.2f/%.2f/%.0f".format(c.sizeMm[0], c.sizeMm[1], c.sizeMm[2], c.volumeMm3)
            made = Made(c, r, stl.size, sig)
            rows += "| $name | ${if (bad.isEmpty()) "PASS" else "FAIL"} | ${c.triangles} | ${c.parts} | %.0f×%.0f×%.0f | %.1f | %.2f | ${stl.size / 1024} | ${bad.joinToString("; ")} |"
                .format(c.sizeMm[0], c.sizeMm[1], c.sizeMm[2], r.volumeCm3, r.minWallMm)
        } catch (e: Throwable) {
            bad += "could not be made: ${e.javaClass.simpleName}: ${e.message}"
            rows += "| $name | FAIL | | | | | | | ${bad.joinToString("; ")} |"
        }
        for (b in bad) failures += "$name: $b"
        return made
    }

    /** A print-space mesh (Z up, mm) seen as viewer space (Y up), for [MeshCheck]. */
    private fun printToViewer(m: Mesh): Mesh {
        val p = m.positions
        val q = FloatArray(p.size) { i -> when (i % 3) { 0 -> p[i]; 1 -> p[i + 1]; else -> -p[i - 1] } }
        return Mesh(q, m.normals, m.colors, m.indices).also { it.sizeMm = m.sizeMm }
    }

    private fun opts(sizeMm: Float = 12f, alt: Boolean = false, flatBg: Boolean = true, reliefMm: Float = 8f,
                     moldMaster: Boolean = false, moldRelief: Boolean = false, wick: Boolean = false, copies: Int = 1,
                     second: String = "", frame: Boolean = true, maxMm: Float = 3f, cylinderMm: Float = 0f,
                     keyring: Boolean = true, stampImage: Boolean = false, tag: Templates.TagStyle = Templates.TagStyle.OUTLINE) =
        Creations.Options(frame = frame, maxMm = maxMm, reliefMm = reliefMm, flatBackground = flatBg, sizeMm = sizeMm, keyring = keyring,
            tagStyle = tag, cylinderMm = cylinderMm, stampImage = stampImage, qrSecond = second, moldMaster = moldMaster,
            moldRelief = moldRelief, wick = wick, copies = copies, alt = alt)

    private inner class Case(val name: String, val kind: Creations.Kind, val image: Bitmap?, val text: String,
                             val o: Creations.Options, val parts: Int = 1, val longMm: Float? = null)

    /** Runs a product's base case and its variants; every variant must differ from the base. */
    private fun product(base: Case, vararg variants: Case) {
        val b = judge(base.name, base.parts, base.longMm) { Creations.make(base.kind, base.image, base.text, base.o).first }
        for (v in variants) {
            val m = judge(v.name, v.parts, v.longMm) { Creations.make(v.kind, v.image, v.text, v.o).first }
            if (b != null && m != null && m.signature == b.signature) {
                failures += "${v.name}: the option changes nothing (same shape as ${base.name})"
                rows += "| ${v.name} | FAIL | | | | | | | the option changes nothing (same shape as ${base.name}) |"
            }
        }
    }

    @Test fun everyProduct() {
        val fig = figure(); val draw = drawing(); val pic = photo()

        // --- the 15 new products
        product(Case("stencil", Creations.Kind.STENCIL, fig, "", opts(100f), longMm = 116f),
            Case("stencil_size", Creations.Kind.STENCIL, fig, "", opts(160f), longMm = 176f),
            Case("stencil_drawing", Creations.Kind.STENCIL, draw, "", opts(100f, flatBg = false)))
        product(Case("magnet", Creations.Kind.MAGNET, fig, "", opts(60f), longMm = 60f),
            Case("magnet_size", Creations.Kind.MAGNET, fig, "", opts(85f), longMm = 85f))
        product(Case("stand", Creations.Kind.STAND, null, "", opts()),
            Case("stand_name", Creations.Kind.STAND, null, "דניאל", opts()))
        product(Case("frame", Creations.Kind.FRAME, null, "", opts(130f), longMm = 154f),
            Case("frame_size", Creations.Kind.FRAME, null, "", opts(100f), longMm = 124f),
            Case("frame_caption", Creations.Kind.FRAME, null, "קיץ 2026", opts(130f), longMm = 154f))
        product(Case("topper_text", Creations.Kind.TOPPER, null, "מזל טוב", opts(110f)),
            Case("topper_size", Creations.Kind.TOPPER, null, "מזל טוב", opts(150f)),
            Case("topper_short", Creations.Kind.TOPPER, null, "יעל", opts(70f)),
            Case("topper_image", Creations.Kind.TOPPER, fig, "", opts(110f)))
        product(Case("pendant", Creations.Kind.PENDANT, fig, "", opts(35f)),
            Case("pendant_size", Creations.Kind.PENDANT, fig, "", opts(55f)),
            Case("pendant_pair", Creations.Kind.PENDANT, fig, "", opts(35f, alt = true), parts = 2))
        product(Case("sign", Creations.Kind.SIGN, null, "החדר של נועה", opts(35f)),
            Case("sign_size", Creations.Kind.SIGN, null, "החדר של נועה", opts(55f)),
            Case("sign_english", Creations.Kind.SIGN, null, "Noa's room", opts(35f)))
        product(Case("bookmark", Creations.Kind.BOOKMARK, null, "נועה", opts(), longMm = 140f),
            Case("bookmark_long", Creations.Kind.BOOKMARK, null, "הספר של נועה כהן", opts(), longMm = 140f))
        product(Case("medal", Creations.Kind.MEDAL, fig, "", opts(70f), longMm = 70f),
            Case("medal_size", Creations.Kind.MEDAL, fig, "", opts(95f), longMm = 95f),
            Case("medal_name", Creations.Kind.MEDAL, fig, "סבא דוד", opts(70f), longMm = 70f),
            Case("medal_photo", Creations.Kind.MEDAL, pic, "", opts(70f, flatBg = false), longMm = 70f))
        product(Case("planter", Creations.Kind.PLANTER, pic, "", opts(80f)),
            Case("planter_size", Creations.Kind.PLANTER, pic, "", opts(110f)),
            Case("planter_tall_picture", Creations.Kind.PLANTER, fig, "", opts(80f)))
        product(Case("night", Creations.Kind.NIGHT, pic, "", opts(), parts = 2))
        product(Case("puzzle", Creations.Kind.PUZZLE, pic, "", opts(110f), parts = 6, longMm = 110f),
            Case("puzzle_size", Creations.Kind.PUZZLE, pic, "", opts(140f), parts = 6, longMm = 140f),
            Case("puzzle_more", Creations.Kind.PUZZLE, pic, "", opts(110f, alt = true), parts = 12, longMm = 110f))
        product(Case("seal_text", Creations.Kind.SEAL, null, "נ", opts(25f)),
            Case("seal_size", Creations.Kind.SEAL, null, "נ", opts(34f)),
            Case("seal_drawing", Creations.Kind.SEAL, draw, "", opts(25f)))
        product(Case("pettag", Creations.Kind.PETTAG, null, "לאקי", opts(40f), longMm = 40f),
            Case("pettag_size", Creations.Kind.PETTAG, null, "לאקי", opts(55f), longMm = 55f),
            Case("pettag_phone", Creations.Kind.PETTAG, null, "לאקי", opts(40f, second = "054-1234567"), longMm = 40f))
        product(Case("box", Creations.Kind.BOX, null, "", opts(70f), parts = 2),
            Case("box_size", Creations.Kind.BOX, null, "", opts(100f), parts = 2),
            Case("box_name", Creations.Kind.BOX, null, "תכשיטים", opts(70f), parts = 2))

        // --- casting molds
        product(Case("mold", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 12f)),
            Case("mold_size", Creations.Kind.MOLD, fig, "", opts(120f, reliefMm = 12f)),
            Case("mold_depth", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 25f)),
            Case("mold_master", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 12f, moldMaster = true)),
            Case("mold_relief", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 12f, moldRelief = true)),
            Case("mold_wick", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 12f, wick = true)),
            Case("mold_text", Creations.Kind.MOLD, fig, "נועה", opts(80f, reliefMm = 12f)),
            Case("mold_4", Creations.Kind.MOLD, fig, "", opts(80f, reliefMm = 12f, copies = 4)),
            Case("mold_master_6", Creations.Kind.MOLD, fig, "", opts(60f, reliefMm = 12f, moldMaster = true, copies = 6)),
            Case("mold_drawing", Creations.Kind.MOLD, draw, "", opts(80f, reliefMm = 12f, flatBg = false)))

        // --- the products that were there before (they must keep working)
        product(Case("lithophane", Creations.Kind.LITHOPHANE, pic, "", opts()),
            Case("lithophane_lamp", Creations.Kind.LITHOPHANE, pic, "", opts(cylinderMm = 80f)))
        product(Case("relief", Creations.Kind.RELIEF, pic, "", opts(flatBg = false)))
        product(Case("qr", Creations.Kind.QR, null, "https://example.com", opts()))
        product(Case("keychain", Creations.Kind.NAME, null, "נועה", opts(12f)),
            Case("keychain_plate", Creations.Kind.NAME, null, "נועה", opts(12f, tag = Templates.TagStyle.PLATE)))
        product(Case("cookie", Creations.Kind.COOKIE, fig, "", opts(80f)))
        product(Case("stamp", Creations.Kind.STAMP, null, "נועה", opts(50f)))

        // --- two-half mold of a 3D model
        val solid = model()
        judge("mold2_model", 2) { Crafts.twoPartMold(solid).first }

        File(out, "report.md").writeText(buildString {
            appendLine("# Print products: quality check"); appendLine()
            appendLine("${rows.count { "| PASS |" in it }} passed, ${rows.count { "| FAIL |" in it }} failed"); appendLine()
            appendLine("| product | result | triangles | pieces | size mm | volume cm3 | thinnest wall mm | STL KB | problems |")
            appendLine("|---|---|---|---|---|---|---|---|---|")
            rows.forEach { appendLine(it) }
        })
        assertTrue("Quality problems:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
