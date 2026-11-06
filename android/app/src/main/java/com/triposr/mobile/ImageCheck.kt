package com.triposr.mobile

/** A reason the 3D result may disappoint. Shown as a warning; never blocks the user. */
enum class ImageIssue(val title: String, val detail: String) {
    NO_OBJECT(tr("לא זוהה אובייקט"),
        tr("הסרת הרקע לא מצאה אובייקט ברור. נסה תמונה עם אובייקט אחד במרכז, או כבה את הסרת הרקע.")),
    BACKGROUND_FAILED(tr("הסרת הרקע כנראה נכשלה"),
        tr("רוב התמונה נשארה, או שהגבולות לא ברורים. הרקע ייכנס למודל. רקע פשוט וניגודיות טובה עוזרים.")),
    FLAT(tr("נראה כמו תמונה שטוחה"),
        tr("ציור, פוסטר, מסך או תמונה של תמונה ייצאו כמו לוח שטוח, כי זה מה שהמודל רואה.")),
    SCREENSHOT(tr("נראה כמו צילום מסך"),
        tr("צילום מסך כולל ממשק ורקע. עדיף לחתוך רק את האובייקט או להשתמש בתמונה המקורית.")),
    CUT_OFF(tr("האובייקט נחתך בשולי התמונה"),
        tr("חלקים שלא נראים בתמונה יושלמו בניחוש. עדיף שכל האובייקט ייכנס לתמונה.")),
    SMALL(tr("האובייקט קטן מאוד בתמונה"),
        tr("מעט פיקסלים על האובייקט אומר פחות פרטים. התקרב או חתוך את התמונה.")),
}

/** Facts about where the image came from, used for the screenshot check. */
data class SourceInfo(val name: String? = null, val cameraExif: Boolean = false)

/**
 * Heuristic checks on the image and the background-removal mask. Pure Kotlin on ARGB / alpha
 * arrays so it can be tested on the JVM; the Android side passes a downscaled copy (≤ 256 px)
 * plus a full-resolution centre crop for the pixel-noise test.
 */
object ImageCheck {

    class Result(val issues: List<ImageIssue>, val stats: Map<String, Float>)

    /**
     * @param alpha mask 0..255 for each pixel of the w×h image, or null when the background was not removed.
     * @param crop ARGB pixels of an unscaled crop of the original (for the "flat colours" screenshot cue).
     * @param srcW / srcH size of the original image.
     */
    fun analyze(w: Int, h: Int, alpha: IntArray?, crop: IntArray?, srcW: Int, srcH: Int, source: SourceInfo): Result {
        val issues = ArrayList<ImageIssue>()
        val stats = LinkedHashMap<String, Float>()

        if (alpha != null) {
            var fg = 0; var soft = 0
            var x1 = w; var y1 = h; var x2 = -1; var y2 = -1
            for (y in 0 until h) for (x in 0 until w) {
                val a = alpha[y * w + x]
                if (a in 40..215) soft++
                if (a > 127) {
                    fg++
                    if (x < x1) x1 = x; if (x > x2) x2 = x; if (y < y1) y1 = y; if (y > y2) y2 = y
                }
            }
            val area = (w * h).toFloat()
            val coverage = fg / area
            stats["coverage"] = coverage
            stats["soft"] = if (fg > 0) soft / fg.toFloat() else 0f
            when {
                coverage < 0.01f -> issues += ImageIssue.NO_OBJECT
                coverage > 0.92f || (fg > 0 && soft / fg.toFloat() > 0.25f) -> issues += ImageIssue.BACKGROUND_FAILED
            }
            if (x2 >= 0) {
                val bw = x2 - x1 + 1; val bh = y2 - y1 + 1
                val fill = fg / (bw * bh).toFloat()
                val boxShare = bw * bh / area
                stats["rect_fill"] = fill
                stats["box_share"] = boxShare
                // A cut-out that is a solid axis-aligned rectangle: a picture, poster, screen or book cover.
                if (fill > 0.93f && boxShare > 0.12f && ImageIssue.BACKGROUND_FAILED !in issues) issues += ImageIssue.FLAT
                if (coverage in 0.01f..0.03f) issues += ImageIssue.SMALL
                // Foreground pressed against two or more image edges.
                var sides = 0
                fun edge(count: Int, len: Int) { if (count > len / 10) sides++ }
                edge((0 until w).count { alpha[it] > 127 }, w)
                edge((0 until w).count { alpha[(h - 1) * w + it] > 127 }, w)
                edge((0 until h).count { alpha[it * w] > 127 }, h)
                edge((0 until h).count { alpha[it * w + w - 1] > 127 }, h)
                stats["edges"] = sides.toFloat()
                if (sides >= 2 && ImageIssue.BACKGROUND_FAILED !in issues && ImageIssue.FLAT !in issues) issues += ImageIssue.CUT_OFF
            }
        }

        // Screenshots: the file name says so, or a phone-shaped image without camera EXIF made of
        // flat UI colours (photos have sensor noise, so neighbouring pixels are rarely identical).
        val flat = crop?.let { flatRatio(it) } ?: 0f
        stats["flat"] = flat
        val aspect = maxOf(srcW, srcH) / minOf(srcW, srcH).coerceAtLeast(1).toFloat()
        val named = source.name?.contains("screenshot", ignoreCase = true) == true ||
            source.name?.contains("צילום מסך") == true
        if (named || (!source.cameraExif && aspect >= 1.9f && flat > 0.45f)) issues += ImageIssue.SCREENSHOT

        return Result(issues, stats)
    }

    /** Share of horizontally adjacent pixel pairs with exactly the same RGB. */
    fun flatRatio(px: IntArray): Float {
        if (px.size < 2) return 0f
        var same = 0
        for (i in 1 until px.size) if ((px[i] xor px[i - 1]) and 0xFFFFFF == 0) same++
        return same / (px.size - 1).toFloat()
    }
}
