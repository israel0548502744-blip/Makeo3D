package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.util.Random
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * TripoSG (VAST-AI, MIT weights) on the NPU: the graphs exported by convert/export_triposg.py and
 * compiled in CI for this chip. DINOv2 encodes the picture, the DiT turns noise into 2048 shape
 * tokens over [steps] flow-matching steps (with and without the image, for guidance), and the
 * VAE's occupancy field is sampled coarse-to-fine: a 64³ grid, then only near the surface at
 * 128³ and 256³. Geometry only, no colors.
 */
class SgEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val CONFIG = "sg_config.json"
        /** The DiT is split into this many parts (3 blocks each), see DIT_PARTS in export_triposg.py. */
        const val DIT_PARTS = 7
        val GRAPHS = listOf("sg_dino") + (0 until DIT_PARTS).map { "sg_dit${it}" } + listOf("sg_vae_kv", "sg_vae_query")
        /** Probe variants (batch x blocks, h = fp16 weights), see triposg-probe.yml. */
        val PROBES = listOf("b1x1", "b2x1", "b1x3", "b1x3h")
        const val COARSE = 64
        const val MAX_TRIANGLES = 600_000
        /** RAM that must stay free after loading a part (below this Android may kill the app). */
        const val MIN_FREE_MB = 1300
        /** A part is only kept loaded across steps while this much RAM stays free. Was 2600 for the
         *  float parts (3x larger); the int8 load test opened all 7 with 1.5 GB still free. */
        const val KEEP_FREE_MB = 1600
        const val LEVELS = 2

        fun files(chip: String) = listOf(CONFIG) + GRAPHS.flatMap { listOf("${it}_${chip}_ctx.onnx", "${it}_${chip}_ctx_qnn.bin") }
    }

    class Config(o: JSONObject) {
        val tokens = o.getInt("tokens")
        val channels = o.getInt("latent_channels")
        val width = o.getInt("width")
        val condDim = o.getInt("cond_dim")
        val guidance = o.getDouble("guidance_scale").toFloat()
        /** 2 = both guidance branches in one call; 1 = one branch per call (fits the NPU's memory). */
        val batch = o.optInt("batch", 2)
        /** Sigma range in which the guidance pass runs when the "smart guidance" option is on. */
        val guidanceInterval = o.optJSONArray("guidance_interval")?.let { it.getDouble(0) to it.getDouble(1) } ?: (0.1 to 0.8)
        val defaultSteps = o.getInt("default_steps")
        val shift = o.getJSONObject("scheduler").getDouble("shift")
        private val dino = o.getJSONObject("dino")
        val resize = dino.getInt("resize_shortest")
        val crop = dino.getInt("crop")
        val rescale = dino.getDouble("rescale").toFloat()
        val mean = FloatArray(3) { dino.getJSONArray("mean").getDouble(it).toFloat() }
        val std = FloatArray(3) { dino.getJSONArray("std").getDouble(it).toFloat() }
        private val vae = o.getJSONObject("vae")
        val chunk = vae.getInt("query_chunk")
        val freqs = vae.getInt("embed_freqs")
        val bounds = vae.getDouble("bounds").toFloat()
        val padding = o.getJSONObject("preprocess").getDouble("padding_ratio").toFloat()
        /** Per DiT part: input and output names in order. */
        val parts = o.getJSONArray("dit_parts").let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                fun names(k: String) = p.getJSONArray(k).let { n -> (0 until n.length()).map { n.getString(it) } }
                names("inputs") to names("outputs")
            }
        }

        /** Flow-matching sigmas for [steps] steps (shifted linear schedule), plus a final 0. */
        fun sigmas(steps: Int) = DoubleArray(steps + 1) { i ->
            if (i == steps) 0.0 else (1.0 - i.toDouble() / steps).let { t -> shift * t / (1 + (shift - 1) * t) }
        }
    }

    private val env = OrtEnvironment.getEnvironment()
    private val chip = NpuPack.chip() ?: throw IllegalStateException(tr("TripoSG צריך מעבד Snapdragon 8 Elite"))
    val config = Config(JSONObject(ModelStore.file(context, CONFIG).readText()))

    private val activity = context.getSystemService(android.app.ActivityManager::class.java)
    private fun freeMb() = android.app.ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }.availMem shr 20
    private fun partMb(graph: String) = ModelStore.file(context, "${graph}_${chip}_ctx_qnn.bin").length() shr 20

    private fun open(graph: String): OrtSession {
        val f = ModelStore.file(context, "${graph}_${chip}_ctx.onnx")
        if (!f.exists()) throw IllegalStateException(tr("חבילת TripoSG לא הורדה ({0})", f.name))
        val opts = OrtSession.SessionOptions()
        opts.addQnn(TripoEngine.qnnOptions(Backend.NPU))
        val t0 = System.nanoTime()
        return env.createSession(f.absolutePath, opts).also {
            CrashLog.log("TripoSG: opened ${graph} in %.1fs".format((System.nanoTime() - t0) / 1e9))
        }
    }

    /**
     * Diagnostic: opens the NPU graphs one after another and keeps them open, to find how many
     * fit at once, then retries a failed one alone. Each line goes to the report.
     */
    fun loadTest(): String {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        fun freeMb() = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem shr 20
        val lines = ArrayList<String>()
        fun line(t: String) { lines += t; CrashLog.log("TripoSG load test: ${t}") }
        line("free RAM ${freeMb()} MB")
        val open = ArrayList<OrtSession>()
        var failed: String? = null
        try {
            for (g in GRAPHS) {
                val size = ModelStore.file(context, "${g}_${chip}_ctx_qnn.bin").length() shr 20
                if (freeMb() - size < MIN_FREE_MB) { line("${g} (${size} MB): not tried, only ${freeMb()} MB RAM free"); break }
                val r = runCatching { open(g) }
                r.onSuccess { open += it; line("${g} (${size} MB): OK, ${open.size} open, free ${freeMb()} MB") }
                r.onFailure { line("${g} (${size} MB): FAILED with ${open.size} open: ${it.message?.take(160)}") }
                if (r.isFailure) { failed = g; break }
            }
        } finally {
            open.forEach { it.close() }
        }
        failed?.let { g ->
            line("free after closing ${freeMb()} MB")
            runCatching { open(g).close() }
                .onSuccess { line("${g} alone: OK") }
                .onFailure { line("${g} alone: FAILED: ${it.message?.take(160)}") }
        }
        if (ModelStore.isReady(context, ModelStore.SG_PROBE)) for (p in PROBES) {
            val g = "sg_probe_${p}"
            val size = ModelStore.file(context, "${g}_${chip}_ctx_qnn.bin").length() shr 20
            runCatching { open(g).close() }
                .onSuccess { line("${g} (${size} MB): OK") }
                .onFailure { line("${g} (${size} MB): FAILED: ${it.message?.substringAfterLast("Error code:")?.take(60)}") }
        }
        return lines.joinToString("\n")
    }

    /** Summary of a tensor for the report: NaNs, range, mean of |x|. */
    private fun stats(a: FloatArray): String {
        var nan = 0; var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var sum = 0.0
        for (v in a) if (v.isNaN() || v.isInfinite()) nan++ else { if (v < lo) lo = v; if (v > hi) hi = v; sum += abs(v) }
        return "n=${a.size} bad=${nan} min=%.3g max=%.3g mean|x|=%.3g".format(lo, hi, sum / maxOf(1, a.size - nan))
    }

    private fun tensor(data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun OrtSession.Result.floats(i: Int = 0): FloatArray =
        (get(i) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }

    // ------------------------------------------------------------------ image

    /**
     * Like TripoSG's prepare_image: crop to the object (alpha), white background, pad 10% and
     * to a square, then the DINOv2 processor's resize + centre crop and normalisation. CHW floats.
     */
    fun prepare(cut: Bitmap): FloatArray {
        val c = config
        val w = cut.width; val h = cut.height
        val px = IntArray(w * h).also { cut.getPixels(it, 0, w, 0, 0, w, h) }
        var x1 = w; var y1 = h; var x2 = -1; var y2 = -1
        for (y in 0 until h) for (x in 0 until w) if (Color.alpha(px[y * w + x]) > 127) {
            if (x < x1) x1 = x; if (x > x2) x2 = x; if (y < y1) y1 = y; if (y > y2) y2 = y
        }
        if (x2 < 0) { x1 = 0; y1 = 0; x2 = w - 1; y2 = h - 1 }
        val bw = x2 - x1 + 1; val bh = y2 - y1 + 1
        val side = (maxOf(bw, bh) * (1 + 2 * c.padding)).toInt()
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.WHITE)
            drawBitmap(Bitmap.createBitmap(cut, x1, y1, bw, bh), ((side - bw) / 2).toFloat(), ((side - bh) / 2).toFloat(),
                Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val scaled = Bitmap.createScaledBitmap(square, c.resize, c.resize, true)
        val o = (c.resize - c.crop) / 2
        val img = Bitmap.createBitmap(scaled, o, o, c.crop, c.crop)
        val n = c.crop * c.crop
        val p = IntArray(n).also { img.getPixels(it, 0, c.crop, 0, 0, c.crop, c.crop) }
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            out[i] = (Color.red(p[i]) * c.rescale - c.mean[0]) / c.std[0]
            out[n + i] = (Color.green(p[i]) * c.rescale - c.mean[1]) / c.std[1]
            out[2 * n + i] = (Color.blue(p[i]) * c.rescale - c.mean[2]) / c.std[2]
        }
        return out
    }

    // ------------------------------------------------------------------ embeddings (fp32 on the CPU)

    /** diffusers Timesteps(width, flip_sin_to_cos=False, shift 0): [sin, cos], twice (batch 2). */
    private fun timestepEmbedding(t: Double): FloatArray {
        val dim = config.width; val half = dim / 2
        val out = FloatArray(2 * dim)
        for (i in 0 until half) {
            val a = t * exp(-ln(10000.0) * i / half)
            out[i] = sin(a).toFloat(); out[half + i] = cos(a).toFloat()
        }
        System.arraycopy(out, 0, out, dim, dim)
        return out
    }

    /** [x, sin(2^k x), cos(2^k x)] per point, as in FrequencyPositionalEmbedding. */
    private fun embedPoint(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
        val f = config.freqs
        out[at] = x; out[at + 1] = y; out[at + 2] = z
        var o = at + 3
        for (k in 0..2) {
            val p = (if (k == 0) x else if (k == 1) y else z).toDouble()
            for (j in 0 until f) out[o++] = sin(p * (1 shl j)).toFloat()
        }
        for (k in 0..2) {
            val p = (if (k == 0) x else if (k == 1) y else z).toDouble()
            for (j in 0 until f) out[o++] = cos(p * (1 shl j)).toFloat()
        }
    }

    // ------------------------------------------------------------------ pipeline

    fun generate(cut: Bitmap, steps: Int, seed: Long, smoothIterations: Int, guidanceInterval: Boolean = false, texture: Boolean = true, progress: (Stage, Float) -> Unit,
                 cancelled: () -> Boolean): TripoEngine.Output {
        val c = config
        val timings = LinkedHashMap<Stage, Double>()
        fun <T> timed(stage: Stage, block: () -> T): T {
            if (cancelled()) throw CancellationException()
            progress(stage, 0f)
            val t0 = System.nanoTime()
            val r = block()
            timings[stage] = (System.nanoTime() - t0) / 1e9
            CrashLog.log("TripoSG ${stage.name} done in %.2fs".format(timings[stage]))
            return r
        }

        val cond = timed(Stage.ENCODE) {
            open("sg_dino").use { s ->
                tensor(prepare(cut), 1, 3, c.crop.toLong(), c.crop.toLong()).use { x ->
                    s.run(mapOf("image" to x)).use { it.floats() }
                }
            }
        }

        val latents = timed(Stage.DENOISE) {
            if (c.batch == 1) denoiseSingle(cond, steps, seed, guidanceInterval, progress, cancelled)
            else denoise(cond, steps, seed, progress, cancelled)
        }

        CrashLog.log("TripoSG cond ${stats(cond)}; latents ${stats(latents)}")
        val res = COARSE shl LEVELS
        val field = timed(Stage.FIELD) { field(latents, progress, cancelled) }
        CrashLog.log("TripoSG field ${stats(field)}, inside ${"%.1f".format(field.count { it > 0 } * 100.0 / field.size)}%")

        var mesh = timed(Stage.SURFACE) { SurfaceNets.extract(field, res + 1, c.bounds) }
        if (mesh.vertexCount == 0) throw IllegalStateException(tr("TripoSG לא מצא צורה. נסה תמונה אחרת."))
        CrashLog.log("TripoSG surface: ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles")
        var projection: MeshColor.Projection? = null
        mesh = timed(Stage.COLOR) {
            var m = if (smoothIterations > 0) MeshTools.smooth(mesh, smoothIterations) else mesh
            // Above ~600k triangles the viewer and the print check run out of Java heap.
            if (m.triangleCount > MAX_TRIANGLES) m = MeshTools.simplify(m, 1f - MAX_TRIANGLES.toFloat() / m.triangleCount)
            m.colors = runCatching { MeshColor.project(m, cut).also { projection = it }.let { MeshColor.paint(m, it) } }
                .onFailure { CrashLog.error("TripoSG colors", it) }
                .getOrElse { FloatArray(m.positions.size) { 0.82f } }
            m
        }
        // Sharp colors: a texture sampled from the full-size picture.
        val proj = projection
        if (texture && proj != null) mesh = timed(Stage.TEXTURE) {
            runCatching { MeshColor.bake(mesh, proj) }.onFailure { CrashLog.error("TripoSG texture", it) }.getOrNull()
                ?: mesh.also { CrashLog.log("TripoSG texture skipped: mesh too dense") }
        }
        return TripoEngine.Output(mesh, timings)
    }

    private fun denoise(cond: FloatArray, steps: Int, seed: Long, progress: (Stage, Float) -> Unit,
                        cancelled: () -> Boolean): FloatArray {
        val c = config
        val n = c.tokens * c.channels
        val rnd = Random(seed)
        val lat = FloatArray(n) { rnd.nextGaussian().toFloat() }
        val sig = c.sigmas(steps)
        val condTokens = cond.size / c.condDim
        // Batch 2: [no image, image]. The unconditional branch sees zeros.
        val cond2 = FloatArray(2 * cond.size).also { System.arraycopy(cond, 0, it, cond.size, cond.size) }
        if (c.parts.size != DIT_PARTS) throw IllegalStateException(tr("חבילת TripoSG לא תואמת לגרסת האפליקציה, עדכן אחד מהם"))
        val sessions = ArrayList<OrtSession>()
        try { for (i in 0 until DIT_PARTS) sessions += open("sg_dit${i}") } catch (e: Throwable) { sessions.forEach { it.close() }; throw e }
        try {
            tensor(cond2, 2, condTokens.toLong(), c.condDim.toLong()).use { condT ->
                val both = FloatArray(2 * n)
                for (i in 0 until steps) {
                    if (cancelled()) throw CancellationException()
                    val t0 = System.nanoTime()
                    System.arraycopy(lat, 0, both, 0, n); System.arraycopy(lat, 0, both, n, n)
                    val v = tensor(both, 2, c.tokens.toLong(), c.channels.toLong()).use { latT ->
                        tensor(timestepEmbedding(sig[i] * 1000), 2, c.width.toLong()).use { tembT ->
                            runDit(sessions, latT, tembT, condT)
                        }
                    }
                    // Guidance, then one Euler step along the predicted flow.
                    val dt = (sig[i] - sig[i + 1]).toFloat()
                    for (k in 0 until n) lat[k] += dt * (v[k] + c.guidance * (v[n + k] - v[k]))
                    if (i == 0) CrashLog.log("TripoSG step 1 in %.2fs".format((System.nanoTime() - t0) / 1e9))
                    progress(Stage.DENOISE, (i + 1f) / steps)
                }
            }
        } finally {
            sessions.forEach { it.close() }
        }
        return lat
    }

    /**
     * Denoising with batch-1 parts: every part runs twice per step (without and with the image).
     * A batch-2 part does not fit in the NPU's memory, and neither do all seven parts at once, so
     * as many parts as fit stay loaded and the rest are loaded for each step and closed again.
     */
    private fun denoiseSingle(cond: FloatArray, steps: Int, seed: Long, guidanceInterval: Boolean, progress: (Stage, Float) -> Unit,
                              cancelled: () -> Boolean): FloatArray {
        val c = config
        val n = c.tokens * c.channels
        val rnd = Random(seed)
        val lat = FloatArray(n) { rnd.nextGaussian().toFloat() }
        val sig = c.sigmas(steps)
        val condTokens = (cond.size / c.condDim).toLong()
        if (c.parts.size != DIT_PARTS) throw IllegalStateException(tr("חבילת TripoSG לא תואמת לגרסת האפליקציה, עדכן אחד מהם"))
        val cached = arrayOfNulls<OrtSession>(DIT_PARTS)
        var budget = DIT_PARTS
        var guidedSteps = 0
        /** The session for part i and whether it must be closed after use. */
        fun session(i: Int): Pair<OrtSession, Boolean> {
            cached[i]?.let { return it to false }
            val need = partMb("sg_dit${i}")
            // Each loaded part also takes its size in RAM, and Android kills the app when free
            // memory runs low: drop kept parts until this one fits with a margin.
            while (freeMb() - need < MIN_FREE_MB && cached.any { it != null }) {
                val j = cached.indexOfLast { it != null }
                cached[j]!!.close(); cached[j] = null
                budget = minOf(budget, cached.count { it != null } + 1)
                CrashLog.log("TripoSG: RAM low (${freeMb()} MB free), unloaded part ${j}, keeping ${budget - 1}")
            }
            while (true) {
                val open = cached.count { it != null }
                val roomy = freeMb() - need >= KEEP_FREE_MB
                try {
                    val s = open("sg_dit${i}")
                    // Keep it if there is room, leaving one slot for the parts loaded per step.
                    val keep = roomy && (open < budget - 1 || (budget == DIT_PARTS && open < budget))
                    if (!keep && budget > open + 1) {
                        budget = open + 1
                        CrashLog.log("TripoSG: keeping ${open} parts loaded (${freeMb()} MB RAM free), the rest per step")
                    }
                    return if (keep) { cached[i] = s; s to false } else s to true
                } catch (e: Throwable) {
                    if (open == 0) throw e
                    budget = open
                    CrashLog.log("TripoSG: NPU full with ${open} parts loaded, keeping ${budget - 1}")
                    for (j in cached.indices.reversed()) if (cached[j] != null && cached.count { it != null } > budget - 1) {
                        cached[j]!!.close(); cached[j] = null
                    }
                }
            }
        }
        try {
            tensor(FloatArray(cond.size), 1, condTokens, c.condDim.toLong()).use { uncondT ->
                tensor(cond, 1, condTokens, c.condDim.toLong()).use { condT ->
                    for (i in 0 until steps) {
                        if (cancelled()) throw CancellationException()
                        val t0 = System.nanoTime()
                        // Outside the interval only the image branch runs (no guidance on this step).
                        val guided = !guidanceInterval || sig[i] in c.guidanceInterval.first..c.guidanceInterval.second
                        if (guided) guidedSteps++
                        val v = tensor(lat.copyOf(), 1, c.tokens.toLong(), c.channels.toLong()).use { latT ->
                            tensor(timestepEmbedding(sig[i] * 1000).copyOf(c.width), 1, c.width.toLong()).use { tembT ->
                                runDitSingle(::session, latT, tembT, if (guided) listOf(uncondT, condT) else listOf(condT))
                            }
                        }
                        val dt = (sig[i] - sig[i + 1]).toFloat()
                        if (guided) {
                            val (vu, vc) = v
                            for (k in 0 until n) lat[k] += dt * (vu[k] + c.guidance * (vc[k] - vu[k]))
                        } else {
                            val vc = v[0]
                            for (k in 0 until n) lat[k] += dt * vc[k]
                        }
                        if (i == 0) CrashLog.log("TripoSG step 1 velocity ${stats(v.last())}")
                        if (i == 0) CrashLog.log("TripoSG step 1 in %.2fs, %d of %d parts kept loaded".format(
                            (System.nanoTime() - t0) / 1e9, cached.count { it != null }, DIT_PARTS))
                        progress(Stage.DENOISE, (i + 1f) / steps)
                    }
                }
            }
        } finally {
            cached.forEach { it?.close() }
        }
        CrashLog.log("TripoSG: guidance on ${guidedSteps} of ${steps} steps")
        return lat
    }

    /** One DiT evaluation per condition, part by part; each part runs all conditions while loaded. */
    private fun runDitSingle(session: (Int) -> Pair<OrtSession, Boolean>, latT: OnnxTensor, tembT: OnnxTensor,
                             conds: List<OnnxTensor>): List<FloatArray> {
        val results = ArrayList<OrtSession.Result>()
        val named = conds.map { HashMap<String, OnnxTensor>() }
        val hidden = arrayOfNulls<OnnxTensor>(conds.size)
        val out = arrayOfNulls<FloatArray>(conds.size)
        try {
            for (i in 0 until DIT_PARTS) {
                val (sess, temporary) = session(i)
                try {
                    val (inputs, outputs) = config.parts[i]
                    for (b in conds.indices) {
                        val feeds = HashMap<String, OnnxTensor>()
                        for (name in inputs) feeds[name] = when (name) {
                            "latents" -> latT
                            "temb" -> tembT
                            "cond" -> conds[b]
                            "hidden_in" -> hidden[b] ?: throw IllegalStateException("no hidden state for part ${i}")
                            else -> named[b][name] ?: throw IllegalStateException("missing ${name} for part ${i}")
                        }
                        val r = sess.run(feeds)
                        if (i == DIT_PARTS - 1) { out[b] = r.use { it.floats() }; continue }
                        // Outputs are ORT-owned CPU buffers, independent of the session, so they stay
                        // valid after a per-step part is closed (copying them ran out of Java heap).
                        results += r
                        for ((k, name) in outputs.withIndex()) {
                            if (name == "hidden") hidden[b] = r[k] as OnnxTensor else named[b][name] = r[k] as OnnxTensor
                        }
                    }
                } finally {
                    if (temporary) sess.close()
                }
            }
        } finally {
            results.forEach { it.close() }
        }
        return out.map { it ?: throw IllegalStateException("DiT has no final part") }
    }

    /**
     * One DiT evaluation through the parts in order. Each part's outputs stay open until the end
     * (U-Net skips made early are consumed by late parts), and are fed by name.
     */
    private fun runDit(s: List<OrtSession>, latT: OnnxTensor, tembT: OnnxTensor, condT: OnnxTensor): FloatArray {
        val results = ArrayList<OrtSession.Result>()
        try {
            val named = HashMap<String, OnnxTensor>()
            var hidden: OnnxTensor? = null
            for ((i, sess) in s.withIndex()) {
                val (inputs, outputs) = config.parts[i]
                val feeds = HashMap<String, OnnxTensor>()
                for (name in inputs) feeds[name] = when (name) {
                    "latents" -> latT
                    "temb" -> tembT
                    "cond" -> condT
                    "hidden_in" -> hidden ?: throw IllegalStateException("no hidden state for part ${i}")
                    else -> named[name] ?: throw IllegalStateException("missing ${name} for part ${i}")
                }
                val r = sess.run(feeds)
                if (i == s.size - 1) return r.use { it.floats() }
                results += r
                for ((k, name) in outputs.withIndex()) {
                    if (name == "hidden") hidden = r[k] as OnnxTensor else named[name] = r[k] as OnnxTensor
                }
            }
            throw IllegalStateException("DiT has no final part")
        } finally {
            results.forEach { it.close() }
        }
    }

    /** Occupancy logits on a (res+1)³ grid over [-bounds, bounds]³, positive inside. */
    private fun field(latents: FloatArray, progress: (Stage, Float) -> Unit, cancelled: () -> Boolean): FloatArray {
        val c = config
        val kv = open("sg_vae_kv").use { s ->
            tensor(latents, 1, c.tokens.toLong(), c.channels.toLong()).use { t -> s.run(mapOf("latents" to t)).use { it.floats() } }
        }
        open("sg_vae_query").use { s ->
            tensor(kv, 1, c.tokens.toLong(), (kv.size / c.tokens).toLong()).use { kvT ->
                val dim = 3 * (2 * c.freqs + 1)
                // Two buffers: the next chunk's points are embedded on the CPU while the NPU runs this one.
                val bufs = arrayOf(FloatArray(c.chunk * dim), FloatArray(c.chunk * dim))
                val embedder = java.util.concurrent.Executors.newSingleThreadExecutor()
                var done = 0L; var total = 0L; var shown = 0f
                var embedNs = 0L; var waitNs = 0L; var runNs = 0L; var chunks = 0
                /** Evaluates [count] points of a (r+1)³ grid given by flat indices. */
                fun query(r: Int, idx: IntArray, count: Int, out: FloatArray) {
                    val g = r + 1; val step = 2f * c.bounds / r
                    fun embed(start: Int, buf: FloatArray) = embedder.submit(java.util.concurrent.Callable {
                        val t0 = System.nanoTime()
                        val m = minOf(c.chunk, count - start)
                        for (q in 0 until m) {
                            val id = idx[start + q]
                            embedPoint(-c.bounds + id / (g * g) * step, -c.bounds + id / g % g * step,
                                -c.bounds + id % g * step, buf, q * dim)
                        }
                        if (m < c.chunk) java.util.Arrays.fill(buf, m * dim, buf.size, 0f)
                        embedNs += System.nanoTime() - t0
                    })
                    var start = 0
                    var cur = 0
                    var pending = embed(0, bufs[0])
                    while (start < count) {
                        if (cancelled()) throw CancellationException()
                        val m = minOf(c.chunk, count - start)
                        val t0 = System.nanoTime()
                        pending.get()
                        val t1 = System.nanoTime()
                        if (start + m < count) pending = embed(start + m, bufs[1 - cur])
                        val logits = tensor(bufs[cur], 1, c.chunk.toLong(), dim.toLong()).use { p ->
                            s.run(mapOf("kv" to kvT, "points" to p)).use { it.floats() }
                        }
                        waitNs += t1 - t0; runNs += System.nanoTime() - t1; chunks++
                        // The exported decoder is positive outside: a mug came out 83% "inside" (a
                        // closed box around it). Flipped so that inside is > 0 for SurfaceNets.
                        for (q in 0 until m) out[idx[start + q]] = -logits[q]
                        start += m; done += m; cur = 1 - cur
                        // Each level's size is known only when it starts: never let the bar go back.
                        shown = maxOf(shown, (done.toFloat() / total.coerceAtLeast(1)).coerceAtMost(0.99f))
                        progress(Stage.FIELD, shown)
                    }
                }

                try {
                    var r = COARSE
                    var g = r + 1
                    var vals = FloatArray(g * g * g)
                    total = vals.size.toLong() * 5  // rough: measured 1.0-1.8M points in all after a 275k first level
                    query(r, IntArray(vals.size) { it }, vals.size, vals)
                    val counts = arrayListOf(vals.size)
                    repeat(LEVELS) { level ->
                        // The last level only needs the cells the surface crosses: no extra coarse margin
                        // (checked against the full shell by sg_eval.py field).
                        val near = nearSurface(vals, g, grow = level < LEVELS - 1)
                        val r2 = r * 2; val g2 = r2 + 1
                        val fine = upsample(vals, g)
                        val mask = BooleanArray(g2 * g2 * g2)
                        for (i in 0 until g2) for (j in 0 until g2) for (k in 0 until g2)
                            if (near[((i / 2) * g + j / 2) * g + k / 2]) mask[(i * g2 + j) * g2 + k] = true
                        val grown = dilate(mask, g2)
                        val idx = IntArray(grown.count { it }).also { a -> var n = 0; for (q in grown.indices) if (grown[q]) a[n++] = q }
                        counts += idx.size
                        if (level == LEVELS - 1) total = done + idx.size
                        query(r2, idx, idx.size, fine)
                        vals = fine; r = r2; g = g2
                    }
                    CrashLog.log("TripoSG field: query points per level ${counts}, ${chunks} chunks: NPU %.1fs, ".format(runNs / 1e9) +
                        "embedding %.1fs (waited %.1fs)".format(embedNs / 1e9, waitNs / 1e9))
                    // Outer layer counts as outside, so the surface is always closed.
                    for (i in 0 until g) for (j in 0 until g) for (k in 0 until g)
                        if (i == 0 || j == 0 || k == 0 || i == g - 1 || j == g - 1 || k == g - 1) {
                            val q = (i * g + j) * g + k
                            if (vals[q] > 0f) vals[q] = -1e-3f
                        }
                    return vals
                } finally {
                    embedder.shutdown()
                }
            }
        }
    }

    /** Points whose value is small or whose sign differs from a neighbour, grown by one if [grow]. */
    private fun nearSurface(v: FloatArray, g: Int, grow: Boolean = true): BooleanArray {
        val near = BooleanArray(v.size)
        for (i in 0 until g) for (j in 0 until g) for (k in 0 until g) {
            val q = (i * g + j) * g + k
            val s = v[q] > 0f
            near[q] = abs(v[q]) < 0.95f ||
                (i > 0 && (v[q - g * g] > 0f) != s) || (i < g - 1 && (v[q + g * g] > 0f) != s) ||
                (j > 0 && (v[q - g] > 0f) != s) || (j < g - 1 && (v[q + g] > 0f) != s) ||
                (k > 0 && (v[q - 1] > 0f) != s) || (k < g - 1 && (v[q + 1] > 0f) != s)
        }
        return if (grow) dilate(near, g) else near
    }

    /** 6-neighbour dilation by one. */
    private fun dilate(m: BooleanArray, g: Int): BooleanArray {
        val out = m.copyOf()
        for (i in 0 until g) for (j in 0 until g) for (k in 0 until g) {
            val q = (i * g + j) * g + k
            if (!m[q]) continue
            if (i > 0) out[q - g * g] = true; if (i < g - 1) out[q + g * g] = true
            if (j > 0) out[q - g] = true; if (j < g - 1) out[q + g] = true
            if (k > 0) out[q - 1] = true; if (k < g - 1) out[q + 1] = true
        }
        return out
    }

    /** Trilinear 2x upsampling of a g³ grid to (2g-1)³, corners aligned. */
    private fun upsample(v: FloatArray, g: Int): FloatArray {
        val g2 = 2 * g - 1
        val out = FloatArray(g2 * g2 * g2)
        for (i in 0 until g2) for (j in 0 until g2) for (k in 0 until g2) {
            val i0 = i / 2; val j0 = j / 2; val k0 = k / 2
            val i1 = i0 + i % 2; val j1 = j0 + j % 2; val k1 = k0 + k % 2
            out[(i * g2 + j) * g2 + k] = 0.125f * (
                v[(i0 * g + j0) * g + k0] + v[(i0 * g + j0) * g + k1] + v[(i0 * g + j1) * g + k0] + v[(i0 * g + j1) * g + k1] +
                v[(i1 * g + j0) * g + k0] + v[(i1 * g + j0) * g + k1] + v[(i1 * g + j1) * g + k0] + v[(i1 * g + j1) * g + k1])
        }
        return out
    }

    override fun close() {}
}
