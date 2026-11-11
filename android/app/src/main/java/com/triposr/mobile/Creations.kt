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
 * WhatsApp...), name keychains, cookie cutters, stamps and casting molds. More products are in [Crafts].
 * Photo products are 100 mm on the longest side; templates keep the size the user picked
 * (Mesh.sizeMm), and the print export uses that size, so thicknesses stay exactly as designed.
 */
/** Marks a Hebrew text that is translated where it is shown. */
private fun he(s: String) = s

object Creations {
    /** What a product needs before it can be made (OLD: the first products, decided in the screen). */
    enum class Need { OLD, IMAGE, TEXT, EITHER, NONE }

    /**
     * The texts are kept in Hebrew and translated when read ([tr]), so they follow the language
     * even though the enum is created once.
     */
    enum class Kind(private val labelHe: String, val needsImage: Boolean, val need: Need = Need.OLD,
                    private val textHe: String? = null, private val secondHe: String? = null, private val toggleHe: String? = null,
                    private val sizeHe: String? = null, val min: Float = 0f, val max: Float = 0f, val def: Float = 0f,
                    private val aboutHe: String = "") {
        LITHOPHANE(he("ליתופן"), true), RELIEF(he("תבליט"), true), QR(he("קוד QR"), false),
        NAME(he("מחזיק מפתחות"), false), COOKIE(he("חותכן עוגיות"), true), STAMP(he("חותמת"), false),
        MOLD(he("תבנית יציקה"), true, textHe = he("כיתוב שקוע ביציקה (לא חובה)")),
        STENCIL(he("שבלונה"), true, Need.IMAGE, sizeHe = he("גודל: %.0f מ״מ"), min = 60f, max = 180f, def = 100f,
            aboutHe = he("לוח דק שהצורה חתוכה בו, לריסוס צבע או לפיזור קקאו ואבקת סוכר. חלקים סגורים בתוך קו מוחזקים בגשרים דקים.")),
        MAGNET(he("מגנט"), true, Need.IMAGE, sizeHe = he("גודל: %.0f מ״מ"), min = 40f, max = 90f, def = 60f,
            aboutHe = he("האובייקט שבתמונה כלוחית צבעונית, עם שקע מאחור למגנט עגול בקוטר 10 מ״מ (מדביקים אותו).")),
        STAND(he("מעמד לטלפון"), false, Need.NONE, textHe = he("שם על המעמד (לא חובה)"),
            aboutHe = he("מעמד לטלפון ברוחב 60 מ״מ, מודפס על הצד בלי תמיכות. השם בולט על הדופן.")),
        FRAME(he("מסגרת לתמונה"), false, Need.NONE, textHe = he("כיתוב על המסגרת (לא חובה)"),
            sizeHe = he("אורך התמונה: %.0f מ״מ"), min = 90f, max = 150f, def = 130f,
            aboutHe = he("מסגרת לתמונה מודפסת ביחס 2:3 (למשל 10×15 או 9×13), עם שקע מאחור שמחזיק אותה.")),
        TOPPER(he("קישוט לעוגה"), false, Need.EITHER, textHe = he("שם או ברכה (או בחר תמונה)"),
            sizeHe = he("רוחב: %.0f מ״מ"), min = 60f, max = 160f, def = 110f,
            aboutHe = he("שם, ברכה או צורה מתמונה על מקלות שננעצים בעוגה. הקובץ אינו אישור בטיחות למזון.")),
        PENDANT(he("תליון"), true, Need.IMAGE, toggleHe = he("זוג עגילים"), sizeHe = he("גודל: %.0f מ״מ"), min = 20f, max = 60f, def = 35f,
            aboutHe = he("האובייקט שבתמונה כתליון עם לולאה לשרשרת. \"זוג עגילים\" מדפיס שניים, אחד הפוך מהשני.")),
        SIGN(he("שלט"), false, Need.TEXT, textHe = he("שם או טקסט"), sizeHe = he("גובה אותיות: %.0f מ״מ"), min = 20f, max = 60f, def = 35f,
            aboutHe = he("שלט לדלת או לחדר: אותיות גדולות בולטות על לוח. החלף פילמנט בגובה 2.4 מ״מ לשני צבעים.")),
        BOOKMARK(he("סימנייה"), false, Need.TEXT, textHe = he("שם או טקסט"),
            aboutHe = he("סימנייה דקה (3×14 ס״מ) עם השם לאורכה וחור לחוט.")),
        MEDAL(he("מדליון דיוקן"), true, Need.IMAGE, textHe = he("שם על המדליון (לא חובה)"),
            sizeHe = he("קוטר: %.0f מ״מ"), min = 50f, max = 100f, def = 70f,
            aboutHe = he("מדליון עגול עם הפנים (או החפץ) שבתמונה כתבליט, ושם מתחת. צריך את \"מפת עומק\" מהשיפורים.")),
        PLANTER(he("עציץ"), true, Need.IMAGE, sizeHe = he("קוטר: %.0f מ״מ"), min = 60f, max = 120f, def = 80f,
            aboutHe = he("עציץ קטן שהתמונה עוטפת אותו כתבליט. בלי חור ניקוז, מתאים לכיסוי עציץ או לצמח מלאכותי.")),
        NIGHT(he("מנורת לילה"), true, Need.IMAGE,
            aboutHe = he("ליתופן עם בסיס: מעמידים את הלוח בחריץ שבבסיס ושמים מאחוריו נר LED. מודפס בלבן.")),
        PUZZLE(he("פאזל"), true, Need.IMAGE, toggleHe = he("יותר חלקים (4 בשורה)"), sizeHe = he("גודל: %.0f מ״מ"), min = 80f, max = 140f, def = 110f,
            aboutHe = he("התמונה כתבליט, חתוכה לחלקי פאזל שמודפסים מופרדים ומתחברים זה לזה.")),
        SEAL(he("חותם שעווה"), false, Need.EITHER, textHe = he("אות או שתיים (או בחר ציור או תמונה)"),
            sizeHe = he("קוטר: %.0f מ״מ"), min = 20f, max = 35f, def = 25f,
            aboutHe = he("חותם עגול עם ידית למכתבים. העיטור שקוע והפוך, כך שבשעווה הוא יוצא בולט וישר. מודפס בעמידה על הידית. מתמונה של אדם או חפץ נוצר תבליט, וזה צריך את \"מפת עומק\" מהשיפורים.")),
        PETTAG(he("תג לחיית מחמד"), false, Need.TEXT, textHe = he("שם החיה"), secondHe = he("טלפון (לא חובה)"),
            sizeHe = he("רוחב: %.0f מ״מ"), min = 30f, max = 55f, def = 40f,
            aboutHe = he("תג בצורת עצם לקולר, עם השם, הטלפון וחור לטבעת.")),
        BOX(he("קופסה עם מכסה"), false, Need.NONE, textHe = he("שם על המכסה (לא חובה)"),
            sizeHe = he("רוחב: %.0f מ״מ"), min = 50f, max = 110f, def = 70f,
            aboutHe = he("קופסה מרובעת ומכסה שנח על מדף פנימי. השם בולט על המכסה."));

        val label get() = tr(labelHe)
        val textLabel get() = textHe?.let { tr(it) }
        val secondLabel get() = secondHe?.let { tr(it) }
        val toggle get() = toggleHe?.let { tr(it) }
        val sizeLabel get() = sizeHe?.let { tr(it) }
        val about get() = if (aboutHe.isEmpty()) "" else tr(aboutHe)
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
        /** Casting mold: a hole in the floor for a candle wick. */
        val wick: Boolean = false,
        /** Casting mold: how many copies side by side (1, 2, 4 or 6). */
        val copies: Int = 1,
        /** The product's own switch ([Kind.toggle]). */
        val alt: Boolean = false,
    )

    /** Status line while something is being built, null when idle. */
    var busy by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null)

    private const val SIZE_MM = Printability.TARGET_MM
    private const val SAMPLES = 300 // along the longest side: 0.33 mm per sample at 100 mm

    fun create(kind: Kind, image: Bitmap?, text: String, o: Options) =
        build(kind.label, kind.name, image, encode(text, o)) { make(kind, image, text, o) }

    /** A product's text and options as JSON, kept with the project so it can be changed later. */
    fun encode(text: String, o: Options): String = org.json.JSONObject().apply {
        put("text", text); put("frame", o.frame); put("maxMm", o.maxMm.toDouble()); put("invert", o.invert)
        put("reliefMm", o.reliefMm.toDouble()); put("flatBackground", o.flatBackground); put("sizeMm", o.sizeMm.toDouble())
        put("keyring", o.keyring); put("tagStyle", o.tagStyle.name); put("cylinderMm", o.cylinderMm.toDouble())
        put("stampImage", o.stampImage); put("qrType", o.qrType.name); put("qrSecond", o.qrSecond); put("caption", o.caption)
        put("moldMaster", o.moldMaster); put("moldRelief", o.moldRelief); put("wick", o.wick); put("copies", o.copies); put("alt", o.alt)
    }.toString()

    fun decode(params: String): Pair<String, Options>? = runCatching {
        val j = org.json.JSONObject(params)
        j.optString("text") to Options(j.getBoolean("frame"), j.getDouble("maxMm").toFloat(), j.getBoolean("invert"),
            j.getDouble("reliefMm").toFloat(), j.getBoolean("flatBackground"), j.getDouble("sizeMm").toFloat(), j.getBoolean("keyring"),
            Templates.TagStyle.valueOf(j.getString("tagStyle")), j.getDouble("cylinderMm").toFloat(), j.getBoolean("stampImage"),
            Templates.QrType.valueOf(j.getString("qrType")), j.optString("qrSecond"), j.optString("caption"),
            j.optBoolean("moldMaster"), j.optBoolean("moldRelief"), j.optBoolean("wick"), j.optInt("copies", 1), j.optBoolean("alt"))
    }.getOrNull()

    /** Builds the product right away, on the calling thread (also what the quality tests call). */
    fun make(kind: Kind, image: Bitmap?, text: String, o: Options): Pair<Mesh, Bitmap> =
        when (kind) {
            Kind.LITHOPHANE -> lithophane(image!!, o)
            Kind.RELIEF -> relief(image!!, o)
            Kind.QR -> qr(Templates.qrPayload(o.qrType, text, o.qrSecond), o.caption)
            Kind.NAME -> nameTag(text, o)
            Kind.COOKIE -> cookie(image!!, o)
            Kind.STAMP -> stamp(image, text, o)
            Kind.MOLD -> mold(image!!, text, o)
            Kind.STENCIL -> Crafts.stencil(image!!, o)
            Kind.MAGNET -> Crafts.magnet(image!!, o)
            Kind.STAND -> Crafts.stand(text)
            Kind.FRAME -> Crafts.frame(text, o)
            Kind.TOPPER -> Crafts.topper(image, text, o)
            Kind.PENDANT -> Crafts.pendant(image!!, o)
            Kind.SIGN -> Crafts.sign(text, o)
            Kind.BOOKMARK -> Crafts.bookmark(text)
            Kind.MEDAL -> Crafts.medal(image!!, text, o)
            Kind.PLANTER -> Crafts.planter(image!!, o)
            Kind.NIGHT -> Crafts.nightLight(image!!, o)
            Kind.PUZZLE -> Crafts.puzzle(image!!, o)
            Kind.SEAL -> Crafts.seal(image, text, o)
            Kind.PETTAG -> Crafts.petTag(text, o.qrSecond, o)
            Kind.BOX -> Crafts.box(text, o)
        }

    /** Two-half casting mold of a finished 3D model. */
    fun moldOf(mesh: Mesh) = build(tr("תבנית משני חצאים"), "MOLD2", null, "") { Crafts.twoPartMold(mesh) }

    private fun build(label: String, kindName: String, image: Bitmap?, params: String, make: () -> Pair<Mesh, Bitmap>) {
        if (busy != null) return
        busy = tr("יוצר {0}…", label); error = null
        AppState.scope.launch {
            try {
                val t0 = System.nanoTime()
                val (mesh, thumb) = make()
                MeshCheck.verify(mesh, kindName)  // never show or save an empty or broken shape
                val seconds = (System.nanoTime() - t0) / 1e9
                val p = AppState.saveProject(mesh, thumb, "—", label, seconds, emptyMap(),
                    name = "$label ${AppState.projects.count { it.quality == label } + 1}",
                    source = image, kind = kindName, params = params)
                AppState.used(kindName)
                CrashLog.log("Created $kindName in %.1fs: ${mesh.triangleCount} triangles".format(seconds))
                withContext(Dispatchers.Main) { AppState.showNew(mesh, p) }
            } catch (e: Throwable) {
                CrashLog.error("create $kindName", e)
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
        if (!Crafts.depthReady()) throw IllegalStateException(tr("תבליט צריך את \"מפת עומק\" מהשיפורים בהגדרות"))
        val (px, w, h) = sample(src)
        val raw = Crafts.depth(src, w, h)
        val mask = if (o.flatBackground) {
            val cut = Crafts.cutout(src)
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
        val img = if (removeBackground) Crafts.cutout(src) else src
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
    private fun mold(src: Bitmap, text: String, o: Options): Pair<Mesh, Bitmap> {
        val app = AppState.app
        if (o.moldRelief && !Crafts.depthReady()) throw IllegalStateException(tr("תבנית עם עומק צריכה את \"מפת עומק\" מהשיפורים בהגדרות"))
        // Several copies share one tray: fewer samples each, so the file stays a size a phone can handle.
        var m = Templates.largest(shape(src, when { o.copies >= 4 -> 130; o.copies == 2 -> 170; else -> 220 }, removeBackground = o.flatBackground))
        if (!o.flatBackground) m = Templates.fillHoles(m)  // a drawing: the mold is what the line encloses
        if (m.count() < 50) throw IllegalStateException(tr("לא נמצאה צורה בתמונה"))
        val depth = if (o.moldRelief) Heightfield.normalizeDepth(Crafts.depth(src, m.w, m.h),
            FloatArray(m.w * m.h) { if (m.cells[it]) 1f else 0f }) else null
        val b = m.bounds()!!
        val bw = b[2] - b[0] + 1; val bh = b[3] - b[1] + 1
        val cell = o.sizeMm / maxOf(bw, bh)
        // Optional writing in the middle of the shape, sunk into the cast.
        val mark = if (text.isBlank()) null else {
            val t = Crafts.fitText(text, bh * cell * 0.22f, bw * cell * 0.6f, cell)
            val ox = b[0] + (bw - t.w) / 2; val oy = b[1] + (bh - t.h) / 2
            BooleanArray(m.w * m.h) { t[it % m.w - ox, it / m.w - oy] }
        }
        val (cols, rows) = when (o.copies) { 2 -> 2 to 1; 4 -> 2 to 2; 6 -> 3 to 2; else -> 1 to 1 }
        return Templates.castingMold(m, depth, cell, o.reliefMm, o.moldMaster, mark, o.wick && !o.moldMaster, cols, rows) to preview(m)
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
