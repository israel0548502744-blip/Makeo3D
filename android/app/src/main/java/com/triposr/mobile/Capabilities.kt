package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.abs

enum class Support(val label: String) {
    UNKNOWN(tr("לא נבדק")),
    OK(tr("עובד")),
    FAILED(tr("לא עובד")),
    CRASHED(tr("גרם לקריסה")),
    UNSUPPORTED(tr("לא נתמך בטלפון")),
    NEEDS_PACK(tr("צריך חבילת NPU")),
}

data class BackendResult(val backend: Backend, val support: Support, val ms: Double = 0.0, val detail: String = "")

/**
 * Finds out which processors actually work on this phone.
 *
 * QNN (NPU/GPU) only exists on Snapdragon. On a Snapdragon phone each backend is tested by
 * running the real (small) decoder model and comparing its output with the CPU. The test runs
 * in a separate process (ProbeService, android:process=":probe"), so if a driver crashes it
 * takes down only that process: the app records the backend as CRASHED and carries on.
 */
object Capabilities {
    private const val RESULT_FILE = "probe_result.txt"
    private const val PROBE_TIMEOUT_MS = 300_000L

    fun isSnapdragon(): Boolean {
        val soc = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL} ${Build.HARDWARE} ${Build.BOARD}".lowercase()
        return "qti" in soc || "qualcomm" in soc || "qcom" in soc || Build.SOC_MODEL.startsWith("SM")
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("capabilities", Context.MODE_PRIVATE)

    /** Results are tied to the app build and to whether the NPU pack is installed. */
    fun probed(ctx: Context) = prefs(ctx).getInt("build", -1) == BuildConfig.VERSION_CODE &&
        prefs(ctx).getBoolean("npu_pack", false) == NpuPack.isReady(ctx)

    fun results(ctx: Context): Map<Backend, BackendResult> {
        val json = prefs(ctx).getString("results", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return Backend.entries.associateWith { b ->
            if (b != Backend.CPU && !isSnapdragon()) return@associateWith BackendResult(b, Support.UNSUPPORTED, detail = tr("לא Snapdragon"))
            if (AppState.crashedBefore(b)) return@associateWith BackendResult(b, Support.CRASHED)
            val o = json?.optJSONObject(b.name) ?: return@associateWith BackendResult(b,
                if (b == Backend.CPU) Support.OK else Support.UNKNOWN)
            val r = BackendResult(b, Support.valueOf(o.getString("support")), o.optDouble("ms"), o.optString("detail"))
            // The NPU works, but the big encoder can only run on it from the pre-compiled pack.
            if (b == Backend.NPU && r.support == Support.OK && !NpuPack.isReady(ctx)) r.copy(support = Support.NEEDS_PACK) else r
        }
    }

    /** Fastest backend that passed the test (NPU, then GPU, then CPU). */
    fun best(ctx: Context): Backend {
        val r = results(ctx)
        return Backend.entries.firstOrNull { r[it]?.support == Support.OK } ?: Backend.CPU
    }

    /** Backends to try, in order, for a user choice (null = automatic). CPU always closes the list. */
    fun chain(ctx: Context, choice: Backend?): List<Backend> {
        val r = results(ctx)
        val ordered = if (choice == null) Backend.entries.filter { r[it]?.support == Support.OK } else listOf(choice)
        return (ordered + Backend.CPU).distinct()
    }

    fun describe(ctx: Context) = results(ctx).values.joinToString(", ") { "${it.backend}=${it.support}" +
        (if (it.ms > 0) "(%.0fms)".format(it.ms) else "") + (if (it.detail.isNotEmpty()) "[${it.detail.take(80)}]" else "") }

    suspend fun probe(ctx: Context, onStatus: (String) -> Unit): Map<Backend, BackendResult> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<Backend, BackendResult>()
        if (!isSnapdragon()) {
            CrashLog.log("Probe: not a Snapdragon (${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}), CPU only")
        } else {
            var remaining = listOf(Backend.NPU, Backend.GPU).filter { !AppState.crashedBefore(it) }
            while (remaining.isNotEmpty()) {
                onStatus(tr("בודק אילו מעבדים עובדים בטלפון ({0})", remaining.joinToString { it.short }))
                val out = File(ctx.filesDir, RESULT_FILE).apply { delete() }
                val pack = NpuPack.isReady(ctx)
                ctx.startService(Intent(ctx, ProbeService::class.java)
                    .putExtra("backends", remaining.map { it.name }.toTypedArray())
                    .putExtra("npu_encoder", if (pack) NpuPack.encoder(ctx)?.absolutePath else null)
                    .putExtra("npu_decoder", if (pack) NpuPack.decoder(ctx)?.absolutePath else null))
                val t0 = System.currentTimeMillis()
                delay(1500)
                while (System.currentTimeMillis() - t0 < PROBE_TIMEOUT_MS) {
                    if (out.exists() && out.readLines().any { it == "DONE" }) break
                    if (!probeProcessAlive(ctx)) { delay(500); break }
                    delay(400)
                }
                val lines = if (out.exists()) out.readLines() else emptyList()
                var crashedOn: Backend? = null
                for (b in remaining) {
                    val res = lines.lastOrNull { it.startsWith("${b.name} ") && !it.startsWith("${b.name} START") }
                    when {
                        res != null -> {
                            val parts = res.split(" ", limit = 4)
                            val enc = lines.firstOrNull { it.startsWith("${b.name}_ENCODER ") }?.substringAfter(" ")
                            found[b] = BackendResult(b, Support.valueOf(parts[1]), parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                                listOfNotNull(parts.getOrNull(3), enc?.let { "encoder ${it}" }).joinToString(" "))
                        }
                        lines.any { it == "${b.name} START" } -> { crashedOn = b; found[b] = BackendResult(b, Support.CRASHED,
                            detail = "the test process died while testing ${b.name}") }
                        crashedOn == null -> found[b] = BackendResult(b, Support.FAILED, detail = "no result (timeout)")
                    }
                }
                CrashLog.log("Probe results: ${found.values.joinToString { "${it.backend}=${it.support} ${it.detail}" }}")
                // After a crash, test the backends that came after it in a fresh process.
                remaining = if (crashedOn != null) remaining.drop(remaining.indexOf(crashedOn) + 1) else emptyList()
            }
        }
        found[Backend.CPU] = BackendResult(Backend.CPU, Support.OK)
        val json = JSONObject()
        found.values.forEach { r ->
            json.put(r.backend.name, JSONObject().put("support", r.support.name).put("ms", r.ms).put("detail", r.detail))
        }
        prefs(ctx).edit().putString("results", json.toString()).putInt("build", BuildConfig.VERSION_CODE)
            .putBoolean("npu_pack", NpuPack.isReady(ctx)).apply()
        results(ctx)
    }

    private fun probeProcessAlive(ctx: Context): Boolean {
        val am = ctx.getSystemService(ActivityManager::class.java)
        return am.runningAppProcesses?.any { it.processName == "${ctx.packageName}:probe" } == true
    }

    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()
}

/** Runs in its own process (":probe"). Writes one line per backend to probe_result.txt. */
class ProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val names = intent?.getStringArrayExtra("backends") ?: emptyArray()
        val npuEncoder = intent?.getStringExtra("npu_encoder")
        val npuDecoder = intent?.getStringExtra("npu_decoder")
        Thread {
            runCatching { probe(names.map { Backend.valueOf(it) }, npuEncoder, npuDecoder) }
                .onFailure { append("ERROR ${it.message?.replace('\n', ' ')}") }
            append("DONE")
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }.start()
        return START_NOT_STICKY
    }

    private val out by lazy { File(filesDir, "probe_result.txt") }
    private fun append(line: String) = out.appendText(line + "\n")

    private fun probe(backends: List<Backend>, npuEncoder: String?, npuDecoder: String?) {
        val env = OrtEnvironment.getEnvironment()
        val model = ModelStore.file(this, TripoEngine.DECODER).absolutePath
        val rnd = Random(1)
        val codes = FloatArray(3 * 40 * 64 * 64) { rnd.nextGaussian().toFloat() * 0.5f }
        val points = FloatArray(TripoEngine.CHUNK * 3) { (rnd.nextFloat() * 2f - 1f) * TripoEngine.RADIUS }

        fun run(session: OrtSession): Pair<FloatArray, FloatArray> {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(codes), longArrayOf(3, 40, 64, 64)).use { c ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(points), longArrayOf(TripoEngine.CHUNK.toLong(), 3)).use { p ->
                    session.run(mapOf("scene_codes" to c, "points" to p)).use { r ->
                        val d = (r[0] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                        val col = (r[1] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                        return d to col
                    }
                }
            }
        }

        val ref = env.createSession(model, OrtSession.SessionOptions()).use { run(it) }
        val range = ref.first.maxOf { abs(it) }.coerceAtLeast(1f)
        for (b in backends) {
            append("${b.name} START")
            try {
                val opts = OrtSession.SessionOptions()
                opts.addQnn(TripoEngine.qnnOptions(b))
                // With the NPU pack installed, test the real pre-compiled models: load the big encoder
                // and run it once (this is what failed on-device before), then compare the decoder.
                if (b == Backend.NPU && npuEncoder != null) {
                    val t0 = System.nanoTime()
                    env.createSession(npuEncoder, opts).use { enc ->
                        val load = (System.nanoTime() - t0) / 1e9
                        val img = FloatArray(3 * 512 * 512) { rnd.nextFloat() }
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(img), longArrayOf(1, 3, 512, 512)).use { t ->
                            val t1 = System.nanoTime()
                            enc.run(mapOf("image" to t)).use { r ->
                                val fb = (r[0] as OnnxTensor).floatBuffer
                                val out = FloatArray(fb.remaining()).also { fb.get(it) }
                                if (out.any { it.isNaN() || it.isInfinite() }) throw IllegalStateException("encoder output has NaN/Inf")
                                append("NPU_ENCODER load=%.1fs run=%.0fms".format(load, (System.nanoTime() - t1) / 1e6))
                            }
                        }
                    }
                }
                env.createSession(if (b == Backend.NPU && npuDecoder != null) npuDecoder else model, opts).use { s ->
                    run(s) // warm-up (graph finalization)
                    val t0 = System.nanoTime()
                    val (d, col) = run(s)
                    val ms = (System.nanoTime() - t0) / 1e6
                    var errD = 0f; var errC = 0f
                    for (i in d.indices) errD = maxOf(errD, abs(d[i] - ref.first[i]))
                    for (i in col.indices) errC = maxOf(errC, abs(col[i] - ref.second[i]))
                    val rel = errD / range
                    if (rel < 0.05f && errC < 0.05f) append("${b.name} OK ${ms} err=%.4f/%.4f".format(rel, errC))
                    else append("${b.name} FAILED ${ms} wrong-results err=%.4f/%.4f".format(rel, errC))
                }
            } catch (e: Throwable) {
                append("${b.name} FAILED 0 ${(e.message ?: e.toString()).replace('\n', ' ').take(300)}")
            }
        }
    }
}
