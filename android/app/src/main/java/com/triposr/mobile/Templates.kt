package com.triposr.mobile

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Print templates built from 2D masks (text rendered by the caller, or a silhouette): name tags
 * and keychains, cookie cutters, stamps and captioned QR plates. Pure Kotlin on boolean grids
 * so it runs and is tested on the JVM; every result is a closed solid from [Heightfield.columns].
 */
object Templates {
    /** A boolean image: [w]×[h] cells of [cellMm] millimetres. */
    class Mask(val w: Int, val h: Int, val cells: BooleanArray) {
        operator fun get(x: Int, y: Int) = x in 0 until w && y in 0 until h && cells[y * w + x]
        fun count() = cells.count { it }

        /** Copy placed with [pad] empty cells on every side. */
        fun padded(pad: Int): Mask {
            val nw = w + 2 * pad; val nh = h + 2 * pad
            return Mask(nw, nh, BooleanArray(nw * nh) { i -> this[i % nw - pad, i / nw - pad] })
        }

        fun mirrored() = Mask(w, h, BooleanArray(w * h) { i -> cells[i / w * w + (w - 1 - i % w)] })

        /** Bounding box of the set cells (x0, y0, x1, y1), or null when empty. */
        fun bounds(): IntArray? {
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            for (y in 0 until h) for (x in 0 until w) if (cells[y * w + x]) {
                x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
            }
            return if (x1 < 0) null else intArrayOf(x0, y0, x1, y1)
        }

        fun cropped(): Mask {
            val b = bounds() ?: return this
            val nw = b[2] - b[0] + 1; val nh = b[3] - b[1] + 1
            return Mask(nw, nh, BooleanArray(nw * nh) { i -> cells[(b[1] + i / nw) * w + b[0] + i % nw] })
        }
    }

    private val DARK = floatArrayOf(0.1f, 0.1f, 0.12f)
    private val LIGHT = floatArrayOf(0.94f, 0.94f, 0.94f)

    // ------------------------------------------------------------------ name tag / keychain

    enum class TagStyle { PLATE, OUTLINE, ROUND }

    /**
     * Raised text on a base. PLATE: a rounded rectangle; OUTLINE: the base follows the letters
     * (2.5 mm around them), the popular "name keychain" look; ROUND: a disc, like a pet tag. [keyring] adds a ring with a 5 mm
     * hole on the left. Text is [textMm] above the [baseMm] base, so a filament change at the
     * base height prints it in a second colour.
     */
    fun nameTag(text: Mask, cellMm: Float, style: TagStyle, keyring: Boolean,
                baseMm: Float = 2.4f, textMm: Float = 1.6f, marginMm: Float = 2.5f): Mesh {
        val margin = (marginMm / cellMm).roundToInt()
        val holeR = 2.5f / cellMm; val ringR = holeR + 2.2f / cellMm
        val extraLeft = if (keyring) (2 * ringR).roundToInt() else 0
        var t = text.cropped().padded(margin + 1)
        if (style == TagStyle.ROUND) {
            // A disc around the text: pad to a square whose inscribed circle holds the text box.
            val c = text.cropped()
            val side = (hypot(c.w.toFloat(), c.h.toFloat()) + 2 * margin + 2).toInt()
            t = Mask(side, side, BooleanArray(side * side) { i -> c[i % side - (side - c.w) / 2, i / side - (side - c.h) / 2] })
        }
        val w = t.w + extraLeft; val h = t.h
        val letters = Mask(w, h, BooleanArray(w * h) { i -> t[i % w - extraLeft, i / w] })
        val base = when (style) {
            TagStyle.OUTLINE -> dilate(letters, margin.toFloat())
            TagStyle.ROUND -> {
                val cx0 = extraLeft + t.w / 2f; val r = t.w / 2f - 1
                Mask(w, h, BooleanArray(w * h) { i -> hypot(i % w + 0.5f - cx0, i / w + 0.5f - h / 2f) <= r })
            }
            TagStyle.PLATE -> {
                val r = min(3f / cellMm, h / 2f - 1)
                val x0 = extraLeft + 1f; val x1 = w - 2f; val y0 = 1f; val y1 = h - 2f
                Mask(w, h, BooleanArray(w * h) { i -> inRoundRect(i % w + 0.5f, i / w + 0.5f, x0, y0, x1, y1, r) })
            }
        }
        // Key ring: a disc joined to the base, with the hole punched through everything.
        val cx = ringR + 1; val cy = h / 2f
        val solid = BooleanArray(w * h) { i ->
            val x = i % w + 0.5f; val y = i / w + 0.5f
            base.cells[i] || (keyring && hypot(x - cx, y - cy) <= ringR)
        }
        if (keyring) connect(solid, base, h, cx, cy, holeR + 1.2f / cellMm)
        val heights = FloatArray(w * h) { i ->
            val x = i % w + 0.5f; val y = i / w + 0.5f
            when {
                keyring && hypot(x - cx, y - cy) <= holeR -> 0f
                !solid[i] -> 0f
                letters.cells[i] -> baseMm + textMm
                else -> baseMm
            }
        }
        return Heightfield.columns(heights, w, h, cellMm) { if (letters.cells[it]) DARK else LIGHT }
    }

    /** A bar from the ring's centre to the base on each row around the centre, so they are one part. */
    private fun connect(solid: BooleanArray, base: Mask, h: Int, cx: Float, cy: Float, half: Float) {
        val w = base.w
        for (y in max(0, (cy - half).toInt()) until min(h, (cy + half).toInt() + 1)) {
            var end = cx.toInt()
            while (end < w && !base.cells[y * w + end]) end++
            if (end < w) for (x in cx.toInt() until end) solid[y * w + x] = true
        }
    }

    private fun inRoundRect(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float, r: Float): Boolean {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false
        val dx = max(0f, max(x0 + r - x, x - (x1 - r)))
        val dy = max(0f, max(y0 + r - y, y - (y1 - r)))
        return dx * dx + dy * dy <= r * r
    }

    // ------------------------------------------------------------------ cookie cutter and stamp

    /**
     * Cookie cutter for a silhouette: a [bladeMm] wall [heightMm] tall right outside the shape,
     * with a [flangeMm] lip at the bottom for strength and grip. Holes in the shape are filled
     * and only the largest part is kept (a cutter is one closed loop).
     */
    fun cookieCutter(shape: Mask, cellMm: Float, bladeMm: Float = 0.9f, heightMm: Float = 14f,
                     flangeMm: Float = 3.5f, flangeHeightMm: Float = 1.8f): Mesh {
        val pad = ((bladeMm + flangeMm) / cellMm).toInt() + 2
        val s = fillHoles(largest(shape.cropped())).padded(pad)
        val d = distanceOutside(s)
        val blade = bladeMm / cellMm; val lip = (bladeMm + flangeMm) / cellMm
        val heights = FloatArray(s.w * s.h) { i ->
            when {
                s.cells[i] -> 0f
                d[i] <= blade -> heightMm
                d[i] <= lip -> flangeHeightMm
                else -> 0f
            }
        }
        return Heightfield.columns(heights, s.w, s.h, cellMm) { LIGHT }
    }

    /**
     * Stamp for cookies, clay or wax: the [design] raised [reliefMm] on a [baseMm] block that
     * follows its outline. Mirrored, so the print reads the right way round.
     */
    fun stamp(design: Mask, cellMm: Float, baseMm: Float = 6f, reliefMm: Float = 2.5f, marginMm: Float = 3f): Mesh {
        val margin = (marginMm / cellMm).roundToInt()
        val m = design.cropped().mirrored().padded(margin + 1)
        val base = fillHoles(dilate(m, margin.toFloat()))
        val heights = FloatArray(m.w * m.h) { i ->
            when {
                m.cells[i] -> baseMm + reliefMm
                base.cells[i] -> baseMm
                else -> 0f
            }
        }
        return Heightfield.columns(heights, m.w, m.h, cellMm) { if (m.cells[it]) DARK else LIGHT }
    }

    // ------------------------------------------------------------------ QR with a caption

    /**
     * QR plate with an optional caption under it (e.g. "Wi-Fi"). Each module is [k] cells so the
     * text can share the grid.
     */
    fun qrWithCaption(modules: BooleanArray, n: Int, caption: Mask?, moduleMm: Float, k: Int,
                      plateMm: Float = 2f, raiseMm: Float = 1f): Mesh {
        val cell = moduleMm / k
        val qw = n * k
        val cap = caption?.cropped()?.let { c -> if (c.w > qw - 2 * k) scaleTo(c, qw - 2 * k) else c }
        val capH = cap?.let { it.h + k } ?: 0
        val w = qw; val h = qw + capH
        val raised = BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (y < qw) modules[(y / k) * n + x / k]
            else cap != null && cap[x - (w - cap.w) / 2, y - qw]
        }
        val heights = FloatArray(w * h) { if (raised[it]) plateMm + raiseMm else plateMm }
        return Heightfield.columns(heights, w, h, cell) { if (raised[it]) DARK else LIGHT }
    }

    /** Nearest-neighbour downscale so the mask is [width] cells wide. */
    fun scaleTo(m: Mask, width: Int): Mask {
        val s = width.toFloat() / m.w
        val h = max(1, (m.h * s).roundToInt())
        return Mask(width, h, BooleanArray(width * h) { i -> m[(i % width / s).toInt(), (i / width / s).toInt()] })
    }

    // ------------------------------------------------------------------ mask operations

    /** Approximate Euclidean distance (cells) from each empty cell to the nearest set cell; 0 inside. */
    fun distanceOutside(m: Mask): FloatArray {
        val w = m.w; val h = m.h
        val big = 1e9f
        val d = FloatArray(w * h) { if (m.cells[it]) 0f else big }
        val a = 1f; val b = sqrt(2f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var v = d[i]
            if (x > 0) v = min(v, d[i - 1] + a)
            if (y > 0) {
                v = min(v, d[i - w] + a)
                if (x > 0) v = min(v, d[i - w - 1] + b)
                if (x < w - 1) v = min(v, d[i - w + 1] + b)
            }
            d[i] = v
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            var v = d[i]
            if (x < w - 1) v = min(v, d[i + 1] + a)
            if (y < h - 1) {
                v = min(v, d[i + w] + a)
                if (x < w - 1) v = min(v, d[i + w + 1] + b)
                if (x > 0) v = min(v, d[i + w - 1] + b)
            }
            d[i] = v
        }
        return d
    }

    fun dilate(m: Mask, r: Float): Mask {
        val d = distanceOutside(m)
        return Mask(m.w, m.h, BooleanArray(m.w * m.h) { d[it] <= r })
    }

    /** Sets every empty cell that can't reach the border (holes inside the shape). */
    fun fillHoles(m: Mask): Mask {
        val w = m.w; val h = m.h
        val outside = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        fun push(i: Int) { if (!m.cells[i] && !outside[i]) { outside[i] = true; stack.addLast(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast(); val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1); if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }
        return Mask(w, h, BooleanArray(w * h) { !outside[it] })
    }

    /** Only the largest 4-connected part. */
    fun largest(m: Mask): Mask {
        val w = m.w; val h = m.h
        val label = IntArray(w * h) { -1 }
        var best = -1; var bestSize = 0; var next = 0
        val stack = ArrayDeque<Int>()
        for (s in 0 until w * h) {
            if (!m.cells[s] || label[s] >= 0) continue
            var size = 0
            label[s] = next; stack.addLast(s)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast(); size++
                val x = i % w; val y = i / w
                for (j in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1))
                    if (j >= 0 && m.cells[j] && label[j] < 0) { label[j] = next; stack.addLast(j) }
            }
            if (size > bestSize) { bestSize = size; best = next }
            next++
        }
        return Mask(w, h, BooleanArray(w * h) { label[it] == best && best >= 0 })
    }

    // ------------------------------------------------------------------ QR payloads

    enum class QrType(val label: String) { LINK(tr("קישור")), WIFI("Wi‑Fi"), WHATSAPP(tr("וואטסאפ")), PHONE(tr("טלפון")), TEXT(tr("טקסט")) }

    /** Text to encode for each preset (formats every phone camera understands). */
    fun qrPayload(type: QrType, a: String, b: String = ""): String = when (type) {
        QrType.LINK -> a.trim().let { if (it.contains("://")) it else "https://${it}" }
        QrType.WIFI -> "WIFI:T:${if (b.isEmpty()) "nopass" else "WPA"};S:${wifiEscape(a)};" +
            (if (b.isEmpty()) "" else "P:${wifiEscape(b)};") + ";"
        QrType.WHATSAPP -> "https://wa.me/" + intlDigits(a) + if (b.isBlank()) "" else "?text=" + java.net.URLEncoder.encode(b, "UTF-8").replace("+", "%20")
        QrType.PHONE -> "tel:" + a.filter { it.isDigit() || it == '+' }
        QrType.TEXT -> a
    }

    private fun wifiEscape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

    /** Israeli local numbers (05x...) become international (9725x...), as wa.me requires. */
    private fun intlDigits(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.startsWith("0")) "972" + d.drop(1) else d
    }
}
