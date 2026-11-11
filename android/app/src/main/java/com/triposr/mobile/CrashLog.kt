package com.triposr.mobile

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a running log of what the app is doing and turns crashes into a text report the
 * user can copy and paste.
 *
 * Java/Kotlin crashes are caught by an uncaught-exception handler. Native crashes (e.g. inside
 * the QNN / Hexagon libraries), ANRs and out-of-memory kills can't be caught in-process, so on
 * the next launch we ask Android why the previous process died (ApplicationExitInfo) and attach
 * the tail of that session's log, which shows the last step before the crash.
 */
object CrashLog {
    private const val MAX_LINES = 400
    private lateinit var app: Application
    private lateinit var dir: File
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Report about how the previous session ended, if it ended badly. Null when nothing to show. */
    @Volatile var pendingReport: String? = null; private set

    /** True when the previous session of the main process ended abnormally (this launch only). */
    @Volatile var previousSessionCrashed = false; private set

    /** Last error caught while the app kept running (shown on the error screen). */
    @Volatile var lastError: String? = null; private set

    fun init(application: Application) {
        app = application
        dir = File(application.filesDir, "logs").apply { mkdirs() }
        Health.init(dir)
        val previousLog = File(dir, "previous_session.log")
        val session = File(dir, "session.log")
        if (session.exists()) session.renameTo(previousLog)

        val javaCrash = File(dir, "crash.txt").takeIf { it.exists() }?.readText()
        val exit = previousExit()
        val flag = File(dir, "crashed.flag")
        previousSessionCrashed = flag.exists() || exit != null
        flag.delete()
        pendingReport = when {
            javaCrash != null -> javaCrash
            exit != null -> buildReport("Previous session ended: ${exit}", null,
                previousLog.takeIf { it.exists() }?.readLines()?.takeLast(120))
            else -> null
        }
        if (javaCrash != null) Health.report(tr("קריסה"), tr("האפליקציה קרסה בהפעלה הקודמת (דוח מלא למעלה)"))
        else if (exit != null) Health.report(tr("קריסה"), tr("ההפעלה הקודמת הסתיימה בתקלה: {0}", exit.lineSequence().first()))

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                log("CRASH on thread ${thread.name}: ${e}")
                File(dir, "crash.txt").writeText(buildReport("Crash (uncaught exception)", e, lines.toList()))
                File(dir, "crashed.flag").createNewFile()
            }
            previous?.uncaughtException(thread, e)
        }
        log("App start: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    @Synchronized
    fun log(message: String) {
        val line = "${time.format(Date())} ${message}"
        android.util.Log.i("TripoSR", message)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        runCatching { File(dir, "session.log").appendText(line + "\n") }
    }

    fun error(where: String, e: Throwable) {
        // Leaving a screen cancels its work; that is not an error worth a report.
        if (e is kotlinx.coroutines.CancellationException) return log("${where}: cancelled (screen left)")
        log("ERROR in ${where}: ${e}")
        Health.report(where, e.toString())
        lastError = buildReport("Error in ${where}", e, lines.toList().takeLast(80))
    }

    fun dismissPending() {
        pendingReport = null
        File(dir, "crash.txt").delete()
    }

    fun recentLog(): List<String> = synchronized(this) { lines.toList() }

    fun clear() = synchronized(this) {
        lines.clear()
        File(dir, "session.log").delete()
        File(dir, "previous_session.log").delete()
        dismissPending()
        lastError = null
        Health.clear()
    }

    /** Full diagnostics: device, app, model state plus recent log lines. */
    fun diagnostics(): String {
        // TripoSG loads its parts hundreds of times per run; those lines would push the useful ones out.
        fun compact(lines: List<String>) = lines.filterNot { "TripoSG: opened sg_dit" in it }
        val current = compact(recentLog())
        // Right after a restart the interesting run is in the previous session's log.
        val previous = if (current.size >= 40) emptyList() else
            File(dir, "previous_session.log").takeIf { it.exists() }?.readLines()?.let(::compact)?.takeLast(100).orEmpty()
        val log = if (previous.isEmpty()) current.takeLast(150)
            else listOf("(previous session)") + previous + listOf("(this session)") + current
        return buildReport("Diagnostics", null, log)
    }

    fun buildReport(title: String, e: Throwable?, log: List<String>?): String = buildString {
        appendLine("=== Makeo 3D — ${title} ===")
        appendLine("Time: ${stamp.format(Date())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), region ${runCatching { Region.country(app) }.getOrDefault("?")}")
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        appendLine("RAM: ${mem.availMem shr 20} MB free of ${mem.totalMem shr 20} MB, heap limit ${am.largeMemoryClass} MB")
        appendLine("Model: ${runCatching { ModelStore.describe(app) }.getOrDefault("?")}")
        appendLine("Settings: ${runCatching { Settings.describe(app) }.getOrDefault("?")}")
        appendLine("Extras: ${runCatching { Extras.describe(app) }.getOrDefault("?")}")
        val problems = runCatching { Health.problems() }.getOrDefault(emptyList())
        if (problems.isNotEmpty()) {
            appendLine("--- Problems (${problems.size}) ---")
            problems.forEach { appendLine(it.toString()) }
        }
        if (e != null) {
            appendLine("--- Exception ---")
            appendLine(e.stackTraceToString().take(6000))
        }
        if (!log.isNullOrEmpty()) {
            appendLine("--- Log ---")
            log.forEach { appendLine(it) }
        }
    }

    private fun previousExit(): String? {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // Only the main process; the device test runs in ":probe" and may crash on purpose.
        val info = runCatching {
            am.getHistoricalProcessExitReasons(app.packageName, 0, 10).firstOrNull { it.processName == app.packageName }
        }.getOrNull() ?: return null
        val prefs = app.getSharedPreferences("crashlog", Context.MODE_PRIVATE)
        if (prefs.getLong("seen_exit", 0) >= info.timestamp) return null
        prefs.edit().putLong("seen_exit", info.timestamp).apply()

        // Android clears idle apps out of memory all the time. That is not a fault: only count a
        // low-memory kill when the app was on screen or working (a service) at that moment.
        if (info.reason == ApplicationExitInfo.REASON_LOW_MEMORY &&
            info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
            log("Previous run was cleared from memory while idle in the background (normal)")
            return null
        }
        val reason = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash (C/C++ library, e.g. ONNX Runtime / QNN)"
            ApplicationExitInfo.REASON_ANR -> "app not responding (ANR)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "killed by the system for low memory"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource usage"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by signal ${info.status}"
            else -> return null // normal exit, user swipe, update, etc.
        }
        return buildString {
            append(reason)
            append(" at ${stamp.format(Date(info.timestamp))}")
            info.description?.let { append(" — ${it}") }
            append(" (pss ${info.pss shr 10} MB, rss ${info.rss shr 10} MB)")
            if (info.reason == ApplicationExitInfo.REASON_ANR) {
                runCatching { info.traceInputStream?.bufferedReader()?.readText()?.take(3000) }
                    .getOrNull()?.let { append("\n--- ANR trace ---\n${it}") }
            }
        }
    }
}

class TripoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The capability test runs in its own process and must not touch the app's logs/state.
        if (getProcessName().endsWith(":probe")) return
        I18n.init(this)
        CrashLog.init(this)
        AppState.init(this)
    }
}
