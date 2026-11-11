package com.triposr.mobile.ui

import com.triposr.mobile.TurntableVideo
import java.io.File
import com.triposr.mobile.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Brush
import com.triposr.mobile.Health
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.triposr.mobile.AppState
import androidx.compose.material3.CircularProgressIndicator
import com.triposr.mobile.Mesh
import com.triposr.mobile.PrintModel
import com.triposr.mobile.Printability
import com.triposr.mobile.Backend
import com.triposr.mobile.Capabilities
import com.triposr.mobile.Support
import com.triposr.mobile.BuildConfig
import com.triposr.mobile.CrashLog
import com.triposr.mobile.ExportFormat
import com.triposr.mobile.MeshTools
import com.triposr.mobile.Creations
import com.triposr.mobile.MeshView
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Project
import com.triposr.mobile.Quality
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

@Composable
fun ResultScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mesh = AppState.mesh
    val project = AppState.project
    if (mesh == null || project == null) {
        LaunchedEffect(Unit) { AppState.back() }
        return
    }
    var colors by remember { mutableStateOf(true) }
    var wire by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(true) }
    var rotate by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<MeshView?>(null) }
    var format by remember { mutableStateOf(ExportFormat.GLB) }
    var busy by remember { mutableStateOf(false) }
    var smoothIt by remember { mutableFloatStateOf(0f) }
    var reduce by remember { mutableFloatStateOf(0f) }
    var edited by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var exportTime by remember { mutableStateOf<String?>(null) }
    var print by remember { mutableStateOf<PrintModel?>(null) }
    var thinPreview by remember { mutableStateOf<Mesh?>(null) }
    var showThin by remember { mutableStateOf(false) }
    var baseCut by remember { mutableFloatStateOf(0f) }
    var tab by remember { mutableStateOf(0) }
    var video by remember { mutableStateOf<Float?>(null) }
    // Touch-up: rubbing parts away or flat, on a model that fills the screen.
    var clean by remember { mutableStateOf<com.triposr.mobile.MeshEditor?>(null) }
    var tool by remember { mutableStateOf(MeshView.Tool.ERASE) }
    var brush by remember { mutableFloatStateOf(60f) }
    var through by remember { mutableStateOf(false) }
    var ring by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var cleaned by remember { mutableStateOf<Mesh?>(null) }
    var big by remember { mutableStateOf(false) }
    fun leaveClean() { view?.stopEdit(); clean = null; ring = null }
    androidx.activity.compose.BackHandler(enabled = clean != null || big) { if (clean != null) leaveClean() else big = false }
    LaunchedEffect(mesh, baseCut) {
        print = null; thinPreview = null; showThin = false
        Printability.baseCutMm = baseCut
        runCatching {
            // One check at a time: a check doesn't stop when its screen is left, and several
            // dense meshes checked at once (results opened one after another) ran out of heap.
            printCheckLock.withLock { withContext(Dispatchers.Default) {
                ensureActive()
                val t0 = System.nanoTime()
                // Very dense meshes (TripoSG makes ~800k triangles) run the check out of Java heap.
                val m = if (mesh.triangleCount > PRINT_CHECK_MAX_TRIANGLES)
                    MeshTools.simplify(mesh, (1f - PRINT_CHECK_MAX_TRIANGLES.toFloat() / mesh.triangleCount).coerceAtMost(0.9f)) else mesh
                Printability.prepared(m, baseCut).also {
                    CrashLog.log("Print check in %.2fs: watertight=${it.report.watertight} minWall=%.2fmm".format(
                        (System.nanoTime() - t0) / 1e9, it.report.minWallMm))
                }
            } }
        }.onSuccess { print = it }.onFailure { CrashLog.error("print check", it) }
    }
    val formats = ExportFormat.available(mesh)
    if (format !in formats) format = ExportFormat.GLB

    /** Runs [write] off the main thread and records how long the export took. */
    suspend fun <T> timedExport(fmt: ExportFormat, write: () -> T): T {
        val t0 = System.nanoTime()
        val r = withContext(Dispatchers.IO) { write() }
        val s = (System.nanoTime() - t0) / 1e9
        CrashLog.log("Export ${fmt.name} in %.2fs (${mesh.triangleCount} triangles, texture ${mesh.texture?.size ?: 0}px)".format(s))
        exportTime = tr("ייצוא {0}: %.1f ש׳", fmt.label).format(s)
        return r
    }

    fun export(fmt: ExportFormat = format, action: (android.net.Uri) -> Unit) {
        busy = true
        scope.launch {
            try {
                val uri = timedExport(fmt) { Actions.exportToCache(ctx, mesh, fmt, project.name) }
                action(uri)
            } catch (e: Throwable) {
                CrashLog.error("export", e); Actions.toast(ctx, tr("הייצוא נכשל: {0}", e.message))
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val editor = clean
        if (editor != null) TopBar(tr("ניקוי הדגם"), onBack = { leaveClean() }) {
            CircleIcon(Icons.AutoMirrored.Rounded.Undo, tr("בטל פעולה")) { if (editor.undo()) view?.refreshEdit() }
            CircleIcon(Icons.Rounded.Check, tr("סיום"), tint = C.Green) {
                if (!editor.changed) leaveClean()
                else if (!busy) {
                    busy = true
                    scope.launch {
                        val m = withContext(Dispatchers.Default) { editor.result() }
                        busy = false
                        if (m.triangleCount == 0) Actions.toast(ctx, tr("לא נשאר כלום מהדגם. בטל פעולה ונסה שוב."))
                        else {
                            leaveClean()
                            AppState.mesh = m; cleaned = m; edited = true; tab = 1
                            Actions.toast(ctx, tr("הניקוי הוחל. לחץ \"שמור שינויים בפרויקט\" כדי לשמור אותו."))
                        }
                    }
                }
            }
        } else TopBar(project.name, onBack = { AppState.back() }) {
            CircleIcon(if (project.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tr("אהבתי"),
                tint = if (project.favorite) Color(0xFFFF5C8A) else C.Text) { AppState.toggleFavorite(project) }
            CircleIcon(Icons.Rounded.Edit, tr("שנה שם")) { renaming = true }
            CircleIcon(Icons.Rounded.Share, tr("שתף")) { export { Actions.share(ctx, it, format) } }
        }
            // The model gets the room the tab under it does not need; all of it when enlarged or cleaning.
            val full = clean != null || big
            val share by androidx.compose.animation.core.animateFloatAsState(if (tab == 0) 2f else 1.1f, label = "viewer")
            Box(Modifier.fillMaxWidth().padding(horizontal = if (clean != null) 8.dp else 20.dp).weight(if (full) 1f else share)
                .glass(RoundedCornerShape(28.dp))) {
                // Screenshot tests (inspection mode) have no OpenGL: show the mesh's thumbnail instead.
                if (androidx.compose.ui.platform.LocalInspectionMode.current) {
                    rememberThumb(project)?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null,
                        modifier = Modifier.fillMaxSize().padding(40.dp)) }
                } else AndroidView(
                    factory = { MeshView(it).also { v ->
                        view = v
                        v.onBrush = { x, y, down -> ring = if (down) androidx.compose.ui.geometry.Offset(x, y) else null }
                    } },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        v.setMesh(if (showThin) thinPreview ?: mesh else mesh)
                        v.setOptions(colors, wire, light, grid, rotate)
                        if (clean != null) { v.tool = tool; v.brushPx = brush; v.through = through }
                    },
                    onRelease = { it.onPause() },
                )
                print?.report?.sizeMm?.let { sz ->
                    Text(tr("{0} × {1} × {2} מ״מ", sz[0].roundToInt(), sz[1].roundToInt(), sz[2].roundToInt()), color = C.Sub, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(10.dp))
                            .background(C.Card.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                ring?.let { o ->
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White, brush, o, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                        drawCircle(Color(0x33FFFFFF), brush, o)
                    }
                }
                if (clean == null) Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(38.dp).clip(CircleShape)
                    .background(Color(0xAA120B2A)).border(1.dp, Color(0x33FFFFFF), CircleShape).pressable { big = !big },
                    contentAlignment = Alignment.Center) {
                    Icon(if (big) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, tr(if (big) "הקטן" else "הגדל"),
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        if (editor != null) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val tools = listOf(MeshView.Tool.ERASE to tr("מחק"), MeshView.Tool.SMOOTH to tr("החלק"), MeshView.Tool.ROTATE to tr("סובב"))
                Segmented(tools.map { it.first }, tool, { t -> tools.first { it.first == t }.second }, { tool = it })
                Text(when (tool) {
                    MeshView.Tool.ERASE -> tr("גע בחלק מיותר והוא נמחק. שתי אצבעות מזיזות ומקרבות את הדגם.")
                    MeshView.Tool.SMOOTH -> tr("שפשף על בליטות וקוצים והם נבלעים במשטח, בלי להשאיר חור.")
                    MeshView.Tool.ROTATE -> tr("גרור כדי לסובב את הדגם, ואז חזור למחיקה או להחלקה.")
                }, color = C.Sub, fontSize = 13.sp)
                if (tool != MeshView.Tool.ROTATE) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("גודל המברשת"), color = C.Text, fontSize = 13.sp)
                        Slider(brush, { b ->
                            brush = b
                            ring = view?.let { androidx.compose.ui.geometry.Offset(it.width / 2f, it.height / 2f) }
                        }, Modifier.weight(1f), valueRange = 15f..220f, onValueChangeFinished = { ring = null }, colors = sliderColors())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((deep, label) in listOf(false to tr("רק מה שרואים"), true to tr("גם מה שמאחור"))) {
                            val on = through == deep
                            Text(label, color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Violet else C.veil(0x14))
                                    .pressable { through = deep }.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
                SoftButton(tr("הסר חתיכות מנותקות"), Modifier.fillMaxWidth(), Icons.Rounded.AutoFixHigh) {
                    val n = editor.removeLoose()
                    view?.refreshEdit()
                    Actions.toast(ctx, if (n > 0) tr("הוסרו {0} חתיכות מנותקות", n) else tr("אין חתיכות מנותקות"))
                }
            }
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        val printOk = print?.report?.watertight
        val tabs = listOf(tr("תצוגה"), tr("עריכה"), tr("בדיקה") + when (printOk) { true -> " ✓"; false -> " !"; null -> "" }, tr("ייצוא"))
        Box(Modifier.padding(horizontal = 20.dp)) { Segmented(tabs.indices.toList(), tab, { tabs[it] }, { tab = it }) }
        if (!big) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ViewToggle(Icons.Rounded.Grid4x4, tr("רשת"), wire) { wire = !wire }
                        ViewToggle(Icons.Rounded.Palette, tr("צבע"), colors) { colors = !colors }
                        ViewToggle(Icons.Rounded.Lightbulb, tr("תאורה"), light) { light = !light }
                        ViewToggle(Icons.Rounded.GridOn, tr("רצפה"), grid) { grid = !grid }
                        ViewToggle(Icons.Rounded.Autorenew, tr("סיבוב"), rotate) { rotate = !rotate }
                        ViewToggle(Icons.Rounded.CenterFocusStrong, tr("איפוס"), false) { view?.resetView() }
                    }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(project.name, C.Text)
                Chip(tr("{0} פוליגונים", "%,d".format(mesh.triangleCount)))
                Chip(tr("{0} קודקודים", "%,d".format(mesh.vertexCount)))
                Chip(tr("%.1f שניות").format(project.seconds), C.Green)
                if (project.geometrySeconds > 0) Chip(tr("גאומטריה %.1f ש׳").format(project.geometrySeconds), C.Violet)
                project.textureSeconds?.let { Chip(tr("טקסטורה %.1f ש׳").format(it), C.Violet) }
                if (mesh.texture != null) Chip(tr("טקסטורה {0}px", mesh.texture!!.size), C.Green)
                exportTime?.let { Chip(it, C.Orange) }
                Chip(project.backend, C.Orange)
            }
            SoftButton(video?.let { tr("מכין סרטון… {0}%", (it * 100).toInt()) } ?: tr("שתף סרטון (טיקטוק, אינסטגרם)"),
                Modifier.fillMaxWidth(), Icons.Rounded.Movie, enabled = video == null) {
                video = 0f
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            File(ctx.cacheDir, "export/Makeo3D.mp4").also {
                                it.parentFile?.mkdirs()
                                TurntableVideo.render(ctx, mesh, it) { p -> video = p }
                            }
                        }
                    }.onSuccess { Actions.shareVideo(ctx, it) }
                        .onFailure {
                            CrashLog.error("turntable video", it)
                            android.widget.Toast.makeText(ctx, tr("יצירת הסרטון נכשלה: {0}", it.message), android.widget.Toast.LENGTH_LONG).show()
                        }
                    video = null
                }
            }

                }
                1 -> {
                    if (project.group == "craft" && project.params.isNotEmpty())
                        SoftButton(tr("שנה מידות, טקסט ואפשרויות"), Modifier.fillMaxWidth(), Icons.Rounded.Tune) { AppState.editAgain(project) }
            if (project.group != "craft") GradientButton(tr("ניקוי במגע: מחיקה והחלקה"), Modifier.fillMaxWidth(), Icons.Rounded.AutoFixHigh, enabled = !busy) {
                showThin = false; rotate = false; wire = false
                view?.let { v ->
                    v.setMesh(mesh)
                    v.startEdit()?.let { clean = it; tool = MeshView.Tool.ERASE }
                }
            }
            if (project.group != "craft") SoftButton(tr("צור מחדש מאותה תמונה"), Modifier.fillMaxWidth(), Icons.Rounded.Autorenew) {
                AppState.recreate(project)
            }
            if (project.group != "craft") {
                SoftButton(Creations.busy ?: tr("תבנית יציקה משני חצאים"), Modifier.fillMaxWidth(), Icons.Rounded.Science,
                    enabled = Creations.busy == null) { Creations.moldOf(mesh) }
                Creations.error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            }
            Section(title = tr("עריכה")) {
                Text(tr("החלקה: {0}", smoothIt.roundToInt()), color = C.Sub, fontSize = 13.sp)
                Slider(smoothIt, { smoothIt = it }, valueRange = 0f..10f, colors = sliderColors())
                Text(tr("פישוט (פחות פוליגונים): {0}%", (reduce * 100).toInt()), color = C.Sub, fontSize = 13.sp)
                Slider(reduce, { reduce = it }, valueRange = 0f..0.9f, colors = sliderColors())
                if (reduce > 0.01f && (AppState.originalMesh ?: mesh).texture != null)
                    Text(tr("פישוט מבטל את הצביעה החדה (נשאר צבע בסיסי)"), color = C.Amber, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(tr("החל"), Modifier.weight(1f), Icons.Rounded.Tune, enabled = !busy) {
                        busy = true
                        scope.launch {
                            val base = cleaned ?: AppState.originalMesh ?: mesh
                            val m = withContext(Dispatchers.Default) { MeshTools.smooth(MeshTools.simplify(base, reduce), smoothIt.roundToInt()) }
                            AppState.mesh = m; edited = true; busy = false
                        }
                    }
                    SoftButton(tr("בטל"), Modifier.weight(0.6f), enabled = edited) {
                        AppState.mesh = AppState.originalMesh; edited = false; smoothIt = 0f; reduce = 0f; cleaned = null
                    }
                }
                if (edited) SoftButton(tr("שמור שינויים בפרויקט"), Modifier.fillMaxWidth(), color = C.Green) {
                    scope.launch(Dispatchers.IO) { AppState.saveEdits(mesh) }
                    edited = false; smoothIt = 0f; reduce = 0f; cleaned = null
                    Actions.toast(ctx, tr("נשמר"))
                }
            }

                }
                2 -> {
            PrintSection(print, showThin, busy, baseCut, onBaseCut = { baseCut = it },
                onShowThin = { on ->
                    val pm = print
                    if (on && thinPreview == null && pm != null) thinPreview = Printability.thicknessPreview(pm)
                    showThin = on
                },
                onExport = { f -> export(f) { Actions.share(ctx, it, f) } })

                }
                else -> {
            Section(title = tr("ייצוא")) {
                Segmented(formats, format, { it.label }, { format = it })
                Text(when (format) {
                    ExportFormat.GLB -> if (mesh.texture != null) tr("מומלץ: עם הטקסטורה החדה, נפתח בכל צופה 3D ובאנדרואיד")
                        else tr("מומלץ: עם צבעים, נפתח בכל צופה 3D ובאנדרואיד")
                    ExportFormat.OBJ_ZIP -> tr("ZIP עם OBJ, MTL ו-PNG: הטקסטורה החדה ל-Blender ו-MeshLab")
                    ExportFormat.OBJ -> tr("Blender, Maya ותוכנות עריכה (צבע לכל קודקוד, בלי הטקסטורה)")
                    ExportFormat.STL -> tr("להדפסה: מתוקן, במילימטרים, עומד על הבסיס (בלי צבע)")
                    ExportFormat.THREE_MF -> tr("הפורמט של תוכנות ההדפסה: מתוקן, במילימטרים, עומד על הבסיס")
                    ExportFormat.SVG -> tr("לחותך לייזר, ויניל או Cricut: קו חיתוך אדום וחלקים בולטים בשחור לחריטה, במילימטרים")
                    ExportFormat.WRL -> tr("הדפסה בצבע מלא (שירותי הדפסה, מדפסות צבע): מתוקן, במילימטרים, עם הצבעים")
                    ExportFormat.PLY -> tr("סריקות ו-MeshLab, צבע לכל קודקוד (בלי הטקסטורה)")
                }, color = C.Sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(tr("שתף מודל"), Modifier.weight(1f), Icons.Rounded.Share, enabled = !busy) {
                        if (format == ExportFormat.STL || format == ExportFormat.THREE_MF) AppState.printFormat = format.name
                        export { Actions.share(ctx, it, format) }
                    }
                    SoftButton(tr("שמור"), Modifier.weight(0.8f), Icons.Rounded.Download, enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val path = timedExport(format) { Actions.saveToDownloads(ctx, mesh, format, project.name) }
                                Actions.toast(ctx, tr("נשמר: {0}", path))
                            } catch (e: Throwable) {
                                CrashLog.error("save", e); Actions.toast(ctx, tr("השמירה נכשלה: {0}", e.message))
                            } finally { busy = false }
                        }
                    }
                }
                SoftButton(tr("פתח בצופה חיצוני / AR"), Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.OpenInNew, enabled = !busy) {
                    export(ExportFormat.GLB) { Actions.openExternal(ctx, it) }
                }
            }
                }
            }
        }
        // The main action stays within reach on every tab: the print format used last.
        val quick = formats.firstOrNull { it.name == AppState.printFormat } ?: formats.firstOrNull { it == ExportFormat.THREE_MF } ?: formats.first()
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientButton(tr("ייצוא להדפסה ({0})", quick.label), Modifier.weight(1f), Icons.Rounded.Download, enabled = !busy) {
                export(quick) { Actions.share(ctx, it, quick) }
            }
            if (tab != 3) SoftButton(tr("פורמטים"), Modifier.width(112.dp)) { tab = 3 }
        }
    }

    if (renaming) RenameDialog(project, onDone = { renaming = false })
}

@Composable
internal fun sliderColors() = SliderDefaults.colors(thumbColor = if (C.dark) Color.White else C.Purple, activeTrackColor = C.Magenta,
    inactiveTrackColor = C.veil(0x22), activeTickColor = Color(0x66FFFFFF), inactiveTickColor = C.veil(0x33))

@Composable
private fun ViewToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) C.Accent else Brush.verticalGradient(listOf(C.veil(0x33), C.veil(0x14))))
            .border(1.dp, if (on) Color(0x55FFFFFF) else C.veil(0x22), RoundedCornerShape(14.dp))
            .pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (on) Color.White else C.Text, modifier = Modifier.size(20.dp))
        }
        Text(label, color = if (on) C.Text else C.Sub, fontSize = 10.sp)
    }
}

@Composable
private fun RenameDialog(p: Project, onDone: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = C.CardHigh,
        title = { Text(tr("שם המודל"), color = C.Text) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton({ if (name.isNotBlank()) AppState.rename(p, name.trim()); onDone() }) { Text(tr("שמור"), color = C.Violet) } },
        dismissButton = { TextButton(onDone) { Text(tr("ביטול"), color = C.Sub) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Violet, unfocusedBorderColor = C.Stroke, focusedTextColor = C.Text,
    unfocusedTextColor = C.Text, cursorColor = C.Violet, focusedContainerColor = C.veil(0x14),
    unfocusedContainerColor = C.veil(0x0D))

/** Print-readiness report: size, repairs, watertightness, wall thickness, and print exports. */
@Composable
internal fun PrintSection(print: PrintModel?, showThin: Boolean, busy: Boolean, baseCut: Float = 0f,
                         onBaseCut: (Float) -> Unit = {}, onShowThin: (Boolean) -> Unit, onExport: (ExportFormat) -> Unit) {
    var cutDraft by remember(baseCut) { mutableFloatStateOf(if (baseCut > 0f) baseCut else 3f) }
    Section(title = tr("הדפסה בתלת־ממד")) {
        if (print == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(tr("בודק את המודל להדפסה…"), color = C.Sub, fontSize = 13.sp)
            }
            return@Section
        }
        val r = print.report
        fun mm(x: Float) = "%.0f".format(x)
        PrintRow(true, tr("גודל: {0} × {1} × {2} מ״מ", mm(r.sizeMm[0]), mm(r.sizeMm[1]), mm(r.sizeMm[2])),
            tr("הצלע הארוכה {0} מ״מ · נפח %.1f סמ״ק", mm(r.sizeMm.max())).format(r.volumeCm3))
        PrintRow(r.watertight, if (r.watertight) tr("אטום ותקין להדפסה") else tr("המודל לא אטום לגמרי"),
            listOfNotNull(
                r.nonManifoldBefore.takeIf { it > 0 }?.let { tr("תוקנו {0} צלעות לא תקינות", it) },
                r.holesFilled.takeIf { it > 0 }?.let { tr("נסגרו {0} פתחים", it) },
                r.floatersRemoved.takeIf { it > 0 }?.let { tr("הוסרו {0} חלקים צפים", it) },
                r.openEdgesAfter.takeIf { it > 0 }?.let { tr("נשארו {0} צלעות פתוחות", it) },
            ).joinToString(" · ").ifEmpty { tr("לא נדרש תיקון") })
        val wallOk = r.minWallMm.isNaN() || r.minWallMm >= Printability.THIN_MM
        PrintRow(wallOk, if (r.minWallMm.isNaN()) tr("עובי דפנות: לא נמדד") else tr("דופן מינימלית: %.1f מ״מ").format(r.minWallMm),
            if (r.thinShare > 0f) tr("%.1f%% מהמשטח דק מ-%.0f מ״מ (באדום)").format(r.thinShare * 100, Printability.THIN_MM)
            else tr("אין אזורים דקים מ-%.0f מ״מ").format(Printability.THIN_MM))
        ToggleRow(tr("הצג אזורים דקים"), tr("אדום: פחות מ-1 מ״מ · כתום: 1–2 מ״מ"), showThin, onShowThin)
        ToggleRow(tr("בסיס שטוח"), tr("חותך את התחתית כדי שהמודל יעמוד יציב על המגש"), baseCut > 0f) { on -> onBaseCut(if (on) cutDraft else 0f) }
        if (baseCut > 0f) {
            Text(tr("גובה החיתוך: %.0f מ״מ").format(cutDraft), color = C.Text, fontSize = 13.sp)
            Slider(cutDraft, { cutDraft = it.roundToInt().toFloat() }, valueRange = 1f..20f,
                onValueChangeFinished = { onBaseCut(cutDraft) }, colors = sliderColors())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(tr("3MF להדפסה"), Modifier.weight(1f), Icons.Rounded.Share, enabled = !busy) { onExport(ExportFormat.THREE_MF) }
            SoftButton("STL", Modifier.weight(0.7f), Icons.Rounded.Share, enabled = !busy) { onExport(ExportFormat.STL) }
        }
        Text(tr("עומד על הבסיס, ציר Z למעלה, במילימטרים. 3MF נפתח ישירות ב-Bambu Studio, PrusaSlicer ו-Cura."),
            color = C.Sub, fontSize = 12.sp)
    }
}

@Composable
private fun PrintRow(ok: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null,
            tint = if (ok) C.Green else C.Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = C.Sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun ProjectsScreen() {
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Project?>(null) }
    var tab by remember { mutableStateOf("") }
    val list = AppState.projects.filter {
        (tab.isEmpty() || (if (tab == "fav") it.favorite else it.group == tab)) && (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("הפרויקטים שלי"))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in listOf("" to tr("הכל"), "fav" to tr("אהבתי"), "sr" to tr("מהיר (TripoSR)"), "sg" to tr("מדויק (TripoSG)"), "craft" to tr("יצירות להדפסה"))) {
                val n = if (key.isEmpty()) AppState.projects.size else AppState.projects.count { if (key == "fav") it.favorite else it.group == key }
                if (key.isNotEmpty() && key != "fav" && n == 0) continue
                val on = tab == key
                Text("${label} · ${n}", color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) C.Violet else C.veil(0x14))
                        .pressable { tab = key }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, colors = fieldColors(),
            placeholder = { Text(tr("חיפוש פרויקטים…"), color = C.Sub) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = C.Sub) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Logo(72)
                Spacer(Modifier.height(16.dp))
                Text(if (AppState.projects.isEmpty()) tr("עוד אין מודלים") else tr("לא נמצאו תוצאות"), color = C.Text,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(tr("כל מודל שתיצור יישמר כאן"), color = C.Sub, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                GradientButton(tr("צור מודל חדש"), sparkles = true) { AppState.startNew() }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                items(list, key = { it.id }) { p ->
                    Box(if (p.id == list.first().id) Modifier.tourTarget("project") else Modifier) {
                        ProjectThumb(p, Modifier.fillMaxWidth())
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp).clip(CircleShape)
                            .background(Color(0xAA120B2A)).border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .pressable { toDelete = p }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Delete, tr("מחק"), tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = C.CardHigh,
            title = { Text(tr("למחוק את \"{0}\"?", p.name), color = C.Text) },
            confirmButton = { TextButton({ AppState.delete(p); toDelete = null }) { Text(tr("מחק"), color = C.Red) } },
            dismissButton = { TextButton({ toDelete = null }) { Text(tr("ביטול"), color = C.Sub) } },
        )
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var backend by remember { mutableStateOf(Settings.backendChoice(ctx)) }
    var quality by remember { mutableStateOf(Settings.quality(ctx)) }
    var removeBg by remember { mutableStateOf(Settings.removeBackground(ctx)) }
    var smooth by remember { mutableStateOf(Settings.smooth(ctx)) }
    var texture by remember { mutableStateOf(Settings.bakeTexture(ctx)) }
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    val problems = remember(Health.tick.intValue) { Health.problems() }
    val errors = problems.count { it.level == Health.Level.ERROR }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("הגדרות"), onBack = if (AppState.backStack.size > 1) ({ AppState.back() }) else null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(Modifier.enter(0), title = tr("תמיכה ומשוב")) {
                val waiting = CrashLog.pendingReport != null || errors > 0
                NavRow(Icons.Rounded.BugReport, tr("דווח על תקלה"),
                    if (waiting) tr("נשמר דוח תקלה · לחץ כדי לשלוח לנו") else tr("שולח לנו דוח מצב שעוזר לתקן מהר"),
                    badge = if (waiting) "1" else null, badgeColor = C.Amber,
                    brush = Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFF97316)))) {
                    Actions.email(ctx, tr("דיווח על תקלה"), tr("מה קרה? (כמה מילים עוזרות מאוד)\n\n"),
                        (CrashLog.pendingReport?.let { it + "\n\n" } ?: "") + CrashLog.diagnostics())
                }
                RowDivider()
                NavRow(Icons.Rounded.Lightbulb, tr("הצעה לשיפור"), tr("רעיון לפיצ'ר או משהו שיכול להיות טוב יותר")) {
                    Actions.email(ctx, tr("הצעה לשיפור"), "")
                }
                RowDivider()
                NavRow(Icons.Rounded.Email, tr("צור קשר"), Actions.SUPPORT_EMAIL) { Actions.email(ctx, tr("פנייה"), "") }
            }
            Section(Modifier.enter(0), title = tr("מראה")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((l, label) in listOf(Look.AUTO to tr("לפי המכשיר"), Look.DARK to tr("כהה"), Look.LIGHT to tr("בהיר"))) {
                        val on = Appearance.look == l
                        Text(label, color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Violet else C.veil(0x14))
                                .pressable { Appearance.set(ctx, l) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Section(Modifier.enter(0), title = tr("שמירת פרויקטים")) {
                var keep by remember { mutableStateOf(Settings.keepProjects(ctx)) }
                Text(tr("כמה פרויקטים לשמור. כשיש יותר, הישנים נמחקים לבד. פרויקט שסימנת בלב לא נמחק אף פעם."), color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in listOf(10, 20, 50, 100, 0)) {
                        val on = keep == n
                        Text(if (n == 0) tr("לא למחוק") else n.toString(), color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Violet else C.veil(0x14))
                                .pressable { keep = n; Settings.setKeepProjects(ctx, n) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Section(Modifier.enter(0), title = tr("הדרכה")) {
                NavRow(Icons.Rounded.Lightbulb, tr("הצג את הסיור שוב"), tr("הסבר קצר על כל אזור באפליקציה")) { Tour.start() }
            }
            Section(Modifier.enter(0), title = "שפה · Language") {
                var lang by remember { mutableStateOf(com.triposr.mobile.I18n.choice(ctx)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((c, label) in listOf(com.triposr.mobile.I18n.Choice.AUTO to tr("אוטומטי"),
                        com.triposr.mobile.I18n.Choice.HE to "עברית", com.triposr.mobile.I18n.Choice.EN to "English")) {
                        val on = lang == c
                        Text(label, color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Violet else C.veil(0x14))
                                .pressable { lang = c; com.triposr.mobile.I18n.setChoice(ctx, c) }
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                run {
                    val now = when (lang) {
                        com.triposr.mobile.I18n.Choice.HE -> false
                        com.triposr.mobile.I18n.Choice.EN -> true
                        else -> java.util.Locale.getDefault().language !in setOf("he", "iw")
                    }
                    if (now != com.triposr.mobile.I18n.english) SoftButton("הפעל מחדש כדי להחליף שפה · Restart to switch", Modifier.fillMaxWidth()) {
                        // A fresh process reads the new language from the start.
                        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        ctx.startActivity(i)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
            Section(Modifier.enter(0)) {
                if (!BuildConfig.STORE) NavRow(Icons.Rounded.BugReport, tr("דוחות ותקלות"),
                    when {
                        errors > 0 -> tr("{0} שגיאות זוהו · לחץ להעתקה ושליחה", errors)
                        problems.isNotEmpty() -> tr("{0} אזהרות · הכל עובד", problems.size)
                        CrashLog.pendingReport != null -> tr("יש דוח קריסה מהפעלה קודמת")
                        else -> tr("לא זוהו תקלות")
                    },
                    badge = if (errors > 0) "${errors}" else if (problems.isNotEmpty()) "${problems.size}" else null,
                    badgeColor = if (errors > 0) C.Red else C.Amber,
                    brush = Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFF97316)))) { AppState.navigate(Screen.Diagnostics) }
                if (!BuildConfig.STORE) RowDivider()
                NavRow(Icons.Rounded.Memory, tr("מודלים ומעבדים"), tr("הורדה, מחיקה ובדיקת NPU/GPU")) { AppState.navigateRoot(Screen.Models) }
            }
            Section(Modifier.enter(1), title = tr("ברירות מחדל ליצירה")) {
                Text(tr("מעבד"), color = C.Sub, fontSize = 13.sp)
                BackendPicker(backend) { backend = it; Settings.setBackendChoice(ctx, it) }
                Text(tr("רמת פירוט"), color = C.Sub, fontSize = 13.sp)
                QualityCards(quality) { quality = it; Settings.setQuality(ctx, it) }
                RowDivider()
                ToggleRow(tr("הסרת רקע אוטומטית"), null, removeBg) { removeBg = it; Settings.setRemoveBackground(ctx, it) }
                ToggleRow(tr("החלקת משטח"), null, smooth) { smooth = it; Settings.setSmooth(ctx, it) }
                ToggleRow(tr("צביעה חדה (טקסטורה)"), tr("פנים, בגדים והדפסים חדים יותר · מוסיף כמה שניות"), texture) {
                    texture = it; Settings.setBakeTexture(ctx, it)
                }
            }
            Section(Modifier.enter(2), title = tr("עדכונים")) {
                Text(tr("גרסה {0} (build {1})", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = C.Text)
                if (!BuildConfig.STORE) SoftButton(if (checking) tr("בודק…") else tr("בדוק עדכונים"), Modifier.fillMaxWidth(), enabled = !checking) {
                    checking = true
                    scope.launch {
                        updateMsg = runCatching { Updater.check() }.fold(
                            { u -> AppState.update = u; if (u == null) tr("אתה בגרסה האחרונה") else null },
                            { tr("הבדיקה נכשלה: {0}", it.message) })
                        checking = false
                    }
                }
                updateMsg?.let { Text(it, color = C.Sub, fontSize = 13.sp) }
                UpdateCard()
            }
            Section(Modifier.enter(3), title = tr("משפטי ופרטיות")) {
                NavRow(Icons.Rounded.Gavel, tr("תנאי שימוש"), tr("אחריות על התוכן שאתה יוצר")) { AppState.openLegal(0) }
                RowDivider()
                NavRow(Icons.Rounded.PrivacyTip, tr("מדיניות פרטיות"), tr("התמונות לא יוצאות מהטלפון")) { AppState.openLegal(1) }
                RowDivider()
                NavRow(Icons.Rounded.Description, tr("רישיונות קוד פתוח"), tr("המודלים והספריות שבאפליקציה")) { AppState.openLegal(2) }
            }
            Section(Modifier.enter(4), title = tr("אודות")) {
                Text(tr("תמונה לתלת־ממד ויצירות להדפסה, ישירות בטלפון. המודלים (TripoSR, TripoSG) רצים מקומית עם ") +
                    tr("ONNX Runtime ו-Qualcomm QNN, בלי שרת ובלי להעלות תמונות."), color = C.Sub, fontSize = 13.sp)
                Text(tr("עובד בכל טלפון אנדרואיד 13 ומעלה. בטלפונים בלי מאיץ בינה מלאכותית (NPU) החישוב רץ על המעבד ") +
                    tr("ולוקח יותר זמן, ובטלפון עם מעט זיכרון מודל כבד עלול להיכשל. TripoSG זמין רק בטלפונים עם ") +
                    "Snapdragon 8 Elite.", color = C.Sub, fontSize = 13.sp)
                Text(tr("© 2026 Makeo 3D. כל הזכויות שמורות. הקוד, העיצוב, השם והמודלים המומרים שייכים ל-Makeo 3D; ") +
                    tr("רכיבי קוד פתוח שייכים לבעליהם ומופיעים ברישיונות."), color = C.Faint, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val healthTick = Health.tick.intValue
    val report = remember(tick, healthTick) { CrashLog.diagnostics() }
    val crash = remember(tick) { CrashLog.pendingReport }
    val lastError = remember(tick, healthTick) { CrashLog.lastError }
    val problems = remember(tick, healthTick) { Health.problems() }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("דוחות ותקלות"), onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section {
                Text(tr("כל תקלה שהאפליקציה מזהה נרשמת כאן: בדיקה אוטומטית בכל הפעלה (ספריות, מודלים, אחסון, מעבדים) ") +
                    tr("וכל שגיאה בזמן עבודה. העתק ושלח לי את הדוח המלא."), color = C.Sub, fontSize = 13.sp)
                GradientButton(tr("העתק דוח מלא"), Modifier.fillMaxWidth(), Icons.Rounded.ContentCopy) {
                    Actions.copy(ctx, (crash?.let { it + "\n\n" } ?: "") + report, tr("הדוח"))
                }
            }
            Section(title = if (problems.isEmpty()) null else tr("תקלות שזוהו ({0})", problems.size)) {
                if (problems.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = C.Green)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("לא זוהו תקלות"), color = C.Green, fontWeight = FontWeight.SemiBold)
                }
                problems.asReversed().forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (p.level == Health.Level.ERROR) Icons.Rounded.Error else Icons.Rounded.Warning, null,
                            tint = if (p.level == Health.Level.ERROR) C.Red else C.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${p.area} · ${p.time}", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(p.message, color = C.Sub, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (crash != null) ReportSection(tr("קריסה מהפעלה קודמת"), crash, C.Red)
            if (lastError != null) ReportSection(tr("שגיאה אחרונה"), lastError, C.Orange)
            ReportSection(tr("דוח מצב מלא"), report, C.Violet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(tr("רענן"), Modifier.weight(1f), Icons.Rounded.Refresh) { tick++ }
                SoftButton(tr("נקה דוחות"), Modifier.weight(1f), Icons.Rounded.Delete, color = C.Red) { confirmClear = true }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = C.CardHigh,
        title = { Text(tr("לנקות את כל הדוחות?"), color = C.Text) },
        text = { Text(tr("היומן, דוח הקריסה ורשימת התקלות יימחקו. אם יש תקלה שעוד לא שלחת, העתק אותה קודם."), color = C.Sub) },
        confirmButton = { TextButton({ CrashLog.clear(); confirmClear = false; tick++ }) { Text(tr("נקה"), color = C.Red) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text(tr("ביטול"), color = C.Sub) } },
    )
}

@Composable
private fun ReportSection(title: String, text: String, color: Color) {
    val ctx = LocalContext.current
    Section {
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(tr("העתק"), Modifier.weight(1f), Icons.Rounded.ContentCopy) { Actions.copy(ctx, text) }
            SoftButton(tr("שתף"), Modifier.weight(1f), Icons.Rounded.Share) { Actions.shareText(ctx, text) }
        }
        // Logs are English/code: show them left-to-right in a monospace font.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            SelectionContainer {
                Text(text.take(12000), color = C.Sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))
                        .background(C.Bg).verticalScroll(rememberScrollState()).padding(10.dp))
            }
        }
    }
}

private const val PRINT_CHECK_MAX_TRIANGLES = 300_000
private val printCheckLock = kotlinx.coroutines.sync.Mutex()
