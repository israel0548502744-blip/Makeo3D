package com.triposr.mobile

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Public repository with only what the app downloads (models, updates, config); the code is private. */
private const val REPO = "israel0548502744-blip/makeo3d-public"

private suspend fun githubJson(path: String): JSONObject = withContext(Dispatchers.IO) {
    val conn = URL("https://api.github.com/repos/${REPO}/${path}").openConnection() as HttpURLConnection
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    try {
        if (conn.responseCode != 200) throw IllegalStateException("GitHub API ${conn.responseCode} for ${path}")
        JSONObject(conn.inputStream.bufferedReader().readText())
    } finally {
        conn.disconnect()
    }
}

data class ReleaseAsset(val name: String, val size: Long, val url: String)

private fun JSONObject.assets(): List<ReleaseAsset> {
    val arr = getJSONArray("assets")
    return (0 until arr.length()).map { i ->
        val a = arr.getJSONObject(i)
        ReleaseAsset(a.getString("name"), a.getLong("size"), a.getString("browser_download_url"))
    }
}

/** User preferences that persist between runs. */
object Settings {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Processor chosen by the user; null means automatic (best backend that passed the device test). */
    fun backendChoice(ctx: Context): Backend? = prefs(ctx).getString("backend_choice", null)
        ?.let { runCatching { Backend.valueOf(it) }.getOrNull() }
    fun setBackendChoice(ctx: Context, b: Backend?) = prefs(ctx).edit().putString("backend_choice", b?.name).apply()
    fun quality(ctx: Context) = Quality.entries[prefs(ctx).getInt("quality", Quality.MEDIUM.ordinal)]
    fun setQuality(ctx: Context, q: Quality) = prefs(ctx).edit().putInt("quality", q.ordinal).apply()
    fun removeBackground(ctx: Context) = prefs(ctx).getBoolean("remove_bg", true)
    fun setRemoveBackground(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("remove_bg", v).apply()
    fun smooth(ctx: Context) = prefs(ctx).getBoolean("smooth", true)
    fun setSmooth(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("smooth", v).apply()

    /**
     * Advanced: smoothing passes (Taubin, keeps volume). 3 is the default; 6-10 removes the
     * contour "rings" that would print as ridges, at the cost of fine detail.
     */
    fun smoothStrength(ctx: Context) = prefs(ctx).getInt("smooth_strength", DEFAULT_SMOOTH)
    fun setSmoothStrength(ctx: Context, v: Int) = prefs(ctx).edit().putInt("smooth_strength", v).apply()

    /**
     * Advanced: density at which the surface is placed (TripoSR default 25). Lower makes the
     * object fuller and thin parts thicker (and drops fewer loose bits); higher makes it slimmer.
     */
    fun densityThreshold(ctx: Context) = prefs(ctx).getFloat("density_threshold", TripoEngine.DENSITY_THRESHOLD)
    fun setDensityThreshold(ctx: Context, v: Float) = prefs(ctx).edit().putFloat("density_threshold", v).apply()

    fun resetAdvanced(ctx: Context) = prefs(ctx).edit().remove("smooth_strength").remove("density_threshold").remove("sg_steps").remove("sg_guidance_interval").apply()

    /** Which model generates the shape; falls back to TripoSR when the chosen pack is missing. */
    fun generator(ctx: Context): Generator = prefs(ctx).getString("generator", null)
        ?.let { runCatching { Generator.valueOf(it) }.getOrNull() }?.takeIf { it.ready(ctx) } ?: Generator.TRIPOSR
    fun setGenerator(ctx: Context, g: Generator) = prefs(ctx).edit().putString("generator", g.name).apply()

    /** Advanced, TripoSG only: denoising steps. 10 is the normal mode (about 1.5 min on the int8
     *  pack, good shapes on the device); 25 is the high-quality mode, close to the original 50. */
    fun sgSteps(ctx: Context) = prefs(ctx).getInt("sg_steps", DEFAULT_SG_STEPS)
    fun setSgSteps(ctx: Context, v: Int) = prefs(ctx).edit().putInt("sg_steps", v).apply()
    const val DEFAULT_SG_STEPS = 10

    /**
     * Advanced, TripoSG only: guidance (the extra "without the image" pass) only on the middle
     * steps. Faster; off until the quality checks (sg_eval.py) and the user's own comparison agree.
     */
    fun sgGuidanceInterval(ctx: Context) = prefs(ctx).getBoolean("sg_guidance_interval", false)
    fun setSgGuidanceInterval(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("sg_guidance_interval", v).apply()

    const val DEFAULT_SMOOTH = 3

    /** Bake a texture (sharp colors) instead of only per-vertex colors. On by default. */
    /** Version of the terms the user accepted (0 = never). Bump TERMS_VERSION when they change. */
    const val TERMS_VERSION = 3
    fun termsAccepted(ctx: Context) = prefs(ctx).getInt("terms_version", 0) >= TERMS_VERSION
    fun acceptTerms(ctx: Context) = prefs(ctx).edit().putInt("terms_version", TERMS_VERSION).apply()

    fun bakeTexture(ctx: Context) = prefs(ctx).getBoolean("bake_texture", true)
    fun setBakeTexture(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("bake_texture", v).apply()

    fun describe(ctx: Context) =
        "backend=${backendChoice(ctx) ?: "AUTO"}, quality=${quality(ctx)}, removeBg=${removeBackground(ctx)}, " +
            "smooth=${smooth(ctx)}x${smoothStrength(ctx)}, density=${densityThreshold(ctx)}, texture=${bakeTexture(ctx)}, " +
            "generator=${generator(ctx)}, sgSteps=${sgSteps(ctx)}, sgGuidanceInterval=${sgGuidanceInterval(ctx)}"
}

enum class Quality(val label: String, val resolution: Int, val hint: String) {
    LOW(tr("נמוכה"), 128, tr("מהיר, כמה שניות")),
    MEDIUM(tr("בינונית"), 192, tr("מאוזן")),
    HIGH(tr("גבוהה"), 256, tr("מפורט, איטי יותר")),
    /** Twice the samples of HIGH. Surfaces get a little smoother; detail is capped by the model itself. */
    MAX(tr("מקסימלית"), 320, tr("משטח חלק יותר, פי 2 זמן. מומלץ עם NPU/GPU")),
}

/** The image-to-3D models the user can choose from; each needs its pack downloaded. */
enum class Generator(val label: String, val hint: String, val stages: List<Stage>) {
    TRIPOSR("TripoSR", tr("מהיר, עם צבעים וטקסטורה"),
        listOf(Stage.LOAD, Stage.SEGMENT, Stage.ENCODE, Stage.FIELD, Stage.SURFACE, Stage.COLOR, Stage.TEXTURE)),
    TRIPOSG("TripoSG", tr("צורה מדויקת יותר, בלי צבע. ניסיוני, NPU בלבד"),
        listOf(Stage.SEGMENT, Stage.ENCODE, Stage.DENOISE, Stage.FIELD, Stage.SURFACE, Stage.COLOR));

    val pack: Pack get() = when (this) {
        TRIPOSR -> ModelStore.MAIN
        TRIPOSG -> ModelStore.TRIPOSG
    }

    fun ready(ctx: Context) = ModelStore.isReady(ctx, pack)

    companion object {
        fun available(ctx: Context) = entries.filter { it.ready(ctx) }
    }
}

/** A set of files downloaded together from the models release. */
class Pack(val key: String, val title: String, val tag: String = ModelStore.RELEASE_TAG, val reuse: Pack? = null,
           val files: () -> List<String>)

/**
 * The TripoSR ONNX models (~1.7 GB) are not inside the APK. They are published once as a
 * GitHub release and downloaded from inside the app with Android's DownloadManager, which keeps
 * going in the background, resumes after network drops and shows a notification.
 *
 * Two packs: MAIN (the ONNX models, needed by everyone) and NPU (models pre-compiled in CI for
 * this phone's Snapdragon chip, optional; see convert/compile_npu.py).
 */
object ModelStore {
    const val RELEASE_TAG = "models-v1"
    val FILES = listOf(TripoEngine.ENCODER, TripoEngine.DECODER)
    val MAIN = Pack("models", tr("TripoSR — מודל תלת־ממד")) { FILES }
    val NPU = Pack("npu", tr("TripoSR — חבילת NPU")) { NpuPack.files() }
    /** Smaller variants of the first TripoSG part, only to find what fits in the NPU's memory. */
    val SG_PROBE = Pack("sg_probe", tr("TripoSG — קבצי בדיקה"), "triposg-probe") {
        NpuPack.chip()?.let { c -> SgEngine.PROBES.flatMap { listOf("sg_probe_${it}_${c}_ctx.onnx", "sg_probe_${it}_${c}_ctx_qnn.bin") } } ?: emptyList()
    }
    private val TRIPOSG_V3 = Pack("triposg_v3", "TripoSG", "triposg-v3") { NpuPack.chip()?.let { SgEngine.files(it) } ?: emptyList() }
    /**
     * TripoSG compiled for this chip's NPU (experimental, ~2.7 GB): the DiT in int8 weights /
     * 16-bit activations, a third of the float size; shapes 99.4-99.7% the same (sg_eval.py int8).
     * DINOv2 and the VAE are the same files as in v3, so a finished v3 download keeps them.
     */
    val TRIPOSG = Pack("triposg_v4", tr("TripoSG — מודל צורה מדויק"), "triposg-v4-int8", reuse = TRIPOSG_V3) {
        NpuPack.chip()?.let { SgEngine.files(it) } ?: emptyList()
    }

    sealed interface Status {
        data object Missing : Status
        data class Downloading(val done: Long, val total: Long) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private fun prefs(ctx: Context, pack: Pack) = ctx.getSharedPreferences(pack.key, Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = ctx.getExternalFilesDir("models")!!
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /**
     * Ready only after DownloadManager reported success. File size alone is not enough:
     * DownloadManager pre-allocates the full file size before the download finishes.
     */
    fun isReady(ctx: Context, pack: Pack = MAIN) =
        pack.files().isNotEmpty() && prefs(ctx, pack).getBoolean("complete", false) && sizesMatch(ctx, pack)

    private fun sizesMatch(ctx: Context, pack: Pack) = pack.files().all { name ->
        val f = file(ctx, name)
        f.exists() && f.length() > 0 && f.length() == prefs(ctx, pack).getLong("size_${name}", -1)
    }

    /** Total size of the pack in the release, or null if the release doesn't have all its files. */
    suspend fun availableSize(pack: Pack): Long? {
        val assets = runCatching { githubJson("releases/tags/${pack.tag}").assets().associateBy { it.name } }
            .onFailure { Health.report(tr("הורדת מודלים"), tr("{0}: לא הצלחתי לקרוא את רשימת הקבצים: {1}", pack.title, it), Health.Level.WARNING) }
            .getOrThrow()
        val files = pack.files()
        val missing = files.filter { it !in assets }
        if (files.isEmpty() || missing.isNotEmpty()) {
            CrashLog.log("${pack.key}: not available (${if (files.isEmpty()) "no chip" else "missing ${missing.joinToString()}"})")
            return null
        }
        return files.sumOf { assets.getValue(it).size }
    }

    suspend fun startDownload(ctx: Context, pack: Pack = MAIN) {
        CrashLog.log("Download start: ${pack.key} (${pack.tag})")
        val assets = githubJson("releases/tags/${pack.tag}").assets().associateBy { it.name }
        val dm = ctx.getSystemService(DownloadManager::class.java)
        cancel(ctx, pack)
        val edit = prefs(ctx, pack).edit()
        var kept = 0
        for (name in pack.files()) {
            val asset = assets[name] ?: throw IllegalStateException("${name} missing from release ${pack.tag}")
            if (reusable(ctx, pack, name, asset.size)) {
                edit.putLong("size_${name}", asset.size).remove("id_${name}")
                kept++
                continue
            }
            file(ctx, name).delete()
            val req = DownloadManager.Request(Uri.parse(asset.url))
                .setTitle(pack.title)
                .setDescription(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, "models", name)
            edit.putLong("size_${name}", asset.size).putLong("id_${name}", dm.enqueue(req))
        }
        edit.putBoolean("complete", kept == pack.files().size).apply()
        CrashLog.log("Download ${pack.key} started: ${pack.files().sumOf { assets[it]?.size ?: 0 } shr 20} MB, ${kept} files kept from ${pack.reuse?.key}")
    }

    /** [name] is already on disk from a finished download of the pack this one replaces, with the same size. */
    private fun reusable(ctx: Context, pack: Pack, name: String, size: Long?): Boolean {
        val old = pack.reuse?.takeIf { prefs(ctx, it).getBoolean("complete", false) } ?: return false
        return size != null && size == prefs(ctx, old).getLong("size_${name}", -1) && file(ctx, name).length() == size
    }

    fun status(ctx: Context, pack: Pack = MAIN): Status {
        if (isReady(ctx, pack)) return Status.Ready
        val files = pack.files()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val ids = files.map { prefs(ctx, pack).getLong("id_${it}", -1) }.filter { it >= 0 }
        if (ids.isEmpty()) return Status.Missing
        var done = 0L
        var total = files.sumOf { prefs(ctx, pack).getLong("size_${it}", 0) }
        var running = false
        var succeeded = 0
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray())).use { c ->
            if (c.count == 0) return Status.Missing
            if (c.count < ids.size) return Status.Failed(tr("ההורדה הופסקה, נסה שוב"))
            while (c.moveToNext()) {
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                done += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                when (st) {
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        return Status.Failed(tr("ההורדה נכשלה (קוד {0})", reason))
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> succeeded++
                    else -> running = true
                }
            }
        }
        if (total <= 0) total = 1
        if (running || succeeded < ids.size) return Status.Downloading(done, total)
        if (!sizesMatch(ctx, pack)) return Status.Failed(tr("הקובץ שהורד לא תקין, נסה שוב"))
        prefs(ctx, pack).edit().putBoolean("complete", true).apply()
        CrashLog.log("Download ${pack.key} complete")
        return Status.Ready
    }

    /** Direct links of the pack's files, for downloading them with another app. */
    fun links(pack: Pack) = pack.files().map { "https://github.com/${REPO}/releases/download/${pack.tag}/${it}" }

    /**
     * Installs a pack from files the user downloaded themselves (e.g. with a download manager
     * app) into a folder they pick. Each file is copied and checked against the release size.
     * Returns the names that are missing or wrong; empty means the pack is ready.
     */
    suspend fun importFrom(ctx: Context, pack: Pack, tree: Uri, progress: (String) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val expected = runCatching { githubJson("releases/tags/${pack.tag}").assets().associate { it.name to it.size } }.getOrNull()
        val cr = ctx.contentResolver
        val treeDoc = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDoc)
        val found = HashMap<String, Pair<Uri, Long>>()
        cr.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                found[c.getString(1)] = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getLong(2)
            }
        }
        val keep = pack.files().filter { it !in found && reusable(ctx, pack, it, expected?.get(it)) }.toSet()
        val bad = pack.files().filter { name ->
            if (name in keep) return@filter false
            val f = found[name] ?: return@filter true
            val want = expected?.get(name)
            want != null && f.second != want
        }
        CrashLog.log("Import ${pack.key}: ${found.size} files in folder, ${bad.size} missing or wrong: ${bad.joinToString()}")
        if (bad.isNotEmpty()) return@withContext bad
        cancel(ctx, pack)
        val edit = prefs(ctx, pack).edit().putBoolean("complete", false)
        edit.apply()
        val files = pack.files() - keep
        keep.forEach { edit.putLong("size_${it}", expected!!.getValue(it)) }
        files.forEachIndexed { i, name ->
            progress(tr("מעתיק {0}/{1}: {2}", i + 1, files.size, name))
            val (uri, size) = found.getValue(name)
            cr.openInputStream(uri)!!.use { input -> file(ctx, name).outputStream().use { input.copyTo(it, 1 shl 20) } }
            if (file(ctx, name).length() != size) {
                file(ctx, name).delete()
                return@withContext listOf(name)
            }
            edit.putLong("size_${name}", size)
        }
        edit.putBoolean("complete", true).apply()
        CrashLog.log("Import ${pack.key} complete")
        emptyList()
    }

    fun cancel(ctx: Context, pack: Pack = MAIN) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val ready = isReady(ctx, pack)
        val edit = prefs(ctx, pack).edit()
        for (name in pack.files()) {
            val id = prefs(ctx, pack).getLong("id_${name}", -1)
            // remove() also deletes the file, so only for unfinished downloads.
            if (id >= 0 && !ready) dm.remove(id)
            edit.remove("id_${name}")
        }
        edit.apply()
    }

    fun delete(ctx: Context, pack: Pack = MAIN) {
        cancel(ctx, pack)
        pack.files().forEach { file(ctx, it).delete() }
        if (pack === MAIN) ctx.filesDir.listFiles { f -> f.name.endsWith("_qnn_ctx.onnx") }?.forEach { it.delete() }
        prefs(ctx, pack).edit().clear().apply()
    }

    fun sizeOnDisk(ctx: Context, pack: Pack = MAIN) = pack.files().sumOf { file(ctx, it).length() }

    fun describe(ctx: Context) = buildString {
        for (pack in listOf(MAIN, NPU, TRIPOSG) + Extras.all.map { it.pack }) {
            append("${pack.key}: ${if (isReady(ctx, pack)) "ready" else "not ready"}")
            pack.files().forEach { append(", ${it}=${file(ctx, it).length() shr 20}MB/${prefs(ctx, pack).getLong("size_${it}", -1) shr 20}MB") }
            append("; ")
        }
    }
}

/**
 * Optional enhancement models (convert/export_extras.py, release "extras-v1"). Nothing here is
 * required: each is downloaded only if the user turns it on in Settings.
 */
object Extras {
    private const val TAG = "extras-v1"

    class Extra(val pack: Pack, val title: String, val detail: String, val license: String, val usesToggle: Boolean)

    val UPSCALE = Extra(Pack("x_upscale", tr("שיפור תמונה (Real-ESRGAN)"), TAG) { listOf("realesrgan_x4v3.onnx") },
        tr("שיפור תמונה"), tr("מחדד ומגדיל פי 4 תמונות קטנות או מטושטשות לפני היצירה"), "BSD-3-Clause · Xintao Wang", true)
    val SEGMENT = Extra(Pack("x_isnet", tr("הסרת רקע מדויקת (ISNet)"), TAG) { listOf("isnet_general.onnx") },
        tr("הסרת רקע מדויקת"), tr("שוליים נקיים וחלקים דקים, במקום המודל הקטן המובנה"), "Apache-2.0 · Xuebin Qin (DIS)", true)
    val TAP = Extra(Pack("x_sam", tr("בחירה בנגיעה (MobileSAM)"), TAG) { listOf("mobilesam_encoder.onnx", "mobilesam_decoder.onnx") },
        tr("בחירת אובייקט בנגיעה"), tr("נוגעים באובייקט בתמונה והוא נבחר, גם כשיש כמה אובייקטים"), "Apache-2.0 · Chaoning Zhang (MobileSAM)", false)
    val DEPTH = Extra(Pack("x_depth", tr("עומק (Depth Anything V2 Small)"), TAG) { listOf("depth_anything_v2s.onnx") },
        tr("מפת עומק לתבליט"), tr("מאפשר ליצור תבליט תלת־ממדי מתמונה"), "Apache-2.0 · Depth Anything V2 Small", false)
    val all = listOf(UPSCALE, SEGMENT, TAP, DEPTH)

    fun ready(ctx: Context, e: Extra) = ModelStore.isReady(ctx, e.pack)
    /** Installed and switched on (extras without a switch are on while installed). */
    fun active(ctx: Context, e: Extra) = ready(ctx, e) &&
        (!e.usesToggle || ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("use_${e.pack.key}", true))
    fun setActive(ctx: Context, e: Extra, on: Boolean) =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("use_${e.pack.key}", on).apply()
    fun file(ctx: Context, e: Extra, i: Int = 0) = ModelStore.file(ctx, e.pack.files()[i])
    fun describe(ctx: Context) = all.joinToString { "${it.pack.key}=${if (active(ctx, it)) "on" else if (ready(ctx, it)) "off" else "-"}" }
}

/** Models compiled ahead of time for this phone's NPU (convert/compile_npu.py, compile-npu.yml). */
object NpuPack {
    /** Chip id used in the file names, or null when there's no pre-compiled pack for this SoC. */
    fun chip(): String? = when {
        android.os.Build.SOC_MODEL.uppercase().startsWith("SM8750") -> "sm8750"
        else -> null
    }

    fun files(): List<String> = chip()?.let { c ->
        listOf("encoder", "decoder").flatMap { m -> listOf("triposr_${m}_${c}_ctx.onnx", "triposr_${m}_${c}_ctx_qnn.bin") }
    } ?: emptyList()

    fun encoder(ctx: Context): File? = chip()?.let { ModelStore.file(ctx, "triposr_encoder_${it}_ctx.onnx") }
    fun decoder(ctx: Context): File? = chip()?.let { ModelStore.file(ctx, "triposr_decoder_${it}_ctx.onnx") }
    fun isReady(ctx: Context) = ModelStore.isReady(ctx, ModelStore.NPU)
}

/**
 * Settings the app reads from the public repository on launch. min_build lets builds that are
 * no longer supported (e.g. the free test builds once the app is in the store) be switched off.
 * Store builds read their own key (min_build_store), so retiring the GitHub builds never hits them.
 */
object RemoteConfig {
    data class Retired(val message: String, val url: String)

    suspend fun retired(): Retired? = withContext(Dispatchers.IO) {
        val conn = URL("https://raw.githubusercontent.com/${REPO}/main/config.json").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val json = try {
            if (conn.responseCode != 200) throw IllegalStateException("config ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
        if (BuildConfig.VERSION_CODE >= json.optInt(if (BuildConfig.STORE) "min_build_store" else "min_build", 0)) null
        else Retired(json.optString("message").ifBlank { tr("הגרסה הזו כבר לא נתמכת. כדי להמשיך, יש להתקין את הגרסה העדכנית.") },
            json.optString("url"))
    }
}

/** Checks the repo's latest GitHub release for a newer APK and installs it. */
object Updater {
    data class Update(val build: Int, val sizeMb: Long, val url: String, val notes: String)

    suspend fun check(): Update? {
        if (BuildConfig.STORE) return null  // the store installs updates itself
        val rel = githubJson("releases/latest")
        val build = rel.getString("tag_name").removePrefix("apk-").toIntOrNull() ?: return null
        if (build <= BuildConfig.VERSION_CODE) return null
        val apk = rel.assets().firstOrNull { it.name.endsWith(".apk") } ?: return null
        return Update(build, apk.size shr 20, apk.url, rel.optString("body"))
    }

    private fun apkFile(ctx: Context, u: Update) = File(ctx.getExternalFilesDir("updates"), "TripoSR-${u.build}.apk")

    fun download(ctx: Context, u: Update): Long {
        ctx.getExternalFilesDir("updates")?.listFiles()?.forEach { it.delete() }
        val req = DownloadManager.Request(Uri.parse(u.url))
            .setTitle(tr("Makeo 3D — עדכון {0}", u.build))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, "updates", apkFile(ctx, u).name)
        return ctx.getSystemService(DownloadManager::class.java).enqueue(req)
    }

    /** Returns 0..1 while downloading, 1 when done, -1 on failure. */
    fun progress(ctx: Context, id: Long): Float {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return -1f
            return when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> 1f
                DownloadManager.STATUS_FAILED -> -1f
                else -> {
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total > 0) (done.toFloat() / total).coerceAtMost(0.99f) else 0f
                }
            }
        }
    }

    fun install(ctx: Context, u: Update) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apkFile(ctx, u))
        ctx.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Where the phone is, from the SIM / mobile network (falls back to the language setting).
 * TripoSG is not offered in the EU/EEA, the UK and South Korea until a lawyer confirms its
 * weights may be distributed there (docs/LEGAL.md).
 */
object Region {
    private val TRIPOSG_BLOCKED = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT",
        "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "GB", "KR",
    )

    fun country(ctx: Context): String {
        val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
        return listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, java.util.Locale.getDefault().country)
            .map { it.uppercase() }.firstOrNull { it.length == 2 } ?: ""
    }

    fun triposgAllowed(ctx: Context) = country(ctx) !in TRIPOSG_BLOCKED
}
