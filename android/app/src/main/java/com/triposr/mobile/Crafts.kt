package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.triposr.mobile.Templates.Mask
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * More print products, each drawn as a height sheet seen from above (top and underside per
 * sample, empty where they meet): stencil, magnet, phone stand, photo frame, cake topper,
 * pendant, door sign, bookmark, portrait medal, planter, night light, puzzle, wax seal, pet tag,
 * box with a lid, and a two-half mold of a 3D model.
 */
object Crafts {
    private val LIGHT = floatArrayOf(0.94f, 0.94f, 0.94f)
    private val DARK = floatArrayOf(0.1f, 0.1f, 0.12f)
    private const val NO_SHAPE = "לא נמצאה צורה בתמונה"

    /** Depth source used instead of the depth extra (the JVM quality tests set it). */
    @Volatile var depthHook: ((Bitmap, Int, Int) -> FloatArray)? = null

    fun depthReady() = depthHook != null || Extras.ready(AppState.app, Extras.DEPTH)

    /** Raw depth of [src] at [w]×[h] (larger = nearer). */
    fun depth(src: Bitmap, w: Int, h: Int): FloatArray = depthHook?.invoke(src, w, h) ?: Enhance.depth(AppState.app, src, w, h)

    /** [src] without its background. A picture that already has a transparent background is kept as it is. */
    fun cutout(src: Bitmap): Bitmap {
        if (src.hasAlpha()) {
            val s = Bitmap.createScaledBitmap(src, 48, 48, false)
            val px = IntArray(48 * 48).also { s.getPixels(it, 0, 48, 0, 0, 48, 48) }
            if (px.count { Color.alpha(it) < 128 } > px.size / 30) return src
        }
        return Enhance.removeBackground(AppState.app, src) { AppState.segmenter() }
    }

    /** The sheet being drawn: [w]×[h] samples of [cell] mm, row 0 at the back. */
    class Sheet(val w: Int, val h: Int, val cell: Float) {
        val top = FloatArray(w * h)
        val bot = FloatArray(w * h)
        val dark = BooleanArray(w * h)
        /** Photo colors (ARGB) per sample; 0 = none. */
        var rgb: IntArray? = null

        fun n(mm: Float) = (mm / cell).roundToInt()
        fun solid(i: Int) = top[i] > bot[i]

        /** Adds [by] mm where [m] (corner at [ox], [oy]) is set and the sheet is solid. */
        fun raise(m: Mask, ox: Int, oy: Int, by: Float) {
            for (y in 0 until m.h) for (x in 0 until m.w) if (m.cells[y * m.w + x]) {
                val sx = ox + x; val sy = oy + y
                if (sx in 0 until w && sy in 0 until h && solid(sy * w + sx)) { top[sy * w + sx] += by; dark[sy * w + sx] = true }
            }
        }

        /** [m] centred on ([cx], [cy]). */
        fun raiseCentered(m: Mask, cx: Int, cy: Int, by: Float) = raise(m, cx - m.w / 2, cy - m.h / 2, by)

        fun paste(s: Sheet, ox: Int, oy: Int) {
            for (y in 0 until s.h) for (x in 0 until s.w) {
                val i = (oy + y) * w + ox + x; val j = y * s.w + x
                top[i] = s.top[j]; bot[i] = s.bot[j]; dark[i] = s.dark[j]
                s.rgb?.let { src -> (rgb ?: IntArray(w * h).also { rgb = it })[i] = src[j] }
            }
        }

        fun mirrored() = Sheet(w, h, cell).also { o ->
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x; val j = y * w + w - 1 - x
                o.top[i] = top[j]; o.bot[i] = bot[j]; o.dark[i] = dark[j]
                rgb?.let { src -> (o.rgb ?: IntArray(w * h).also { o.rgb = it })[i] = src[j] }
            }
        }

        /**
         * Joins pieces that are not attached to the largest one, each with a bridge [widthMm]
         * wide and [z] mm high along the shortest way across the gap; [twice] adds a second
         * bridge from the far side of each piece, so it cannot swing.
         */
        fun join(z: Float, widthMm: Float, twice: Boolean = false) {
            val label = IntArray(w * h) { -1 }
            val sizes = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            for (s0 in 0 until w * h) {
                if (!solid(s0) || label[s0] >= 0) continue
                var size = 0; label[s0] = sizes.size; stack.addLast(s0)
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast(); size++
                    val x = i % w; val y = i / w
                    for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1))
                        if (j >= 0 && solid(j) && label[j] < 0) { label[j] = sizes.size; stack.addLast(j) }
                }
                sizes.add(size)
            }
            if (sizes.size < 2) return
            val main = sizes.indices.maxByOrNull { sizes[it] }!!
            val r = (widthMm / 2 / cell).roundToInt().coerceAtLeast(1)
            val bridge = -2  // label of bridge samples
            fun paint(i: Int) {
                for (dy in -r..r) for (dx in -r..r) {
                    val x = i % w + dx; val y = i / w + dy
                    if (x !in 0 until w || y !in 0 until h || dx * dx + dy * dy > r * r) continue
                    val k = y * w + x
                    if (!solid(k)) { top[k] = z; bot[k] = 0f; label[k] = bridge }
                }
            }
            /** Shortest way from the samples [from] across empty samples to a solid sample [to] accepts; returns where it started and ended. */
            fun link(from: (Int) -> Boolean, to: (Int) -> Boolean): Pair<Int, Int>? {
                val prev = IntArray(w * h) { -2 }
                val queue = ArrayDeque<Int>()
                for (i in 0 until w * h) if (from(i)) { prev[i] = -1; queue.addLast(i) }
                while (queue.isNotEmpty()) {
                    val i = queue.removeFirst()
                    val x = i % w; val y = i / w
                    for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                        if (j < 0 || prev[j] != -2) continue
                        if (label[j] >= 0 || label[j] == bridge) {
                            if (!to(j)) continue
                            var k = i; var start = i
                            while (k != -1) { start = k; if (label[k] == -1) paint(k); k = prev[k] }
                            return start to j
                        }
                        prev[j] = i; queue.addLast(j)
                    }
                }
                return null
            }
            val joined = BooleanArray(sizes.size).also { it[main] = true }
            val contact = IntArray(sizes.size) { -1 }  // where each piece's first bridge leaves it
            repeat(sizes.size - 1) {
                val hit = link({ label[it] >= 0 && joined[label[it]] }, { label[it] >= 0 && !joined[label[it]] }) ?: return@repeat
                joined[label[hit.second]] = true; contact[label[hit.second]] = hit.second
            }
            if (twice) for (piece in sizes.indices) {
                val c = contact[piece]; if (c < 0) continue
                var far = 0f
                for (i in 0 until w * h) if (label[i] == piece) far = max(far, hypot((i % w - c % w).toFloat(), (i / w - c / w).toFloat()))
                if (far * cell < 8f) continue  // a small piece holds on one bridge
                link({ label[it] == piece && hypot((it % w - c % w).toFloat(), (it / w - c / w).toFloat()) > far * 0.7f }, { label[it] != piece })
            }
        }

        /** Distance (samples) of each solid sample from the nearest empty one. */
        fun inward() = Templates.distanceOutside(Mask(w, h, BooleanArray(w * h) { !solid(it) }))

        private fun color(i: Int): FloatArray {
            val c = rgb?.get(i) ?: 0
            return when {
                dark[i] -> DARK
                c != 0 -> floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)
                else -> LIGHT
            }
        }

        fun mesh(): Mesh = Heightfield.shaped(top, w, h, cell, bot) { color(it) }

        /** Top view for the gallery. */
        fun thumb(): Bitmap {
            val px = IntArray(w * h) { i ->
                val c = rgb?.get(i) ?: 0
                when {
                    !solid(i) -> Color.rgb(236, 236, 240)
                    dark[i] -> Color.rgb(40, 40, 48)
                    c != 0 -> c or (0xFF shl 24)
                    else -> Color.rgb(150, 150, 168)
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        fun done() = mesh() to thumb()
    }

    private fun beside(a: Sheet, b: Sheet, gapMm: Float): Sheet {
        val gap = a.n(gapMm)
        return Sheet(a.w + gap + b.w, max(a.h, b.h), a.cell).also { it.paste(a, 0, 0); it.paste(b, a.w + gap, 0) }
    }

    /** Filled drawing on a [w]×[h] canvas as a mask. */
    private fun draw(w: Int, h: Int, block: (Canvas, Paint) -> Unit): Mask {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { isAntiAlias = false; color = Color.BLACK; style = Paint.Style.FILL }
        block(Canvas(bmp), paint)
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        return Mask(w, h, BooleanArray(w * h) { Color.alpha(px[it]) > 127 })
    }

    /** [s] as a mask about [heightMm] tall, shrunk to fit [maxWidthMm]. */
    fun fitText(s: String, heightMm: Float, maxWidthMm: Float, cell: Float): Mask {
        var m = Creations.textMask(s.trim(), (heightMm * 1.4f / cell).toInt().coerceAtLeast(10)).cropped()
        val maxW = (maxWidthMm / cell).toInt().coerceAtLeast(6)
        if (m.w > maxW) m = Templates.scaleTo(m, maxW)
        return m
    }

    private fun rotated(m: Mask) = Mask(m.h, m.w, BooleanArray(m.w * m.h) { i -> m[i / m.h, m.h - 1 - i % m.h] })

    /** A picture cut to its object: colors, the object's mask and, when asked, depth (0..1, 1 = nearest). */
    class Pic(val w: Int, val h: Int, val px: IntArray, val mask: Mask, val depth: FloatArray?)

    /**
     * [src] with [samples] along its longest side, cropped to its shape: the object (background
     * removed) or, for a drawing, the dark lines. [whole] keeps every part, not only the largest.
     */
    private fun pic(src: Bitmap, samples: Int, removeBg: Boolean, whole: Boolean = false, depth: Boolean = false): Pic {
        val app = AppState.app
        val img = if (removeBg) cutout(src) else src
        val s = samples.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * s).toInt().coerceAtLeast(8); val h = (img.height * s).toInt().coerceAtLeast(8)
        val px = IntArray(w * h).also { Bitmap.createScaledBitmap(img, w, h, true).getPixels(it, 0, w, 0, 0, w, h) }
        var m = Mask(w, h, BooleanArray(w * h) {
            if (removeBg) Color.alpha(px[it]) > 127 else Heightfield.luminance(intArrayOf(px[it]))[0] < 0.5f
        })
        if (!whole) m = Templates.largest(m)
        if (m.count() < 40) throw IllegalStateException(tr(NO_SHAPE))
        val d = if (depth && depthReady())
            Heightfield.normalizeDepth(depth(src, w, h), FloatArray(w * h) { if (m.cells[it]) 1f else 0f }) else null
        val b = m.bounds()!!
        val cw = b[2] - b[0] + 1; val ch = b[3] - b[1] + 1
        fun at(i: Int) = (b[1] + i / cw) * w + b[0] + i % cw
        return Pic(cw, ch, IntArray(cw * ch) { px[at(it)] or (0xFF shl 24) }, m.cropped(), d?.let { all -> FloatArray(cw * ch) { all[at(it)] } })
    }

    /** Transparent pixels become white (a cut-out picture used as a photo). */
    private fun onWhite(px: IntArray): IntArray { for (i in px.indices) if (Color.alpha(px[i]) < 128) px[i] = Color.WHITE; return px }

    /** [src] scaled so its longest side has [samples] samples: pixels, width, height. */
    private fun scaled(src: Bitmap, samples: Int): Triple<IntArray, Int, Int> {
        val s = samples.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * s).toInt().coerceAtLeast(8); val h = (src.height * s).toInt().coerceAtLeast(8)
        return Triple(onWhite(IntArray(w * h).also { Bitmap.createScaledBitmap(src, w, h, true).getPixels(it, 0, w, 0, 0, w, h) }), w, h)
    }

    /** How raised each sample is, 0..1: the depth extra when it is installed, else dark = raised. */
    private fun raisedBy(src: Bitmap, px: IntArray, w: Int, h: Int): FloatArray {
        val app = AppState.app
        if (depthReady()) return Heightfield.normalizeDepth(depth(src, w, h))
        val lum = Heightfield.luminance(px)
        return FloatArray(w * h) { 1f - lum[it] }
    }

    // ------------------------------------------------------------------ flat shapes

    /** Thin plate with the shape cut out, for spraying or dusting through. */
    fun stencil(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val p = pic(src, 240, o.flatBackground, whole = true)
        val cell = o.sizeMm / maxOf(p.w, p.h)
        val pad = (8f / cell).roundToInt()
        val s = Sheet(p.w + 2 * pad, p.h + 2 * pad, cell)
        for (i in s.top.indices) s.top[i] = if (p.mask[i % s.w - pad, i / s.w - pad]) 0f else 1.2f
        s.join(1.2f, 1.6f, twice = true)  // parts enclosed by the cut would fall out: hold them with bridges
        return s.done()
    }

    /** The object as a colored plaque with a pocket underneath for a round 10 mm magnet. */
    fun magnet(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val p = pic(src, 200, true, depth = true)
        val cell = o.sizeMm / maxOf(p.w, p.h)
        val s = Sheet(p.w + 2, p.h + 2, cell)
        val rgb = IntArray(s.w * s.h).also { s.rgb = it }
        for (y in 0 until p.h) for (x in 0 until p.w) if (p.mask.cells[y * p.w + x]) {
            val i = (y + 1) * s.w + x + 1
            s.top[i] = 3.2f + 2.5f * (p.depth?.get(y * p.w + x) ?: 0f); rgb[i] = p.px[y * p.w + x]
        }
        val edge = s.inward()
        val best = edge.indices.maxByOrNull { edge[it] }!!
        if (edge[best] * cell >= 6.5f) {
            val bx = best % s.w; val by = best / s.w; val r = 5.2f / cell
            for (i in s.top.indices) if (hypot((i % s.w - bx).toFloat(), (i / s.w - by).toFloat()) <= r) s.bot[i] = 2.3f
        }
        return s.done()
    }

    /** The object as a pendant with a ring on top; [Creations.Options.alt] makes a mirrored pair (earrings). */
    fun pendant(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val p = pic(src, 150, true)
        val cell = o.sizeMm / maxOf(p.w, p.h)
        val ring = 3.2f / cell; val hole = 1.5f / cell
        val side = ring.toInt() + 2; val above = (ring + hole).toInt() + 2
        val s = Sheet(p.w + 2 * side, p.h + above + 2, cell)
        val rgb = IntArray(s.w * s.h).also { s.rgb = it }
        for (y in 0 until p.h) for (x in 0 until p.w) if (p.mask.cells[y * p.w + x]) {
            val i = (y + above) * s.w + x + side
            s.top[i] = 2.5f; rgb[i] = p.px[y * p.w + x]
        }
        // The ring sits on the highest point of the shape.
        var sum = 0; var cnt = 0
        for (y in 0 until min(3, p.h)) for (x in 0 until p.w) if (p.mask.cells[y * p.w + x]) { sum += x; cnt++ }
        val cx = side + sum / cnt.coerceAtLeast(1).toFloat(); val cy = above - hole
        for (i in s.top.indices) {
            val d = hypot(i % s.w - cx, i / s.w - cy)
            if (d < hole) s.top[i] = 0f else if (d <= ring) s.top[i] = 2.5f
        }
        return (if (o.alt) beside(s, s.mirrored(), 4f) else s).done()
    }

    /** Cake topper: the text (on a thin backing that joins the letters) or the picture's shape, on stakes. */
    fun topper(src: Bitmap?, text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        val stakeMm = 70f
        if (text.isNotBlank()) {
            val cell = 0.4f
            val m = Templates.scaleTo(Creations.textMask(text.trim(), 120).cropped(), (o.sizeMm / cell).toInt())
            val pad = (2.5f / cell).roundToInt()
            val bar = (3f / cell).roundToInt(); val overlap = (1.2f / cell).roundToInt()
            val s = Sheet(m.w + 2 * pad, pad + m.h - overlap + bar + (stakeMm / cell).roundToInt(), cell)
            val placed = Mask(s.w, s.h, BooleanArray(s.w * s.h) { m[it % s.w - pad, it / s.w - pad] })
            val backing = Templates.dilate(placed, 1.6f / cell)
            val barTop = pad + m.h - overlap
            val stakes = if (m.w * cell >= 60f) listOf(pad + m.w / 4, pad + m.w * 3 / 4) else listOf(pad + m.w / 2)
            val half = (2f / cell).roundToInt()
            for (i in s.top.indices) {
                val x = i % s.w; val y = i / s.w
                when {
                    placed.cells[i] -> { s.top[i] = 2.8f; s.dark[i] = true }
                    y in barTop until barTop + bar && x in pad until pad + m.w -> s.top[i] = 2.8f
                    y >= barTop && stakes.any { kotlin.math.abs(x - it) <= half } -> s.top[i] = 2.8f
                    backing.cells[i] -> s.top[i] = 1.4f
                }
            }
            s.join(1.4f, 2.4f)  // a letter standing apart (a yod, a dot) is tied to the rest
            return s.done()
        }
        val p = pic(src ?: throw IllegalStateException(tr("צריך טקסט או תמונה")), 200, true)
        val shape = Templates.fillHoles(p.mask)
        val cell = o.sizeMm / maxOf(p.w, p.h)
        val s = Sheet(p.w + 2, p.h + 1 + (stakeMm / cell).roundToInt(), cell)
        val rgb = IntArray(s.w * s.h).also { s.rgb = it }
        for (y in 0 until p.h) for (x in 0 until p.w) if (shape.cells[y * p.w + x]) {
            val i = (y + 1) * s.w + x + 1
            s.top[i] = 2.4f; rgb[i] = p.px[y * p.w + x]
        }
        // One stake under the middle of the shape's lower part.
        var sum = 0L; var cnt = 0
        for (y in p.h * 9 / 10 until p.h) for (x in 0 until p.w) if (shape.cells[y * p.w + x]) { sum += x; cnt++ }
        val cx = 1 + (sum / cnt.coerceAtLeast(1)).toInt(); val half = (2f / cell).roundToInt()
        var start = p.h
        for (y in p.h - 1 downTo 0) if (shape[cx - 1, y]) { start = y; break }
        for (y in (start - (4f / cell).roundToInt()).coerceAtLeast(0) + 1 until s.h) for (x in cx - half..cx + half)
            if (x in 0 until s.w) s.top[y * s.w + x] = 2.4f
        return s.done()
    }

    /** Door or room sign: big raised letters on a plate (a second color after a filament change). */
    fun sign(text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        if (text.isBlank()) throw IllegalStateException(tr("צריך להכניס שם או טקסט"))
        val cell = 0.5f
        var mask = Creations.textMask(text.trim(), (o.sizeMm * 1.4f / cell).toInt())
        // Longer than a printer bed: smaller letters, so the whole sign is at most 200 mm.
        val wide = mask.cropped().w * cell + 12f
        if (wide > 200f) mask = Creations.textMask(text.trim(), (o.sizeMm * 1.4f / cell * 188f / (wide - 12f)).toInt())
        val m = mask.cropped()
        val thumb = Bitmap.createBitmap(IntArray(m.w * m.h) { if (m.cells[it]) Color.rgb(40, 40, 48) else Color.rgb(236, 236, 240) },
            m.w, m.h, Bitmap.Config.ARGB_8888)
        return Templates.nameTag(mask, cell, Templates.TagStyle.PLATE, false, marginMm = 6f) to thumb
    }

    /** Bookmark: a thin strip with the text along it and a hole for a tassel. */
    fun bookmark(text: String): Pair<Mesh, Bitmap> {
        if (text.isBlank()) throw IllegalStateException(tr("צריך להכניס שם או טקסט"))
        val cell = 0.4f
        val w = (30f / cell).roundToInt(); val h = (140f / cell).roundToInt()
        val s = Sheet(w + 2, h + 2, cell)
        val body = draw(w, h) { c, p -> c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 4f / cell, 4f / cell, p) }
        val holeY = 1 + s.n(8f); val holeR = 2f / cell
        for (i in s.top.indices) {
            val x = i % s.w; val y = i / s.w
            if (body[x - 1, y - 1] && hypot(x - s.w / 2f, (y - holeY).toFloat()) > holeR) s.top[i] = 1f
        }
        s.raiseCentered(rotated(fitText(text, 13f, 105f, cell)), s.w / 2, 1 + s.n(16f) + (h - s.n(22f)) / 2, 0.6f)
        return s.done()
    }

    /** Bone-shaped pet tag: the name, the phone number under it, and a hole for the collar ring. */
    fun petTag(name: String, phone: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        if (name.isBlank()) throw IllegalStateException(tr("צריך להכניס שם או טקסט"))
        val cell = 0.25f
        val w = (o.sizeMm / cell).roundToInt(); val h = (w * 0.55f).roundToInt(); val r = h * 0.3f
        val body = draw(w, h) { c, p ->
            c.drawRect(r, h * 0.2f, w - r, h * 0.8f, p)
            for (cx in listOf(r, w - r)) for (cy in listOf(r, h - r)) c.drawCircle(cx, cy, r, p)
        }
        val s = Sheet(w + 2, h + 2, cell)
        val holeR = 1.6f / cell
        for (i in s.top.indices) {
            val x = i % s.w; val y = i / s.w
            if (body[x - 1, y - 1] && hypot(x - 1 - r, y - 1 - r) > holeR) s.top[i] = 2.2f
        }
        val x0 = 2.1f * r; val avail = w - x0 - 0.5f * r
        val cx = 1 + (x0 + avail / 2).roundToInt()
        if (phone.isBlank()) s.raiseCentered(fitText(name, h * 0.34f * cell, avail * cell, cell), cx, 1 + h / 2, 0.8f)
        else {
            s.raiseCentered(fitText(name, h * 0.26f * cell, avail * cell, cell), cx, 1 + (h * 0.38f).roundToInt(), 0.8f)
            s.raiseCentered(fitText(phone, h * 0.16f * cell, avail * cell, cell), cx, 1 + (h * 0.66f).roundToInt(), 0.8f)
        }
        return s.done()
    }

    /**
     * Wax seal: a round head with the design sunk into it (mirrored, so the wax shows it raised
     * and the right way round) on a handle. Printed standing on the handle.
     */
    fun seal(src: Bitmap?, text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        val cell = 0.2f
        val handleMm = 32f; val headMm = 6f
        val r = o.sizeMm / 2 / cell; val rh = 7f / cell
        val n = (2 * r).toInt() + 3; val c = n / 2f
        val s = Sheet(n, n, cell)
        for (i in s.top.indices) {
            val d = hypot(i % n - c, i / n - c)
            if (d <= r) { s.top[i] = handleMm + headMm; s.bot[i] = if (d <= rh) 0f else max(0f, handleMm - (r - d) * cell) }
        }
        var m = (if (text.isNotBlank()) Creations.textMask(text.trim(), 120)
            else pic(src ?: throw IllegalStateException(tr("צריך טקסט או תמונה")), 160, false, whole = true).mask).cropped().mirrored()
        val side = (r - 2.5f / cell) * 1.41f
        m = Templates.scaleTo(m, min(side, side * m.w / m.h).toInt().coerceAtLeast(6))
        s.raiseCentered(m, n / 2, n / 2, -1.3f)
        s.dark.fill(false)  // the design shows by its depth; the color would run down the handle
        return s.done()
    }

    /** Square box with a lid that drops onto a ledge inside the rim; the text is raised on the lid. */
    fun box(text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        val cell = max(0.4f, o.sizeMm / 170f)
        val side = (o.sizeMm / cell).roundToInt(); val height = o.sizeMm * 0.6f
        val box = Sheet(side + 2, side + 2, cell)
        for (y in 0 until side) for (x in 0 until side) {
            val d = minOf(x, y, side - 1 - x, side - 1 - y) * cell
            box.top[(y + 1) * box.w + x + 1] = if (d < 1.6f) height else if (d < 3.2f) height - 2.6f else 1.6f
        }
        val lidSide = side - 2 * (1.6f / cell).roundToInt() - 1
        val lid = Sheet(lidSide + 2, side + 2, cell)
        val notch = 6f / cell
        for (y in 0 until lidSide) for (x in 0 until lidSide)
            if (hypot(x - lidSide / 2f, y - lidSide.toFloat()) > notch) lid.top[(y + 1) * lid.w + x + 1] = 2.2f
        if (text.isNotBlank()) lid.raiseCentered(fitText(text, 12f, lidSide * cell - 12f, cell), lid.w / 2, 1 + lidSide * 2 / 5, 1f)
        return beside(box, lid, 5f).done()
    }

    /** Frame for a 2:3 photo ([Creations.Options.sizeMm] = its long side) with a pocket at the back and a caption. */
    fun frame(text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        val cell = 0.6f
        val long = o.sizeMm; val short = long * 2 / 3
        val border = 12f; val lower = 16f
        val s = Sheet(((long + 2 * border) / cell).roundToInt() + 2, ((short + border + lower) / cell).roundToInt() + 2, cell)
        for (i in s.top.indices) {
            val x = (i % s.w - 1) * cell; val y = (i / s.w - 1) * cell
            if (x < 0 || y < 0 || x > long + 2 * border || y > short + border + lower) continue
            val px = x - border; val py = y - border  // position over the photo
            when {
                px > 3f && px < long - 3f && py > 3f && py < short - 3f -> {}  // window
                px > -0.5f && px < long + 0.5f && py > -0.5f && py < short + 0.5f -> { s.top[i] = 6f; s.bot[i] = 3.5f }
                else -> s.top[i] = 6f
            }
        }
        if (text.isNotBlank()) s.raiseCentered(fitText(text, 7f, long, cell), s.w / 2, 1 + ((border + short + lower / 2) / cell).roundToInt(), 0.8f)
        return s.done()
    }

    /** Phone stand: the side view drawn and made 60 mm wide; printed on its side, the name raised on it. */
    fun stand(text: String): Pair<Mesh, Bitmap> {
        val cell = 0.4f
        val w = (96f / cell).roundToInt(); val h = (86f / cell).roundToInt()
        val profile = draw(w, h) { c, p ->
            // Base, a lip in front, a slot for the phone, and the leaning back as a solid wedge.
            val pts = floatArrayOf(0f, 0f, 96f, 0f, 96f, 7f, 56f, 86f, 48f, 86f, 20f, 7f, 6f, 7f, 6f, 18f, 0f, 18f)
            val path = Path()
            for (k in 0 until pts.size / 2) {
                val x = pts[k * 2] / cell; val y = h - pts[k * 2 + 1] / cell
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); c.drawPath(path, p)
        }
        val s = Sheet(w + 2, h + 2, cell)
        for (i in s.top.indices) if (profile[i % s.w - 1, i / s.w - 1]) s.top[i] = 60f
        if (text.isNotBlank()) s.raiseCentered(fitText(text, 11f, 34f, cell), 1 + (60f / cell).roundToInt(), 1 + h - (26f / cell).roundToInt(), 0.8f)
        return s.done()
    }

    // ------------------------------------------------------------------ photo products

    /** Round medal with the face (or object) as a relief, a rim and a caption. Needs the depth extra. */
    fun medal(src: Bitmap, text: String, o: Creations.Options): Pair<Mesh, Bitmap> {
        val app = AppState.app
        if (!depthReady()) throw IllegalStateException(tr("מדליון צריך את \"מפת עומק\" מהשיפורים בהגדרות"))
        val cell = 0.3f
        val n = (o.sizeMm / cell).roundToInt()
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val px = IntArray(n * n).also { Bitmap.createScaledBitmap(square, n, n, true).getPixels(it, 0, n, 0, 0, n, n) }
        val keep = if (o.flatBackground) {
            val cut = Bitmap.createScaledBitmap(cutout(square), n, n, true)
            IntArray(n * n).also { cut.getPixels(it, 0, n, 0, 0, n, n) }.map { Color.alpha(it) / 255f }.toFloatArray()
        } else null
        val depth = Heightfield.normalizeDepth(depth(square, n, n), keep)
        val s = Sheet(n + 2, n + 2, cell)
        val rgb = IntArray(s.w * s.h).also { s.rgb = it }
        val c = n / 2f; val r = n / 2f; val rim = 3f / cell
        val band = text.isNotBlank()
        for (y in 0 until n) for (x in 0 until n) {
            val d = hypot(x - c, y - c); val i = (y + 1) * s.w + x + 1; val j = y * n + x
            when {
                d > r -> {}
                d > r - rim -> s.top[i] = 6.5f
                band && y > c + 0.5f * r -> s.top[i] = 3f
                else -> {
                    s.top[i] = 2f + 4f * depth[j] * (keep?.get(j) ?: 1f)
                    if ((keep?.get(j) ?: 1f) > 0.5f) rgb[i] = px[j] or (0xFF shl 24)
                }
            }
        }
        if (band) s.raiseCentered(fitText(text, o.sizeMm * 0.11f, o.sizeMm * 0.55f, cell), s.w / 2, 1 + (c + 0.68f * r).roundToInt(), 1.2f)
        return s.done()
    }

    /** Planter: the picture as a relief around a pot (repeated to go all the way round). */
    fun planter(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val circumference = (Math.PI * o.sizeMm).toFloat()
        val cell = circumference / 420
        val h = (o.sizeMm * 0.9f / cell).toInt()
        val tiles = (420f / (h * src.width / src.height.toFloat())).roundToInt().coerceIn(1, 8)
        val tw = 420 / tiles; val w = tw * tiles
        val px = onWhite(IntArray(tw * h).also { Bitmap.createScaledBitmap(src, tw, h, true).getPixels(it, 0, tw, 0, 0, tw, h) })
        val up = raisedBy(src, px, tw, h)
        val band = (3f / cell).roundToInt()
        val heights = FloatArray(w * h) { i ->
            val y = i / w
            if (y < band || y >= h - band) 4.2f else 2f + 2.2f * up[y * tw + i % w % tw]
        }
        val colors = FloatArray(w * h * 3) { k ->
            val c = px[(k / 3 / w) * tw + k / 3 % w % tw]
            (when (k % 3) { 0 -> Color.red(c); 1 -> Color.green(c); else -> Color.blue(c) }) / 255f
        }
        return Heightfield.pot(heights, w, h, circumference, 2.4f, colors) to src
    }

    /** Night light: a lithophane and, next to it, a base with a slot that holds it upright in front of a light. */
    fun nightLight(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val (px, w, h) = scaled(src, 280)
        val cell = 100f / (maxOf(w, h) - 1)
        val plate = Heightfield.lithophaneHeights(px, w, h, cell, 0.8f, 3f, 3f)
        val gap = (6f / cell).roundToInt(); val base = (26f / cell).roundToInt()
        val s = Sheet(w, h + gap + base, cell)
        System.arraycopy(plate, 0, s.top, 0, w * h)
        val mid = h + gap + base / 2f
        for (y in h + gap until s.h) for (x in 0 until w) s.top[y * w + x] = if (kotlin.math.abs(y - mid) * cell <= 1.7f) 5f else 12f
        return s.mesh() to src
    }

    /** Jigsaw puzzle: the picture as a relief, cut into interlocking pieces that print apart. */
    fun puzzle(src: Bitmap, o: Creations.Options): Pair<Mesh, Bitmap> {
        val cell = 0.45f
        val (px, w, h) = scaled(src, (o.sizeMm / cell).roundToInt())
        val up = raisedBy(src, px, w, h)
        val cols = if (o.alt) 4 else 3
        val rows = (cols * h / w.toFloat()).roundToInt().coerceAtLeast(2)
        val cw = w.toFloat() / cols; val ch = h.toFloat() / rows
        val k = min(cw, ch); val r = 0.1118f * k
        val cuts = draw(w, h) { c, p ->
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f
            val path = Path()
            for (i in 1 until cols) for (j in 0 until rows) {
                val x = i * cw; val y0 = j * ch; val mid = y0 + ch / 2; val out = (i + j) % 2 == 0
                val cx = x + (if (out) 0.1f else -0.1f) * k
                path.moveTo(x, y0); path.lineTo(x, mid - 0.05f * k)
                path.arcTo(RectF(cx - r, mid - r, cx + r, mid + r), if (out) -153.43f else -26.57f, if (out) 306.86f else -306.86f, false)
                path.lineTo(x, y0 + ch)
            }
            for (j in 1 until rows) for (i in 0 until cols) {
                val y = j * ch; val x0 = i * cw; val mid = x0 + cw / 2; val out = (i + j) % 2 == 0
                val cy = y + (if (out) 0.1f else -0.1f) * k
                path.moveTo(x0, y); path.lineTo(mid - 0.05f * k, y)
                path.arcTo(RectF(mid - r, cy - r, mid + r, cy + r), if (out) -116.57f else 116.57f, if (out) -306.86f else 306.86f, false)
                path.lineTo(x0 + cw, y)
            }
            c.drawPath(path, p)
        }
        val s = Sheet(w, h, cell)
        s.rgb = IntArray(w * h) { px[it] or (0xFF shl 24) }
        for (i in s.top.indices) s.top[i] = if (cuts.cells[i]) 0f else 2.4f + 1.8f * up[i]
        return s.mesh() to src
    }

    // ------------------------------------------------------------------ mold of a 3D model

    /**
     * Two-half mold of a 3D model, split front/back through its middle: each half is a block
     * with that side of the model sunk into it, pins and sockets to line the halves up, and a
     * pouring channel from the top. Parts hidden behind others (undercuts) are filled in.
     */
    fun twoPartMold(mesh: Mesh): Pair<Mesh, Bitmap> {
        val p = mesh.positions
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until mesh.vertexCount) for (c in 0..2) { lo[c] = min(lo[c], p[v * 3 + c]); hi[c] = max(hi[c], p[v * 3 + c]) }
        val cell = 0.5f
        val mm = 80f / maxOf(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]).coerceAtLeast(1e-6f)
        val ow = ((hi[0] - lo[0]) * mm / cell).toInt() + 2; val oh = ((hi[1] - lo[1]) * mm / cell).toInt() + 2
        val front = FloatArray(ow * oh) { -Float.MAX_VALUE }; val back = FloatArray(ow * oh) { Float.MAX_VALUE }
        fun gx(v: Int) = (p[v * 3] - lo[0]) * mm / cell
        fun gy(v: Int) = (hi[1] - p[v * 3 + 1]) * mm / cell
        fun gz(v: Int) = p[v * 3 + 2] * mm
        fun hit(x: Int, y: Int, z: Float) {
            if (x !in 0 until ow || y !in 0 until oh) return
            val i = y * ow + x
            if (z > front[i]) front[i] = z
            if (z < back[i]) back[i] = z
        }
        val idx = mesh.indices
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            val ax = gx(a); val ay = gy(a); val bx = gx(b); val by = gy(b); val cx = gx(c); val cy = gy(c)
            for (v in intArrayOf(a, b, c)) hit(gx(v).roundToInt(), gy(v).roundToInt(), gz(v))
            val area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if (area == 0f) continue
            for (y in kotlin.math.ceil(minOf(ay, by, cy)).toInt()..maxOf(ay, by, cy).toInt())
                for (x in kotlin.math.ceil(minOf(ax, bx, cx)).toInt()..maxOf(ax, bx, cx).toInt()) {
                    val w0 = ((bx - x) * (cy - y) - (cx - x) * (by - y)) / area
                    val w1 = ((cx - x) * (ay - y) - (ax - x) * (cy - y)) / area
                    val w2 = 1f - w0 - w1
                    if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                    hit(x, y, w0 * gz(a) + w1 * gz(b) + w2 * gz(c))
                }
        }
        val mid = (lo[2] + hi[2]) / 2 * mm
        val margin = (10f / cell).roundToInt()
        val w = ow + 2 * margin; val h = oh + 2 * margin
        // The pouring channel enters above the model's highest point.
        var firstRow = 0; var sum = 0L; var cnt = 0
        search@ for (y in 0 until oh) for (x in 0 until ow) if (front[y * ow + x] > -Float.MAX_VALUE) { firstRow = y; break@search }
        for (y in firstRow until min(oh, firstRow + 4)) for (x in 0 until ow) if (front[y * ow + x] > -Float.MAX_VALUE) { sum += x; cnt++ }
        val pourX = margin + sum / cnt.coerceAtLeast(1).toFloat(); val pourR = 4f
        val pourEnd = margin + firstRow + (3f / cell).roundToInt()
        val pins = listOf(margin / 2f to h - 1 - margin / 2f, w - 1 - margin / 2f to margin / 2f)

        fun half(isFront: Boolean): Sheet {
            val depth = FloatArray(w * h) { i ->
                val x = i % w - margin; val y = i / w - margin
                if (x !in 0 until ow || y !in 0 until oh || front[y * ow + x] == -Float.MAX_VALUE) 0f
                else max(0f, if (isFront) front[y * ow + x] - mid else mid - back[y * ow + x])
            }
            val block = (depth.maxOrNull() ?: 0f) + 4f
            val s = Sheet(w, h, cell)
            for (i in s.top.indices) {
                val x = i % w; val y = i / w
                var z = block - depth[i]
                val dx = kotlin.math.abs(x - pourX) * cell
                if (y <= pourEnd && dx < pourR) z = min(z, block - sqrt(pourR * pourR - dx * dx))
                for ((px, py) in pins) {
                    val d = hypot(x - px, y - py) * cell
                    if (isFront && d < 2.5f) z = block + sqrt(2.5f * 2.5f - d * d)
                    if (!isFront && d < 2.9f) z = block - sqrt(2.9f * 2.9f - d * d)
                }
                s.top[i] = z
            }
            return s
        }
        val s = beside(half(true), half(false).mirrored(), 5f)
        val outline = Bitmap.createBitmap(IntArray(ow * oh) { if (front[it] > -Float.MAX_VALUE) Color.rgb(40, 40, 48) else Color.rgb(236, 236, 240) },
            ow, oh, Bitmap.Config.ARGB_8888)
        return s.mesh() to outline
    }
}
