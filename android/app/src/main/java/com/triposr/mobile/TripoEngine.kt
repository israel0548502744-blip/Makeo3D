package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ln

enum class Backend(val label: String, val short: String) {
    NPU("NPU (Hexagon)", "NPU"),
    GPU("GPU (Adreno)", "GPU"),
    CPU("CPU", "CPU"),
}

/** Pipeline steps, with their rough share of the total time (for the progress ring). */
enum class Stage(val label: String, val weight: Float) {
    LOAD(tr("טעינת המודל"), 0.10f),
    SEGMENT(tr("הסרת רקע"), 0.05f),
    ENCODE(tr("ניתוח התמונה"), 0.20f),
    DENOISE(tr("בניית הצורה (TripoSG)"), 0.70f),
    FIELD(tr("בניית שדה תלת־ממד"), 0.35f),
    SURFACE(tr("יצירת רשת פוליגונים"), 0.08f),
    COLOR(tr("צביעה והחלקה"), 0.07f),
    TEXTURE(tr("צביעה חדה (טקסטורה)"), 0.15f),
}

/**
 * Runs the two TripoSR graphs exported by convert/export_onnx.py with ONNX Runtime.
 * NPU/GPU go through the Qualcomm QNN execution provider; any operator QNN cannot take
 * falls back to the CPU automatically.
 */
class TripoEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val ENCODER = "triposr_encoder.onnx"
        const val DECODER = "triposr_decoder.onnx"
        const val SEGMENTER = "u2netp.onnx"
        const val IMAGE_SIZE = 512
        const val CHUNK = 32768          // must match DECODER_CHUNK in export_onnx.py
        const val RADIUS = 0.87f
        const val DENSITY_THRESHOLD = 25f

        /** QNN execution provider options for the NPU (HTP) or GPU backend. */
        fun qnnOptions(b: Backend): Map<String, String> = when (b) {
            Backend.NPU -> mapOf(
                "backend_path" to "libQnnHtp.so",
                "htp_performance_mode" to "burst",
                "enable_htp_fp16_precision" to "1",
            )
            Backend.GPU -> mapOf("backend_path" to "libQnnGpu.so")
            Backend.CPU -> emptyMap()
        }
    }

    private val env = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var segmenter: OrtSession? = null
    var backend: Backend? = null; private set
    var encoderBackend: Backend = Backend.CPU; private set

    /**
     * Opens the models on the first backend in [chain] that works (CPU is always last).
     * [onAttempt] is called before each try, so a native crash can be traced to the backend.
     */
    fun load(chain: List<Backend>, onStatus: (String) -> Unit, onAttempt: (Backend) -> Unit) {
        if (backend == chain.first() && encoder != null) return
        closeSessions()
        var last: Throwable? = null
        for (b in chain) {
            onAttempt(b)
            val t0 = System.nanoTime()
            try {
                onStatus(tr("טוען את המודל ל-{0}", b.label))
                if (b == Backend.NPU) {
                    // Compiling the 1.6 GB encoder for the NPU on the phone takes too long and too much
                    // memory (the system kills the app), so the NPU only uses the pack compiled in CI.
                    if (!NpuPack.isReady(context)) throw IllegalStateException("NPU pack not installed")
                    encoder = npuSession(NpuPack.encoder(context)!!)
                    decoder = npuSession(NpuPack.decoder(context)!!)
                } else {
                    encoder = createSession(ModelStore.file(context, ENCODER), b, "encoder")
                    decoder = createSession(ModelStore.file(context, DECODER), b, "decoder")
                }
                encoderBackend = b
                backend = chain.first()
                CrashLog.log("Sessions ready on ${b.label} in %.1fs".format((System.nanoTime() - t0) / 1e9))
                return
            } catch (e: Throwable) {
                last = e
                CrashLog.error("loading model on ${b.label}", e)
                closeSessions()
                if (e is OutOfMemoryError) System.gc()
            }
        }
        throw IllegalStateException(tr("לא הצלחתי לטעון את המודל על אף מעבד: {0}", last?.message), last)
    }

    @Synchronized
    fun segmenter(): OrtSession = segmenter ?: run {
        val f = File(context.filesDir, SEGMENTER)
        if (!f.exists()) context.assets.open(SEGMENTER).use { i -> f.outputStream().use { i.copyTo(it) } }
        cpuSession(f).also { segmenter = it }
    }

    /** image: CHW float RGB in 0..1, 3 x 512 x 512, background already grey. Returns scene codes [3,40,64,64]. */
    fun encode(image: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(image), shape).use { input ->
            encoder!!.run(mapOf("image" to input)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
    }

    /**
     * Evaluates the triplane NeRF at [count] points produced by [pointAt] (writes xyz into the array).
     * Calls [sink] with (start index, count, density logits, colors) for every chunk.
     */
    fun query(
        sceneCodes: FloatArray,
        count: Int,
        pointAt: (Int, FloatArray) -> Unit,
        sink: (Int, Int, FloatBuffer, FloatBuffer) -> Unit,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val codes = OnnxTensor.createTensor(env, FloatBuffer.wrap(sceneCodes), longArrayOf(3, 40, 64, 64))
        val pts = ByteBuffer.allocateDirect(CHUNK * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val p = FloatArray(3)
        codes.use {
            var start = 0
            while (start < count) {
                if (cancelled()) throw CancellationException()
                val n = minOf(CHUNK, count - start)
                pts.clear()
                for (q in 0 until CHUNK) {
                    if (q < n) pointAt(start + q, p) else { p[0] = 0f; p[1] = 0f; p[2] = 0f }
                    pts.put(p)
                }
                pts.rewind()
                OnnxTensor.createTensor(env, pts, longArrayOf(CHUNK.toLong(), 3)).use { pt ->
                    decoder!!.run(mapOf("scene_codes" to codes, "points" to pt)).use { out ->
                        sink(start, n, (out[0] as OnnxTensor).floatBuffer, (out[1] as OnnxTensor).floatBuffer)
                    }
                }
                start += n
                onProgress(start.toFloat() / count)
            }
        }
    }

    class Output(val mesh: Mesh, val timings: Map<Stage, Double>)

    /**
     * Triplane -> density grid -> surface -> vertex colors -> optional baked texture of
     * [textureSize]² texels (0 = no texture). [progress] gets (stage, 0..1 within it).
     */
    fun reconstruct(
        image: FloatArray,
        resolution: Int,
        smooth: Boolean,
        textureSize: Int,
        smoothIterations: Int = 3,
        densityThreshold: Float = DENSITY_THRESHOLD,
        progress: (Stage, Float) -> Unit,
        cancelled: () -> Boolean,
    ): Output {
        val timings = LinkedHashMap<Stage, Double>()
        fun <T> timed(stage: Stage, block: () -> T): T {
            if (cancelled()) throw CancellationException()
            progress(stage, 0f)
            CrashLog.log("Stage ${stage.name} start")
            val t0 = System.nanoTime()
            val r = block()
            timings[stage] = (System.nanoTime() - t0) / 1e9
            CrashLog.log("Stage ${stage.name} done in %.2fs".format(timings[stage]))
            return r
        }

        val codes = timed(Stage.ENCODE) { encode(image) }

        val res = resolution
        val step = 2f * RADIUS / (res - 1)
        val field = timed(Stage.FIELD) {
            val field = FloatArray(res * res * res)
            val iso = ln(densityThreshold)
            query(codes, field.size,
                pointAt = { idx, p ->
                    p[0] = -RADIUS + (idx / (res * res)) * step
                    p[1] = -RADIUS + (idx / res % res) * step
                    p[2] = -RADIUS + (idx % res) * step
                },
                sink = { start, n, density, _ -> for (q in 0 until n) field[start + q] = density.get(q) - iso },
                onProgress = { progress(Stage.FIELD, it) },
                cancelled = cancelled)
            field
        }

        // Treat the grid's outer layer as empty space so an object touching the bounding box
        // still gets a closed (watertight) surface instead of a hole.
        closeBoundary(field, res)
        var mesh = timed(Stage.SURFACE) { SurfaceNets.extract(field, res, RADIUS) }
        if (mesh.vertexCount == 0) throw IllegalStateException(tr("לא נמצא אובייקט בתמונה. נסה תמונה עם אובייקט ברור במרכז."))
        CrashLog.log("Surface: ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles")

        mesh = timed(Stage.COLOR) {
            val m = if (smooth) MeshTools.smooth(mesh, smoothIterations) else mesh
            val colors = FloatArray(m.positions.size)
            query(codes, m.vertexCount,
                pointAt = { idx, p -> System.arraycopy(m.positions, idx * 3, p, 0, 3) },
                sink = { start, n, _, color -> color.position(0); color.get(colors, start * 3, n * 3) },
                onProgress = { progress(Stage.COLOR, it) },
                cancelled = cancelled)
            m.colors = colors
            m
        }

        if (textureSize > 0) TextureAtlas.build(mesh, textureSize)?.let { atlas ->
            mesh = timed(Stage.TEXTURE) {
                val px = IntArray(textureSize * textureSize)
                val pixelOf = IntArray(CHUNK)
                val src = mesh.positions
                query(codes, atlas.texelCount,
                    pointAt = { idx, p -> pixelOf[idx % CHUNK] = atlas.texel(idx, src, p) },
                    sink = { _, n, _, color ->
                        for (q in 0 until n) {
                            fun ch(k: Int) = (color.get(q * 3 + k).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            px[pixelOf[q]] = (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
                        }
                    },
                    onProgress = { progress(Stage.TEXTURE, it) },
                    cancelled = cancelled)
                Mesh(mesh.positions, mesh.normals, mesh.colors, atlas.indices).apply {
                    uvs = atlas.uvs
                    texture = Texture(textureSize, px)
                }
            }
        } ?: CrashLog.log("Texture skipped: mesh too dense for ${textureSize}px")
        return Output(mesh.toYUp(), timings)
    }

    private fun closeBoundary(field: FloatArray, res: Int) {
        val last = res - 1
        for (i in 0 until res) for (j in 0 until res) for (k in 0 until res) {
            if (i == 0 || j == 0 || k == 0 || i == last || j == last || k == last) {
                val n = (i * res + j) * res + k
                if (field[n] > 0f) field[n] = -1e-3f
            }
        }
    }

    private fun createSession(model: File, b: Backend, cacheName: String): OrtSession {
        if (!model.exists()) throw IllegalStateException(tr("המודל לא הורד עדיין ({0})", model.name))
        if (b == Backend.CPU) return cpuSession(model)
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.addQnn(qnnOptions(b))
        CrashLog.log("Creating ${b.short} session for ${model.name} (${cacheName})")
        return env.createSession(model.absolutePath, opts)
    }

    /** Loads a pre-compiled QNN context model (its .bin file sits next to it). */
    private fun npuSession(ctxModel: File): OrtSession {
        CrashLog.log("Loading NPU context ${ctxModel.name} (${ctxModel.parentFile?.listFiles()?.filter {
            it.name.startsWith(ctxModel.nameWithoutExtension) }?.sumOf { it.length() }?.shr(20)} MB)")
        val opts = OrtSession.SessionOptions()
        opts.addQnn(qnnOptions(Backend.NPU))
        return env.createSession(ctxModel.absolutePath, opts)
    }

    private fun cpuSession(model: File): OrtSession {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
        return env.createSession(model.absolutePath, opts)
    }

    private fun closeSessions() {
        encoder?.close(); decoder?.close()
        encoder = null; decoder = null; backend = null
    }

    override fun close() {
        closeSessions()
        segmenter?.close()
        segmenter = null
    }
}
