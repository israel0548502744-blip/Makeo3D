package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Print products that don't need the 3D model: lithophane (light shines through a photo),
 * relief (photo raised by its depth, needs the depth extra), QR code plates (link, Wi-Fi,
 * WhatsApp...), name keychains, cookie cutters, stamps and casting molds.
 * Photo products are 100 mm on the longest side; templates keep the size the user picked
 * (Mesh.sizeMm), and the print export uses that size, so thicknesses stay exactly as designed.
 */
object Creations {
    enum class Kind(val label: String, val needsImage: Boolean) {
        LITHOPHANE(tr("ליתופן"), true), RELIEF(tr("תבליט"), true), QR(tr("קוד QR"), false),
        NAME(tr("מחזיק מפתחות"), false), COOKIE(tr("חותכן עוגיות"), true), STAMP(tr("חותמת"), false),
        MOLD(tr("תבנית יציקה"), true),
    }

    class Options(
        val frame: Boolean = true,
        val maxMm: Float = 3f,
        val invert: Boolean = false,
        val reliefMm: Float = 8f,
        val flatBackground: Boolean = true,
        /** Name tag: letter height; cookie cutter / stamp: longest side. */
        val sizeMm: Float = 12f,
        val keyring: Boolean = true,
        val tagStyle: Templates.TagStyle = Templates.TagStyle.OUTLINE,
        /** Lithophane wrapped into a cylinder of this diameter (mm) for a lamp; 0 = flat. */
        val cylinderMm: Float = 0f,
        /** Stamp: use the image (dark parts) instead of the text. */
        val stampImage: Boolean = false,
        val qrType: Templates.QrType = Templates.QrType.LINK,
        val qrSecond: String = "",
        val caption: String = "",
        /** Casting mold: a master to pour silicone over (false: pour straight into the print). */
        val moldMaster: Boolean = false,
        /** Casting mold: the face follows the picture's depth (needs the depth extra). */
        val moldRelief: Boolean = false,
    )

    /** Status line while something is being built, null when idle. */
    var busy by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null)

    private const val SIZE_MM = Printability.TARGET_MM
    private const val SAMPLES = 300 // along the longest side: 0.33 mm per sample at 100 mm

    fun create(kind: Kind, image: Bitmap?, text: String, o: Options) {
        if (busy != null) return
        busy = tr("יוצר {0}…", kind.label); error = null
        val app = AppState.app
        AppState.scope.launch {
            try {
                val t0 = System.nanoTime()
                val (mesh, thumb) = when (kind) {
                    Kind.LITHOPHANE -> lithophane(image!!, o)
                    Kind.RELIEF -> relief(image!!, o)
                    Kind.QR -> qr(Templates.qrPayload(o.qrType, text, o.qrSecond), o.caption)
                    Kind.NAME -> nameTag(text, o)
                    Kind.COOKIE -> cookie(image!!, o)
                    Kind.STAMP -> stamp(image, text, o)
                    Kind.MOLD -> mold(image!!, o)
                }
                val seconds = (System.nanoTime() - t0) / 1e9
                val p = AppState.saveProject(mesh, thumb, "—", kind.label, seconds, emptyMap(),
                    name = "${kind.label} ${AppState.projects.count { it.quality == kind.label } + 1}",
                    source = image, kind = kind.name)
                CrashLog.log("Created ${kind.name} in %.1fs: ${mesh.triangleCount} triangles".format(seconds))
                withContext(Dispatchers.Main) { AppState.showNew(mesh, p) }
            } catch (e: Throwable) {
                CrashLog.error("create ${kind.name}", e)
                error = (e.message ?: e.javaClass.simpleName).take(200)
            } finally {
                busy = null
            }
        }
    }

    /** Resized so the longest side has [SAMPLES] samples. */
    private fun sample(src: Bitmap): Triple<IntArray, Int, Int> {
        val s = SAMPLES.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * s).toInt().coerceAtLeast(8); val h = (src.height * s).toInt().coerceAtLeast(8)
        val b = Bitmap.createScaledBitmap(src, w, h, true)
        return Triple(IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }, w, h)
    }

    private fun widthFor(w: Int, h: Int) = SIZE_MM * (w - 1) / (maxOf(w, h) - 1)

    private fun lithophane(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        if (o.cylinderMm > 0f) return lithophaneLamp(src, o)
        var (px, w, h) = sample(src)
        if (o.invert) px = IntArray(px.size) { px[it] xor 0x00FFFFFF }
        val mesh = Heightfield.lithophane(px, w, h, widthMm = widthFor(w, h), maxMm = o.maxMm, frameMm = if (o.frame) 3f else 0f)
        return mesh to grey(src)
    }

    /** The photo all the way round a cylinder; the height follows the photo's proportions. */
    private fun lithophaneLamp(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        val circumference = (Math.PI * o.cylinderMm).toFloat()
        val w = 600; val h = (w * src.height / src.width.toFloat()).toInt().coerceAtLeast(8)
        val b = Bitmap.createScaledBitmap(src, w, h, true)
        var px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        if (o.invert) px = IntArray(px.size) { px[it] xor 0x00FFFFFF }
        val heights = Heightfield.lithophaneHeights(px, w, h, circumference / w, 0.8f, o.maxMm, if (o.frame) 3f else 0f, sides = false)
        val colors = FloatArray(w * h * 3) { val t = (heights[it / 3] - 0.8f) / (o.maxMm - 0.8f); 0.95f - 0.75f * t }
        return Heightfield.cylinder(heights, w, h, circumference, colors) to grey(src)
    }

    private fun relief(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        val app = AppState.app
        if (!Extras.ready(app, Extras.DEPTH)) throw IllegalStateException(tr("תבליט צריך את \"מפת עומק\" מהשיפורים בהגדרות"))
        val (px, w, h) = sample(src)
        val raw = Enhance.depth(app, src, w, h)
        val mask = if (o.flatBackground) {
            val cut = Enhance.removeBackground(app, src) { AppState.segmenter() }
            val small = Bitmap.createScaledBitmap(cut, w, h, true)
            IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }.map { Color.alpha(it) / 255f }.toFloatArray()
        } else null
        val depth = Heightfield.normalizeDepth(raw, mask)
        val mesh = Heightfield.relief(depth, px, w, h, mask, widthMm = widthFor(w, h), reliefMm = o.reliefMm)
        return mesh to src
    }

    fun qrMatrix(text: String): Pair<BooleanArray, Int> {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val n = m.width
        return BooleanArray(n * n) { m.get(it % n, it / n) } to n
    }

    fun qrBitmap(text: String, px: Int = 8): Bitmap {
        val (mod, n) = qrMatrix(text)
        val pixels = IntArray(n * px * n * px) { i -> val x = i % (n * px) / px; val y = i / (n * px) / px; if (mod[y * n + x]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, n * px, n * px, Bitmap.Config.ARGB_8888)
    }

    private fun qr(text: String, caption: String): Pair<Mesh, Bitmap> {
        if (text.isBlank()) throw IllegalStateException(tr("צריך להכניס קישור או טקסט"))
        val (mod, n) = qrMatrix(text.trim())
        val mesh = if (caption.isBlank()) Heightfield.qr(mod, n, moduleMm = SIZE_MM / n)
        else {
            val k = 6
            Templates.qrWithCaption(mod, n, textMask(caption.trim(), 7 * k), SIZE_MM / n, k)
        }
        return mesh to qrBitmap(text.trim())
    }

    // ------------------------------------------------------------------ text and shape templates

    private const val TEXT_CELL_MM = 0.25f

    /**
     * Rubik Bold (SIL OFL, Hebrew + Latin) ships with the app, so printed text never uses the
     * phone's system font (on Samsung that is Samsung's own, not licensed for products).
     */
    private val font by lazy { android.graphics.Typeface.createFromAsset(AppState.app.assets, "fonts/Rubik-Bold.ttf") }

    /**
     * Text rendered as a mask, [heightCells] cells per line height. StaticLayout runs the bidi
     * algorithm, so Hebrew, English and numbers mix correctly.
     */
    fun textMask(text: String, heightCells: Int): Templates.Mask {
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = heightCells.toFloat()
            color = Color.BLACK
        }
        val width = kotlin.math.ceil(android.text.Layout.getDesiredWidth(text, paint)).toInt() + 4
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        val w = width; val h = layout.height + 4
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply { translate(0f, 2f); layout.draw(this) }
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        return Templates.Mask(w, h, BooleanArray(w * h) { Color.alpha(px[it]) > 127 })
    }

    private fun preview(mask: Templates.Mask): Bitmap {
        val m = mask.cropped()
        val px = IntArray(m.w * m.h) { if (m.cells[it]) Color.rgb(40, 40, 48) else Color.rgb(236, 236, 240) }
        return Bitmap.createBitmap(px, m.w, m.h, Bitmap.Config.ARGB_8888)
    }

    private fun nameTag(text: String, o: Options): Pair<Mesh, Bitmap> {
        if (text.isBlank()) throw IllegalStateException(tr("צריך להכניס שם או טקסט"))
        // Font size ~1.4x the height of capital letters.
        val mask = textMask(text.trim(), (o.sizeMm * 1.4f / TEXT_CELL_MM).toInt())
        val mesh = Templates.nameTag(mask, TEXT_CELL_MM, o.tagStyle, o.keyring)
        return mesh to preview(mask)
    }

    /** Silhouette of the image: the background-removal mask, or dark lines for a drawing on white. */
    private fun shape(src: Bitmap, samples: Int, removeBackground: Boolean): Templates.Mask {
        val img = if (removeBackground) Enhance.removeBackground(AppState.app, src) { AppState.segmenter() } else src
        val s = samples.toFloat() / maxOf(img.width, img.height)
        val w = (img.width * s).toInt().coerceAtLeast(8); val h = (img.height * s).toInt().coerceAtLeast(8)
        val b = Bitmap.createScaledBitmap(img, w, h, true)
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        return Templates.Mask(w, h, BooleanArray(w * h) {
            if (removeBackground) Color.alpha(px[it]) > 127 else Heightfield.luminance(intArrayOf(px[it]))[0] < 0.5f
        })
    }

    private fun cookie(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        val samples = 320
        val m = shape(src, samples, removeBackground = o.flatBackground)
        if (m.count() < 50) throw IllegalStateException(tr("לא נמצאה צורה בתמונה"))
        val b = m.bounds()!!
        val cell = o.sizeMm / maxOf(b[2] - b[0] + 1, b[3] - b[1] + 1)
        return Templates.cookieCutter(m, cell) to preview(m)
    }

    /** Casting mold of the picture's shape: sizeMm = longest side, reliefMm = how deep. */
    private fun mold(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        val app = AppState.app
        if (o.moldRelief && !Extras.ready(app, Extras.DEPTH)) throw IllegalStateException(tr("תבנית עם עומק צריכה את \"מפת עומק\" מהשיפורים בהגדרות"))
        var m = Templates.largest(shape(src, 220, removeBackground = o.flatBackground))
        if (!o.flatBackground) m = Templates.fillHoles(m)  // a drawing: the mold is what the line encloses
        if (m.count() < 50) throw IllegalStateException(tr("לא נמצאה צורה בתמונה"))
        val depth = if (o.moldRelief) Heightfield.normalizeDepth(Enhance.depth(app, src, m.w, m.h),
            FloatArray(m.w * m.h) { if (m.cells[it]) 1f else 0f }) else null
        val b = m.bounds()!!
        val cell = o.sizeMm / maxOf(b[2] - b[0] + 1, b[3] - b[1] + 1)
        return Templates.castingMold(m, depth, cell, o.reliefMm, o.moldMaster) to preview(m)
    }

    private fun stamp(src: Bitmap?, text: String, o: Options): Pair<Mesh, Bitmap> {
        val m = if (o.stampImage) shape(src ?: throw IllegalStateException(tr("צריך לבחור תמונה")), 300, removeBackground = false)
        else textMask(text.trim().ifEmpty { throw IllegalStateException(tr("צריך להכניס טקסט")) }, 120)
        val b = m.bounds() ?: throw IllegalStateException(tr("לא נמצא ציור"))
        val cell = o.sizeMm / maxOf(b[2] - b[0] + 1, b[3] - b[1] + 1)
        return Templates.stamp(m, cell) to preview(m)
    }

    private fun grey(src: Bitmap): Bitmap {
        val s = 256f / maxOf(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        for (i in px.indices) { val l = (Heightfield.luminance(intArrayOf(px[i]))[0] * 255).toInt(); px[i] = Color.rgb(l, l, l) }
        return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
    }
}
