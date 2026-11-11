package com.triposr.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Create : Screen
    data object Processing : Screen
    data object Result : Screen
    data object Projects : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
    data object Extra : Screen
    data object Models : Screen
    data object Legal : Screen
    /** Everything that can be made, in groups. */
    data object Make : Screen
    /** One group of products ([AppState.category]). */
    data object Category : Screen
    data object More : Screen
}

data class JobState(
    val stage: Stage = Stage.LOAD,
    val stageProgress: Float = 0f,
    val status: String = "",
    val error: String? = null,
    val done: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val backend: Backend = Backend.GPU,
    /** Stages this job goes through (they differ per generator). */
    val plan: List<Stage> = Generator.TRIPOSR.stages,
) {
    /** 0..1 over the whole pipeline, weighted by how long each stage usually takes. */
    val overall: Float
        get() {
            if (done) return 1f
            var sum = 0f
            for (s in plan) {
                if (s.ordinal < stage.ordinal) sum += s.weight
                else if (s == stage) sum += s.weight * stageProgress
            }
            return (sum / plan.sumOf { it.weight.toDouble() }.toFloat()).coerceIn(0f, 1f)
        }
}

/** What the model will be given: the 512 px frame after background removal, and warnings about it. */
class Preview(
    val source: Bitmap,
    val removeBackground: Boolean,
    val framed: Bitmap,
    val issues: List<ImageIssue>,
    /** The image actually processed: [source], or its 4x upscale when that extra is on. */
    val work: Bitmap = source,
    /** Tap-to-select points in [work] pixels; when set they decide the object instead of background removal. */
    val points: List<Pair<Float, Float>> = emptyList(),
    /** [work] with everything outside the selection dimmed, shown while tapping. */
    val selection: Bitmap? = null,
    /** [work] with the foreground as alpha, before framing (TripoSG frames it its own way). */
    val cut: Bitmap? = null,
)

data class Project(
    val id: String,
    val name: String,
    val created: Long,
    val backend: String,
    val quality: String,
    val seconds: Double,
    val vertices: Int,
    val triangles: Int,
    /** Seconds per pipeline stage (Stage names), for the result screen and diagnostics. */
    val timings: Map<String, Double> = emptyMap(),
    /** TRIPOSR, TRIPOSG or a Creations.Kind name; inferred for projects saved before it existed. */
    val kind: String = "",
    /** A print product's text and options (JSON), so it can be opened again and changed. */
    val params: String = "",
    /** Liked by the user: listed under its own tab and never cleared away. */
    val favorite: Boolean = false,
) {
    /** Gallery tab: "sr", "sg" or "craft". */
    val group: String get() = when {
        kind == Generator.TRIPOSG.name || (kind.isEmpty() && Stage.DENOISE.name in timings) -> "sg"
        kind == Generator.TRIPOSR.name || (kind.isEmpty() && backend != "—") -> "sr"
        else -> "craft"
    }
    /** The picture the model was made from, for "create again" (thumbnail only for older projects). */
    fun source(ctx: Context): Bitmap? = (File(dir(ctx), "source.jpg").takeIf { it.exists() } ?: File(dir(ctx), "thumb.png")
        .takeIf { it.exists() })?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** Image -> mesh with vertex colors (encode, density field, surface, colors). */
    val geometrySeconds: Double get() = listOf(Stage.ENCODE, Stage.DENOISE, Stage.FIELD, Stage.SURFACE, Stage.COLOR).sumOf { timings[it.name] ?: 0.0 }
    val textureSeconds: Double? get() = timings[Stage.TEXTURE.name]

    fun dir(ctx: Context) = File(File(ctx.filesDir, "projects"), id)
    fun thumb(ctx: Context): Bitmap? = File(dir(ctx), "thumb.png").takeIf { it.exists() }
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    val date: String get() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(created))
}

/**
 * App-wide state. It lives in the Application (not the Activity) so a running reconstruction
 * keeps going when the user leaves the app; ProcessingService keeps the process alive meanwhile.
 */
object AppState {
    lateinit var app: Context; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: TripoEngine? = null
    private var runningJob: Job? = null
    @Volatile private var cancelRequested = false

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    /** The "what do you want to make?" sheet opened by the + button. */
    var createMenu by mutableStateOf(false)
    /** Which printable the creations screen opens on. */
    var extraKind by mutableStateOf(Creations.Kind.LITHOPHANE)
    /** 0 = terms, 1 = privacy, 2 = open-source licenses. */
    var legalTab by mutableIntStateOf(0)

    /** The group the Category screen shows (a ui Category name). */
    var category by mutableStateOf("PHOTO")

    fun openCategory(name: String) { category = name; navigate(Screen.Category) }

    /** What the product screen opens with: a saved product being changed, or a preset. Taken once. */
    class Draft(val kind: Creations.Kind, val text: String, val options: Creations.Options, val image: Bitmap?)
    var draft: Draft? = null

    /** Opens a saved print product in its screen again, with the text, picture and options it was made with. */
    fun editAgain(p: Project) {
        scope.launch {
            val kind = runCatching { Creations.Kind.valueOf(p.kind) }.getOrNull() ?: return@launch
            val (text, options) = Creations.decode(p.params) ?: return@launch
            val image = withContext(Dispatchers.IO) { File(p.dir(app), "source.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
            withContext(Dispatchers.Main) { draft = Draft(kind, text, options, image); openExtra(kind) }
        }
    }

    /** Counts how often each thing is made, for "used lately" on the home screen. */
    fun used(id: String) { prefs().edit().putInt("used_$id", prefs().getInt("used_$id", 0) + 1).putLong("usedAt_$id", System.currentTimeMillis()).apply() }

    /** Ids made most often (latest first among equals). */
    fun mostUsed(n: Int): List<String> = prefs().all.keys.filter { it.startsWith("used_") }.map { it.removePrefix("used_") }
        .sortedWith(compareByDescending<String> { prefs().getInt("used_$it", 0) }.thenByDescending { prefs().getLong("usedAt_$it", 0) }).take(n)

    /** The print format the export button offers: the one used last. */
    var printFormat: String
        get() = prefs().getString("print_format", ExportFormat.THREE_MF.name)!!
        set(v) { prefs().edit().putString("print_format", v).apply() }

    fun openExtra(kind: Creations.Kind) {
        createMenu = false
        extraKind = kind
        navigate(Screen.Extra)
    }

    fun openLegal(tab: Int) {
        legalTab = tab
        navigate(Screen.Legal)
    }
    val screen: Screen get() = backStack.last()

    var input by mutableStateOf<Bitmap?>(null)
    var framed by mutableStateOf<Bitmap?>(null)
    var preview by mutableStateOf<Preview?>(null)
    var previewBusy by mutableStateOf(false)
    private var sourceInfo = SourceInfo()
    private var previewJob: Job? = null
    var job by mutableStateOf<JobState?>(null)
    var project by mutableStateOf<Project?>(null)
    var mesh by mutableStateOf<Mesh?>(null)
    var originalMesh: Mesh? = null
    val projects = mutableStateListOf<Project>()
    var update by mutableStateOf<Updater.Update?>(null)
    /** Shown once on the home screen, e.g. "NPU crashed, switched to GPU". */
    var notice by mutableStateOf<String?>(null)

    private fun prefs() = app.getSharedPreferences("state", Context.MODE_PRIVATE)

    fun init(context: Context) {
        app = context.applicationContext
        // The previous run died while a backend was in use. Only count it against the backend when
        // Android reports an abnormal exit (crash / native crash / ANR / low memory), not a force-stop.
        prefs().getString("active_backend", null)?.let { name ->
            prefs().edit().remove("active_backend").commit()
            if (!CrashLog.previousSessionCrashed) return@let
            val crashed = Backend.valueOf(name)
            prefs().edit().putBoolean("crashed_${name}", true).commit()
            CrashLog.log("Previous run died while using ${name}")
            if (crashed != Backend.CPU) {
                if (Settings.backendChoice(app) == crashed) Settings.setBackendChoice(app, null)
                notice = tr("בהפעלה הקודמת האפליקציה נסגרה בזמן עבודה על {0}. ", crashed.label) +
                    tr("מעכשיו המצב האוטומטי ידלג עליו. אפשר לאפס בהגדרות, ודוח הקריסה זמין להעתקה.")
            }
        }
        scope.launch { reloadProjects() }
        scope.launch(Dispatchers.IO) { Health.runStartupChecks(app) }
    }

    fun crashedBefore(b: Backend) = prefs().getBoolean("crashed_${b.name}", false)
    fun clearCrashed(b: Backend) = prefs().edit().remove("crashed_${b.name}").apply()

    fun navigate(s: Screen) {
        if (screen != s) { backStack.add(s); CrashLog.log("Screen: ${s}") }
    }

    fun navigateRoot(s: Screen) {
        CrashLog.log("Screen: ${s}")
        backStack.clear()
        backStack.add(Screen.Home)
        if (s != Screen.Home) backStack.add(s)
    }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun setImage(bmp: Bitmap, info: SourceInfo = SourceInfo()) {
        input = bmp
        framed = null
        preview = null
        sourceInfo = info
        tapPoints = emptyList()
        refreshPreview(Settings.removeBackground(app))
    }

    /** Tap-to-select points (in the preview's work-image pixels). */
    var tapPoints by mutableStateOf<List<Pair<Float, Float>>>(emptyList()); private set
    private var sam: Pair<Bitmap, Enhance.SamImage>? = null
    private var upscaled: Pair<Bitmap, Bitmap>? = null

    fun tap(x: Float, y: Float) {
        tapPoints = tapPoints + (x to y)
        refreshPreview(Settings.removeBackground(app))
    }

    fun clearTaps() {
        tapPoints = emptyList()
        refreshPreview(Settings.removeBackground(app))
    }

    @Synchronized
    private fun engine(): TripoEngine = engine ?: TripoEngine(app).also { engine = it }

    /** The built-in background-removal model (u2netp, shipped in the APK). */
    fun segmenter() = engine().segmenter()

    /**
     * Runs background removal and framing for the current image in the background, so the user
     * sees what the model will get (and any warnings) before starting. [start] reuses the result.
     */
    fun refreshPreview(removeBackground: Boolean) {
        val img = input ?: return
        val current = preview
        val points = tapPoints
        if (current != null && current.source === img && current.removeBackground == removeBackground && current.points == points) return
        previewJob?.cancel()
        previewBusy = true
        previewJob = scope.launch {
            val p = runCatching { makePreview(img, removeBackground, points) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) CrashLog.error("preview", it) }
                .getOrNull()
            withContext(Dispatchers.Main) {
                if (input === img && p != null) preview = p
                if (input === img) previewBusy = false
            }
        }
    }

    private fun makePreview(img: Bitmap, removeBackground: Boolean, points: List<Pair<Float, Float>>): Preview {
        val t0 = System.nanoTime()
        // Optional extras, each only if installed and switched on in Settings.
        val work = when {
            !Enhance.shouldUpscale(app, img) -> img
            upscaled?.first === img -> upscaled!!.second
            else -> Enhance.upscale(app, img).also { upscaled = img to it; CrashLog.log("Upscaled ${img.width}x${img.height} -> ${it.width}x${it.height}") }
        }
        var selection: Bitmap? = null
        val cut = when {
            points.isNotEmpty() && Extras.ready(app, Extras.TAP) -> {
                val emb = sam?.takeIf { it.first === work }?.second ?: Enhance.samEncode(app, work).also { sam = work to it }
                val mask = Enhance.samMask(app, emb, points)
                selection = dimOutside(work, mask)
                Enhance.applyMask(work, mask)
            }
            removeBackground -> Enhance.removeBackground(app, work) { engine().segmenter() }
            else -> work.copy(Bitmap.Config.ARGB_8888, false)
        }
        val framed = Preprocess.frameForeground(cut)
        val check = Preprocess.check(work, cut, removeBackground || points.isNotEmpty(), sourceInfo)
        CrashLog.log("Preview in %.2fs: removeBg=${removeBackground} taps=${points.size} extras=${Extras.describe(app)} issues=${check.issues} ".format((System.nanoTime() - t0) / 1e9) +
            check.stats.entries.joinToString { "%s=%.3f".format(it.key, it.value) })
        return Preview(img, removeBackground, framed, check.issues, work, points, selection, cut)
    }

    private fun dimOutside(src: Bitmap, mask: IntArray): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        for (i in px.indices) if (mask[i] < 128) {
            val c = px[i]
            px[i] = android.graphics.Color.rgb(android.graphics.Color.red(c) / 4, android.graphics.Color.green(c) / 4, android.graphics.Color.blue(c) / 4 + 20)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun start(choice: Backend?, quality: Quality, removeBackground: Boolean, smooth: Boolean) {
        val image = input ?: return
        if (runningJob?.isActive == true) return
        cancelRequested = false
        val generator = Settings.generator(app)
        job = JobState(backend = if (generator == Generator.TRIPOSG) Backend.NPU else choice ?: Capabilities.best(app),
            status = tr("מתחיל..."), plan = generator.stages)
        navigate(Screen.Processing)
        ProcessingService.start(app)
        CrashLog.log("Job start: generator=${generator} backend=${choice ?: "AUTO"} quality=${quality} removeBg=${removeBackground} smooth=${smooth} " +
            "image=${image.width}x${image.height}")

        runningJob = scope.launch {
            try {
                fun stage(s: Stage, p: Float, status: String = s.label) {
                    job = job?.copy(stage = s, stageProgress = p, status = status)
                }
                if (generator == Generator.TRIPOSG) {
                    runTripoSG(image, removeBackground, smooth) { s, p -> stage(s, p) }
                    return@launch
                }
                stage(Stage.LOAD, 0f)
                if (!ModelStore.isReady(app)) throw IllegalStateException(tr("צריך להוריד את המודל קודם (מסך הבית)"))
                if (choice == null && !Capabilities.probed(app)) {
                    Capabilities.probe(app) { stage(Stage.LOAD, 0.2f, it) }
                }
                val chain = Capabilities.chain(app, choice)
                CrashLog.log("Backend chain: ${chain} (${Capabilities.describe(app)})")
                val eng = engine()
                eng.load(chain, onStatus = { stage(Stage.LOAD, 0.5f, it) }, onAttempt = { b ->
                    // Written synchronously: if the process dies inside native code we find it on next launch.
                    prefs().edit().putString("active_backend", b.name).commit()
                    job = job?.copy(backend = b)
                })

                stage(Stage.SEGMENT, 0f)
                val segStart = System.nanoTime()
                val ready = preview?.takeIf { it.source === image && (it.points.isNotEmpty() || it.removeBackground == removeBackground) }
                val frame = ready?.framed ?: makePreview(image, removeBackground, tapPoints).framed
                framed = frame
                val segSeconds = (System.nanoTime() - segStart) / 1e9
                CrashLog.log("Frame ${if (ready != null) "from preview" else "computed"} in %.2fs, warnings=${ready?.issues}".format(segSeconds))

                val t0 = System.nanoTime()
                // Texture baking costs one decoder pass per texel: 4M texels on NPU/GPU, 1M on the CPU.
                val textureSize = when {
                    !Settings.bakeTexture(app) -> 0
                    eng.encoderBackend == Backend.CPU -> 1024
                    else -> 2048
                }
                val smoothIterations = Settings.smoothStrength(app)
                val density = Settings.densityThreshold(app)
                CrashLog.log("Advanced: smooth=${smoothIterations} density=${density}")
                val out = eng.reconstruct(Preprocess.toChw(frame), quality.resolution, smooth, textureSize,
                    smoothIterations, density,
                    progress = { s, p -> stage(s, p) },
                    cancelled = { cancelRequested })
                val seconds = (System.nanoTime() - t0) / 1e9

                val timings = LinkedHashMap<String, Double>()
                timings[Stage.SEGMENT.name] = segSeconds
                for ((s, t) in out.timings) timings[s.name] = t
                finish(out.mesh, frame, eng.encoderBackend.short, quality.label, timings, seconds, image, Generator.TRIPOSR.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                CrashLog.log("Job cancelled")
                job = null
                withContext(Dispatchers.Main) { if (screen == Screen.Processing) back() }
            } catch (e: Throwable) {
                // Includes OutOfMemoryError: report it instead of crashing the app.
                CrashLog.error("reconstruction", e)
                job = job?.copy(error = (e.message ?: e.javaClass.simpleName).take(300))
                if (e is OutOfMemoryError) { engine?.close(); engine = null }
            } finally {
                prefs().edit().remove("active_backend").commit()
                ProcessingService.finished(app)
            }
        }
    }

    private suspend fun finish(m: Mesh, thumb: Bitmap, backend: String, quality: String, timings: Map<String, Double>, seconds: Double,
                               source: Bitmap, kind: String) {
        val p = saveProject(m, thumb, backend, quality, (System.currentTimeMillis() - job!!.startedAt) / 1000.0, timings,
            source = source, kind = kind)
        CrashLog.log(("Job done in %.1fs (pipeline %.1fs): geometry %.2fs, texture %s, ${m.triangleCount} triangles, " +
            "texture ${m.texture?.size ?: 0}px").format(p.seconds, seconds, p.geometrySeconds,
            p.textureSeconds?.let { "%.2fs".format(it) } ?: "off"))
        withContext(Dispatchers.Main) {
            originalMesh = m
            mesh = m
            project = p
            input = null // the next "create" starts from a fresh image
            preview = null
            job = job?.copy(done = true, stageProgress = 1f, status = tr("המודל מוכן!"))
            if (screen == Screen.Processing) showResult()
        }
    }

    /** TripoSG on the NPU: background removal as usual, then its own framing and pipeline. */
    private suspend fun runTripoSG(image: Bitmap, removeBackground: Boolean, smooth: Boolean, stage: (Stage, Float) -> Unit) {
        stage(Stage.SEGMENT, 0f)
        val segStart = System.nanoTime()
        val ready = preview?.takeIf { it.source === image && it.cut != null && (it.points.isNotEmpty() || it.removeBackground == removeBackground) }
        val pv = ready ?: makePreview(image, removeBackground, tapPoints)
        framed = pv.framed
        val segSeconds = (System.nanoTime() - segStart) / 1e9
        val steps = Settings.sgSteps(app)
        val t0 = System.nanoTime()
        val out = SgEngine(app).use { sg ->
            sg.generate(pv.cut!!, steps, seed = 0L, smoothIterations = if (smooth) Settings.smoothStrength(app) else 0,
                guidanceInterval = Settings.sgGuidanceInterval(app), progress = stage, cancelled = { cancelRequested })
        }
        val timings = LinkedHashMap<String, Double>()
        timings[Stage.SEGMENT.name] = segSeconds
        for ((s, t) in out.timings) timings[s.name] = t
        finish(out.mesh, pv.framed, "NPU", tr("TripoSG · {0} צעדים", steps) + if (Settings.sgGuidanceInterval(app)) tr(" · הכוונה חכמה") else "", timings, (System.nanoTime() - t0) / 1e9,
            image, Generator.TRIPOSG.name)
    }

    /** "Create again": the project's picture goes back to the create screen, with today's settings. */
    fun recreate(p: Project) {
        if (isRunning) { navigate(Screen.Processing); return }
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { p.source(app) }
            withContext(Dispatchers.Main) {
                if (bmp == null) { notice = tr("התמונה המקורית של המודל הזה לא נשמרה"); return@withContext }
                if (job?.done == true) job = null
                setImage(bmp)
                navigate(Screen.Create)
            }
        }
    }

    /** The "+" / "create" entry point: shows the running job, or a fresh create screen. */
    fun startNew() {
        if (isRunning) navigate(Screen.Processing)
        else {
            if (job?.done == true) job = null
            navigate(Screen.Create)
        }
    }

    /** Opens the finished job's model (from the processing screen, the home card or the notification). */
    fun showResult() {
        if (mesh == null) return
        // Back from a fresh model goes to the gallery, not to the create screen it came from.
        navigateRoot(Screen.Projects)
        navigate(Screen.Result)
        if (job?.done == true) job = null
    }

    /** Closes the model sessions, e.g. before the model files are deleted. */
    fun resetEngine() {
        if (isRunning) return
        engine?.close()
        engine = null
    }

    fun cancel() {
        cancelRequested = true
    }

    val isRunning get() = runningJob?.isActive == true

    private fun projectsDir() = File(app.filesDir, "projects").apply { mkdirs() }

    fun saveProject(mesh: Mesh, thumb: Bitmap, backend: String, quality: String, seconds: Double,
                    timings: Map<String, Double>, name: String = tr("מודל {0}", projects.size + 1),
                    source: Bitmap? = null, kind: String = "", params: String = ""): Project {
        val id = System.currentTimeMillis().toString()
        val p = Project(id, name, System.currentTimeMillis(), backend, quality,
            seconds, mesh.vertexCount, mesh.triangleCount, timings, kind, params)
        val dir = p.dir(app).apply { mkdirs() }
        source?.let { src ->
            val s = minOf(1f, 1536f / maxOf(src.width, src.height))
            val b = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
            File(dir, "source.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        MeshTools.save(mesh, File(dir, "mesh.bin"))
        File(dir, "thumb.png").outputStream().use {
            Bitmap.createScaledBitmap(thumb, 256, 256, true).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        writeMeta(p)
        scope.launch(Dispatchers.Main) { projects.add(0, p); prune(p) }
        CreateWidget.refresh(app)
        return p
    }

    private fun writeMeta(p: Project) {
        File(p.dir(app), "meta.json").writeText(JSONObject().apply {
            put("name", p.name); put("created", p.created); put("backend", p.backend)
            put("quality", p.quality); put("seconds", p.seconds)
            put("vertices", p.vertices); put("triangles", p.triangles)
            put("timings", JSONObject(p.timings)); put("kind", p.kind); put("params", p.params); put("favorite", p.favorite)
        }.toString())
    }

    /** Reads saved projects from disk (blocking; snapshot state lists are safe to update from any thread). */
    fun reloadProjects() {
        val list = projectsDir().listFiles()?.mapNotNull { dir ->
            runCatching {
                val j = JSONObject(File(dir, "meta.json").readText())
                val t = j.optJSONObject("timings")
                val timings = t?.keys()?.asSequence()?.associateWith { t.getDouble(it) } ?: emptyMap()
                Project(dir.name, j.getString("name"), j.getLong("created"), j.optString("backend"),
                    j.optString("quality"), j.optDouble("seconds"), j.optInt("vertices"), j.optInt("triangles"), timings,
                    j.optString("kind"), j.optString("params"), j.optBoolean("favorite"))
            }.getOrNull()
        }?.sortedByDescending { it.created } ?: emptyList()
        synchronized(projects) { projects.clear(); projects.addAll(list) }
    }

    /** Shows a freshly made model (from [start] or [Creations]) with back leading to the gallery. */
    fun showNew(m: Mesh, p: Project) {
        originalMesh = m; mesh = m; project = p
        navigateRoot(Screen.Projects)
        navigate(Screen.Result)
    }

    fun open(p: Project) {
        scope.launch {
            try {
                val m = MeshTools.load(File(p.dir(app), "mesh.bin"))
                withContext(Dispatchers.Main) {
                    originalMesh = m; mesh = m; project = p
                    navigate(Screen.Result)
                }
            } catch (e: Throwable) {
                CrashLog.error("open project ${p.id}", e)
                notice = tr("לא הצלחתי לפתוח את הפרויקט: {0}", e.message)
            }
        }
    }

    fun delete(p: Project) {
        p.dir(app).deleteRecursively()
        projects.remove(p)
        if (project?.id == p.id) { project = null; mesh = null }
        CreateWidget.refresh(app)
    }

    /**
     * Clears the oldest projects beyond the number kept (Settings). Liked projects, the one just
     * made and the one open never go.
     */
    private fun prune(fresh: Project) {
        val keep = Settings.keepProjects(app)
        if (keep <= 0) return
        val old = projects.filter { !it.favorite && it.id != fresh.id && it.id != project?.id }.drop(keep - 1)
        if (old.isEmpty()) return
        CrashLog.log("Cleared ${old.size} old project(s): the newest ${keep} are kept")
        old.forEach { delete(it) }
    }

    fun toggleFavorite(p: Project) {
        val n = p.copy(favorite = !p.favorite)
        writeMeta(n)
        val i = projects.indexOfFirst { it.id == p.id }
        if (i >= 0) projects[i] = n
        if (project?.id == p.id) project = n
    }

    fun rename(p: Project, name: String) {
        val n = p.copy(name = name)
        writeMeta(n)
        val i = projects.indexOfFirst { it.id == p.id }
        if (i >= 0) projects[i] = n
        if (project?.id == p.id) project = n
    }

    /** Saves an edited mesh over the project's stored mesh. */
    fun saveEdits(m: Mesh) {
        val p = project ?: return
        MeshTools.save(m, File(p.dir(app), "mesh.bin"))
        val n = p.copy(vertices = m.vertexCount, triangles = m.triangleCount)
        writeMeta(n)
        val i = projects.indexOfFirst { it.id == p.id }
        if (i >= 0) projects[i] = n
        project = n
        originalMesh = m
    }
}
