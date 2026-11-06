package com.triposr.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Problems found while the app runs: every caught error plus the result of a self-check that
 * runs on each launch (native libraries, model files, storage, processors, power state). They
 * are listed on the reports screen and in every copied report, so a bug can be traced without
 * guessing which part failed.
 */
object Health {
    enum class Level(val label: String) { ERROR(tr("שגיאה")), WARNING(tr("אזהרה")) }

    class Problem(val time: String, val level: Level, val area: String, val message: String) {
        override fun toString() = "${time} [${level.name}] ${area}: ${message}"
    }

    private const val MAX = 60
    private val items = ArrayList<Problem>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var file: File? = null

    /** Bumped on every change so Compose screens re-read [problems]. */
    val tick = mutableIntStateOf(0)

    fun problems(): List<Problem> = synchronized(items) { items.toList() }
    fun errorCount() = problems().count { it.level == Level.ERROR }

    fun init(logDir: File) {
        file = File(logDir, "problems.txt")
    }

    fun report(area: String, message: String, level: Level = Level.ERROR) {
        val p = Problem(time.format(Date()), level, area, message.take(500))
        synchronized(items) {
            // The same problem repeated (e.g. a failing download polled every second) is kept once.
            if (items.any { it.area == area && it.message == p.message }) return
            items.add(p)
            while (items.size > MAX) items.removeAt(0)
        }
        runCatching { file?.appendText(p.toString() + "\n") }
        tick.intValue++
    }

    fun clear() {
        synchronized(items) { items.clear() }
        runCatching { file?.delete() }
        tick.intValue++
    }

    /** Runs every check; each one is isolated so a failing check is itself reported. */
    fun runStartupChecks(ctx: Context) {
        // Robolectric (screenshot tests) has no native libraries or real storage.
        if (Build.FINGERPRINT == "robolectric") return
        val started = System.currentTimeMillis()
        check(tr("ספריות מערכת")) { nativeLibraries(ctx) }
        check("ONNX Runtime") { ai.onnxruntime.OrtEnvironment.getEnvironment() }
        check(tr("אחסון")) { storage(ctx) }
        check(tr("מודלים")) { models(ctx) }
        check(tr("מעבדים")) { processors(ctx) }
        check(tr("קבצי האפליקציה")) { assets(ctx) }
        check(tr("מצב סוללה וחום")) { power(ctx) }
        check(tr("הרשאות")) { permissions(ctx) }
        CrashLog.log("Startup checks done in ${System.currentTimeMillis() - started} ms: " +
            "${problems().size} problem(s)")
    }

    private inline fun check(area: String, block: () -> Unit) {
        runCatching(block).onFailure { report(area, tr("הבדיקה עצמה נכשלה: {0}", it)) }
    }

    private fun nativeLibraries(ctx: Context) {
        val dir = File(ctx.applicationInfo.nativeLibraryDir)
        val required = mutableListOf("libonnxruntime.so")
        if (Capabilities.isSnapdragon()) required += listOf("libQnnHtp.so", "libQnnGpu.so", "libQnnSystem.so")
        val missing = required.filter { !File(dir, it).exists() }
        if (missing.isNotEmpty()) report(tr("ספריות מערכת"), tr("חסרות: {0} (ב-{1})", missing.joinToString(), dir.path))
        if (Capabilities.isSnapdragon() && dir.listFiles()?.none { it.name.startsWith("libQnnHtpV") && it.name.endsWith("Skel.so") } == true) {
            report(tr("ספריות מערכת"), tr("לא נמצאה ספריית Skel של ה-NPU (libQnnHtpV*Skel.so)"))
        }
    }

    private fun storage(ctx: Context) {
        val dir = ctx.getExternalFilesDir(null) ?: return report(tr("אחסון"), tr("אין גישה לתיקיית הקבצים של האפליקציה"))
        val freeMb = StatFs(dir.path).availableBytes shr 20
        when {
            freeMb < 500 -> report(tr("אחסון"), tr("נשארו רק {0} MB פנויים: יצירה ושמירת מודלים עלולות להיכשל", freeMb))
            freeMb < 2000 -> report(tr("אחסון"), tr("נשארו {0} MB פנויים. הורדת מודלים נוספים דורשת יותר", freeMb), Level.WARNING)
        }
    }

    private fun models(ctx: Context) {
        val packs = listOf(ModelStore.MAIN, ModelStore.NPU, ModelStore.TRIPOSG) + Extras.all.map { it.pack }
        for (p in packs) {
            when (val s = ModelStore.status(ctx, p)) {
                is ModelStore.Status.Failed -> report(tr("מודלים"), "${p.title}: ${s.message}")
                ModelStore.Status.Missing -> {
                    // Half-downloaded files that no longer match: the user sees "download" again.
                    val partial = p.files().filter { ModelStore.file(ctx, it).exists() }
                    if (partial.isNotEmpty() && !ModelStore.isReady(ctx, p)) report(tr("מודלים"),
                        tr("{0}: {1} קבצים קיימים אבל החבילה לא שלמה", p.title, partial.size), Level.WARNING)
                }
                else -> {}
            }
        }
        if (!ModelStore.isReady(ctx)) report(tr("מודלים"), tr("המודל הראשי עוד לא הורד"), Level.WARNING)
    }

    private fun processors(ctx: Context) {
        if (!Capabilities.isSnapdragon()) {
            report(tr("מעבדים"), tr("הטלפון לא Snapdragon ({0}): רק CPU, יצירה איטית", Build.SOC_MODEL), Level.WARNING)
            return
        }
        for (r in Capabilities.results(ctx).values) {
            if (r.support in listOf(Support.FAILED, Support.CRASHED, Support.UNSUPPORTED)) {
                report(tr("מעבדים"), "${r.backend.label}: ${r.support.label}. ${r.detail.take(200)}",
                    if (r.backend == Backend.CPU) Level.ERROR else Level.WARNING)
            }
        }
        for (b in Backend.entries) if (AppState.crashedBefore(b)) {
            report(tr("מעבדים"), tr("האפליקציה נסגרה בעבר בזמן עבודה על {0}; המצב האוטומטי מדלג עליו", b.label), Level.WARNING)
        }
    }

    private fun assets(ctx: Context) {
        for (name in listOf("fonts/Rubik-Bold.ttf", "third_party_licenses.txt", "legal/terms_he.txt", "legal/privacy_he.txt")) {
            runCatching { ctx.assets.open(name).close() }.onFailure { report(tr("קבצי האפליקציה"), tr("חסר {0}", name)) }
        }
    }

    private fun power(ctx: Context) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm.isPowerSaveMode) report(tr("מצב סוללה וחום"), tr("חיסכון בסוללה פעיל: היצירה תהיה איטית יותר"), Level.WARNING)
        if (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            report(tr("מצב סוללה וחום"), tr("הטלפון חם מאוד (רמה {0}): המעבדים מואטים", pm.currentThermalStatus), Level.WARNING)
        }
    }

    private fun permissions(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            report(tr("הרשאות"), tr("התראות כבויות: לא תקבל הודעה כשמודל מוכן ברקע"), Level.WARNING)
        }
    }
}
