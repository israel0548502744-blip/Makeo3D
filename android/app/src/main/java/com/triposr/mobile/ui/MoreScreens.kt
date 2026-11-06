package com.triposr.mobile.ui

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
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Brush
import com.triposr.mobile.Health
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
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
    LaunchedEffect(mesh, baseCut) {
        print = null; thinPreview = null; showThin = false
        Printability.baseCutMm = baseCut
        runCatching {
            withContext(Dispatchers.Default) {
                val t0 = System.nanoTime()
                // Very dense meshes (TripoSG makes ~800k triangles) run the check out of Java heap.
                val m = if (mesh.triangleCount > PRINT_CHECK_MAX_TRIANGLES)
                    MeshTools.simplify(mesh, (1f - PRINT_CHECK_MAX_TRIANGLES.toFloat() / mesh.triangleCount).coerceAtMost(0.9f)) else mesh
                Printability.prepared(m, baseCut).also {
                    CrashLog.log("Print check in %.2fs: watertight=${it.report.watertight} minWall=%.2fmm".format(
                        (System.nanoTime() - t0) / 1e9, it.report.minWallMm))
                }
            }
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
        exportTime = "ייצוא ${fmt.label}: %.1f ש׳".format(s)
        return r
    }

    fun export(fmt: ExportFormat = format, action: (android.net.Uri) -> Unit) {
        busy = true
        scope.launch {
            try {
                val uri = timedExport(fmt) { Actions.exportToCache(ctx, mesh, fmt, project.name) }
                action(uri)
            } catch (e: Throwable) {
                CrashLog.error("export", e); Actions.toast(ctx, "הייצוא נכשל: ${e.message}")
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(project.name, onBack = { AppState.back() }) {
            CircleIcon(Icons.Rounded.Edit, "שנה שם") { renaming = true }
            CircleIcon(Icons.Rounded.Share, "שתף") { export { Actions.share(ctx, it, format) } }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(440.dp).enter(0).glass(RoundedCornerShape(28.dp))) {
                // Screenshot tests (inspection mode) have no OpenGL: show the mesh's thumbnail instead.
                if (androidx.compose.ui.platform.LocalInspectionMode.current) {
                    rememberThumb(project)?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null,
                        modifier = Modifier.fillMaxSize().padding(40.dp)) }
                } else AndroidView(
                    factory = { MeshView(it).also { v -> view = v } },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        v.setMesh(if (showThin) thinPreview ?: mesh else mesh)
                        v.setOptions(colors, wire, light, grid, rotate)
                    },
                    onRelease = { it.onPause() },
                )
                Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewToggle(Icons.Rounded.Grid4x4, "רשת", wire) { wire = !wire }
                    ViewToggle(Icons.Rounded.Palette, "צבע", colors) { colors = !colors }
                    ViewToggle(Icons.Rounded.Lightbulb, "תאורה", light) { light = !light }
                    ViewToggle(Icons.Rounded.GridOn, "רצפה", grid) { grid = !grid }
                    ViewToggle(Icons.Rounded.Autorenew, "סיבוב", rotate) { rotate = !rotate }
                    ViewToggle(Icons.Rounded.CenterFocusStrong, "איפוס", false) { view?.resetView() }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(project.name, C.Text)
                Chip("${"%,d".format(mesh.triangleCount)} פוליגונים")
                Chip("${"%,d".format(mesh.vertexCount)} קודקודים")
                Chip("%.1f שניות".format(project.seconds), C.Green)
                if (project.geometrySeconds > 0) Chip("גאומטריה %.1f ש׳".format(project.geometrySeconds), C.Violet)
                project.textureSeconds?.let { Chip("טקסטורה %.1f ש׳".format(it), C.Violet) }
                if (mesh.texture != null) Chip("טקסטורה ${mesh.texture!!.size}px", C.Green)
                exportTime?.let { Chip(it, C.Orange) }
                Chip(project.backend, C.Orange)
            }

            Section(title = "עריכה") {
                Text("החלקה: ${smoothIt.roundToInt()}", color = C.Sub, fontSize = 13.sp)
                Slider(smoothIt, { smoothIt = it }, valueRange = 0f..10f, colors = sliderColors())
                Text("פישוט (פחות פוליגונים): ${(reduce * 100).toInt()}%", color = C.Sub, fontSize = 13.sp)
                Slider(reduce, { reduce = it }, valueRange = 0f..0.9f, colors = sliderColors())
                if (reduce > 0.01f && (AppState.originalMesh ?: mesh).texture != null)
                    Text("פישוט מבטל את הצביעה החדה (נשאר צבע בסיסי)", color = C.Amber, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("החל", Modifier.weight(1f), Icons.Rounded.Tune, enabled = !busy) {
                        busy = true
                        scope.launch {
                            val base = AppState.originalMesh ?: mesh
                            val m = withContext(Dispatchers.Default) { MeshTools.smooth(MeshTools.simplify(base, reduce), smoothIt.roundToInt()) }
                            AppState.mesh = m; edited = true; busy = false
                        }
                    }
                    SoftButton("בטל", Modifier.weight(0.6f), enabled = edited) {
                        AppState.mesh = AppState.originalMesh; edited = false; smoothIt = 0f; reduce = 0f
                    }
                }
                if (edited) SoftButton("שמור שינויים בפרויקט", Modifier.fillMaxWidth(), color = C.Green) {
                    scope.launch(Dispatchers.IO) { AppState.saveEdits(mesh) }
                    edited = false; smoothIt = 0f; reduce = 0f
                    Actions.toast(ctx, "נשמר")
                }
            }

            PrintSection(print, showThin, busy, baseCut, onBaseCut = { baseCut = it },
                onShowThin = { on ->
                    val pm = print
                    if (on && thinPreview == null && pm != null) thinPreview = Printability.thicknessPreview(pm)
                    showThin = on
                },
                onExport = { f -> export(f) { Actions.share(ctx, it, f) } })

            Section(title = "ייצוא") {
                Segmented(formats, format, { it.label }, { format = it })
                Text(when (format) {
                    ExportFormat.GLB -> if (mesh.texture != null) "מומלץ: עם הטקסטורה החדה, נפתח בכל צופה 3D ובאנדרואיד"
                        else "מומלץ: עם צבעים, נפתח בכל צופה 3D ובאנדרואיד"
                    ExportFormat.OBJ_ZIP -> "ZIP עם OBJ, MTL ו-PNG: הטקסטורה החדה ל-Blender ו-MeshLab"
                    ExportFormat.OBJ -> "Blender, Maya ותוכנות עריכה (צבע לכל קודקוד, בלי הטקסטורה)"
                    ExportFormat.STL -> "להדפסה: מתוקן, במילימטרים, עומד על הבסיס (בלי צבע)"
                    ExportFormat.THREE_MF -> "הפורמט של תוכנות ההדפסה: מתוקן, במילימטרים, עומד על הבסיס"
                    ExportFormat.SVG -> "לחותך לייזר, ויניל או Cricut: קו חיתוך אדום וחלקים בולטים בשחור לחריטה, במילימטרים"
                    ExportFormat.WRL -> "הדפסה בצבע מלא (שירותי הדפסה, מדפסות צבע): מתוקן, במילימטרים, עם הצבעים"
                    ExportFormat.PLY -> "סריקות ו-MeshLab, צבע לכל קודקוד (בלי הטקסטורה)"
                }, color = C.Sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("שתף מודל", Modifier.weight(1f), Icons.Rounded.Share, enabled = !busy) {
                        export { Actions.share(ctx, it, format) }
                    }
                    SoftButton("שמור", Modifier.weight(0.8f), Icons.Rounded.Download, enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val path = timedExport(format) { Actions.saveToDownloads(ctx, mesh, format, project.name) }
                                Actions.toast(ctx, "נשמר: $path")
                            } catch (e: Throwable) {
                                CrashLog.error("save", e); Actions.toast(ctx, "השמירה נכשלה: ${e.message}")
                            } finally { busy = false }
                        }
                    }
                }
                SoftButton("פתח בצופה חיצוני / AR", Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.OpenInNew, enabled = !busy) {
                    export(ExportFormat.GLB) { Actions.openExternal(ctx, it) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (renaming) RenameDialog(project, onDone = { renaming = false })
}

@Composable
internal fun sliderColors() = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = C.Magenta,
    inactiveTrackColor = Color(0x22FFFFFF), activeTickColor = Color(0x66FFFFFF), inactiveTickColor = Color(0x33FFFFFF))

@Composable
private fun ViewToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) C.Accent else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, if (on) Color(0x55FFFFFF) else Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
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
        title = { Text("שם המודל", color = C.Text) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton({ if (name.isNotBlank()) AppState.rename(p, name.trim()); onDone() }) { Text("שמור", color = C.Violet) } },
        dismissButton = { TextButton(onDone) { Text("ביטול", color = C.Sub) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Violet, unfocusedBorderColor = C.Stroke, focusedTextColor = C.Text,
    unfocusedTextColor = C.Text, cursorColor = C.Violet, focusedContainerColor = Color(0x14FFFFFF),
    unfocusedContainerColor = Color(0x0DFFFFFF))

/** Print-readiness report: size, repairs, watertightness, wall thickness, and print exports. */
@Composable
internal fun PrintSection(print: PrintModel?, showThin: Boolean, busy: Boolean, baseCut: Float = 0f,
                         onBaseCut: (Float) -> Unit = {}, onShowThin: (Boolean) -> Unit, onExport: (ExportFormat) -> Unit) {
    var cutDraft by remember(baseCut) { mutableFloatStateOf(if (baseCut > 0f) baseCut else 3f) }
    Section(title = "הדפסה בתלת־ממד") {
        if (print == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("בודק את המודל להדפסה…", color = C.Sub, fontSize = 13.sp)
            }
            return@Section
        }
        val r = print.report
        fun mm(x: Float) = "%.0f".format(x)
        PrintRow(true, "גודל: ${mm(r.sizeMm[0])} × ${mm(r.sizeMm[1])} × ${mm(r.sizeMm[2])} מ״מ",
            "הצלע הארוכה ${mm(r.sizeMm.max())} מ״מ · נפח %.1f סמ״ק".format(r.volumeCm3))
        PrintRow(r.watertight, if (r.watertight) "אטום ותקין להדפסה" else "המודל לא אטום לגמרי",
            listOfNotNull(
                r.nonManifoldBefore.takeIf { it > 0 }?.let { "תוקנו $it צלעות לא תקינות" },
                r.holesFilled.takeIf { it > 0 }?.let { "נסגרו $it פתחים" },
                r.floatersRemoved.takeIf { it > 0 }?.let { "הוסרו $it חלקים צפים" },
                r.openEdgesAfter.takeIf { it > 0 }?.let { "נשארו $it צלעות פתוחות" },
            ).joinToString(" · ").ifEmpty { "לא נדרש תיקון" })
        val wallOk = r.minWallMm.isNaN() || r.minWallMm >= Printability.THIN_MM
        PrintRow(wallOk, if (r.minWallMm.isNaN()) "עובי דפנות: לא נמדד" else "דופן מינימלית: %.1f מ״מ".format(r.minWallMm),
            if (r.thinShare > 0f) "%.1f%% מהמשטח דק מ-%.0f מ״מ (באדום)".format(r.thinShare * 100, Printability.THIN_MM)
            else "אין אזורים דקים מ-%.0f מ״מ".format(Printability.THIN_MM))
        ToggleRow("הצג אזורים דקים", "אדום: פחות מ-1 מ״מ · כתום: 1–2 מ״מ", showThin, onShowThin)
        ToggleRow("בסיס שטוח", "חותך את התחתית כדי שהמודל יעמוד יציב על המגש", baseCut > 0f) { on -> onBaseCut(if (on) cutDraft else 0f) }
        if (baseCut > 0f) {
            Text("גובה החיתוך: %.0f מ״מ".format(cutDraft), color = C.Text, fontSize = 13.sp)
            Slider(cutDraft, { cutDraft = it.roundToInt().toFloat() }, valueRange = 1f..20f,
                onValueChangeFinished = { onBaseCut(cutDraft) }, colors = sliderColors())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("3MF להדפסה", Modifier.weight(1f), Icons.Rounded.Share, enabled = !busy) { onExport(ExportFormat.THREE_MF) }
            SoftButton("STL", Modifier.weight(0.7f), Icons.Rounded.Share, enabled = !busy) { onExport(ExportFormat.STL) }
        }
        Text("עומד על הבסיס, ציר Z למעלה, במילימטרים. 3MF נפתח ישירות ב-Bambu Studio, PrusaSlicer ו-Cura.",
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
    val list = AppState.projects.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        TopBar("הגלריה שלי")
        OutlinedTextField(query, { query = it }, singleLine = true, colors = fieldColors(),
            placeholder = { Text("חיפוש פרויקטים…", color = C.Sub) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = C.Sub) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Logo(72)
                Spacer(Modifier.height(16.dp))
                Text(if (AppState.projects.isEmpty()) "עוד אין מודלים" else "לא נמצאו תוצאות", color = C.Text,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("כל מודל שתיצור יישמר כאן", color = C.Sub, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                GradientButton("צור מודל חדש", sparkles = true) { AppState.startNew() }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                items(list, key = { it.id }) { p ->
                    Box {
                        ProjectThumb(p, Modifier.fillMaxWidth())
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp).clip(CircleShape)
                            .background(Color(0xAA120B2A)).border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .pressable { toDelete = p }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Delete, "מחק", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = C.CardHigh,
            title = { Text("למחוק את \"${p.name}\"?", color = C.Text) },
            confirmButton = { TextButton({ AppState.delete(p); toDelete = null }) { Text("מחק", color = C.Red) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("ביטול", color = C.Sub) } },
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
        TopBar("הגדרות")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(Modifier.enter(0), title = "תמיכה ומשוב") {
                val waiting = CrashLog.pendingReport != null || errors > 0
                NavRow(Icons.Rounded.BugReport, "דווח על תקלה",
                    if (waiting) "נשמר דוח תקלה · לחץ כדי לשלוח לנו" else "שולח לנו דוח מצב שעוזר לתקן מהר",
                    badge = if (waiting) "1" else null, badgeColor = C.Amber,
                    brush = Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFF97316)))) {
                    Actions.email(ctx, "דיווח על תקלה", "מה קרה? (כמה מילים עוזרות מאוד)\n\n",
                        (CrashLog.pendingReport?.let { it + "\n\n" } ?: "") + CrashLog.diagnostics())
                }
                RowDivider()
                NavRow(Icons.Rounded.Lightbulb, "הצעה לשיפור", "רעיון לפיצ'ר או משהו שיכול להיות טוב יותר") {
                    Actions.email(ctx, "הצעה לשיפור", "")
                }
                RowDivider()
                NavRow(Icons.Rounded.Email, "צור קשר", Actions.SUPPORT_EMAIL) { Actions.email(ctx, "פנייה", "") }
            }
            Section(Modifier.enter(0)) {
                if (!BuildConfig.STORE) NavRow(Icons.Rounded.BugReport, "דוחות ותקלות",
                    when {
                        errors > 0 -> "$errors שגיאות זוהו · לחץ להעתקה ושליחה"
                        problems.isNotEmpty() -> "${problems.size} אזהרות · הכל עובד"
                        CrashLog.pendingReport != null -> "יש דוח קריסה מהפעלה קודמת"
                        else -> "לא זוהו תקלות"
                    },
                    badge = if (errors > 0) "$errors" else if (problems.isNotEmpty()) "${problems.size}" else null,
                    badgeColor = if (errors > 0) C.Red else C.Amber,
                    brush = Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFF97316)))) { AppState.navigate(Screen.Diagnostics) }
                if (!BuildConfig.STORE) RowDivider()
                NavRow(Icons.Rounded.Memory, "מודלים ומעבדים", "הורדה, מחיקה ובדיקת NPU/GPU") { AppState.navigateRoot(Screen.Models) }
            }
            Section(Modifier.enter(1), title = "ברירות מחדל ליצירה") {
                Text("מעבד", color = C.Sub, fontSize = 13.sp)
                BackendPicker(backend) { backend = it; Settings.setBackendChoice(ctx, it) }
                Text("רמת פירוט", color = C.Sub, fontSize = 13.sp)
                QualityCards(quality) { quality = it; Settings.setQuality(ctx, it) }
                RowDivider()
                ToggleRow("הסרת רקע אוטומטית", null, removeBg) { removeBg = it; Settings.setRemoveBackground(ctx, it) }
                ToggleRow("החלקת משטח", null, smooth) { smooth = it; Settings.setSmooth(ctx, it) }
                ToggleRow("צביעה חדה (טקסטורה)", "פנים, בגדים והדפסים חדים יותר · מוסיף כמה שניות", texture) {
                    texture = it; Settings.setBakeTexture(ctx, it)
                }
            }
            Section(Modifier.enter(2), title = "עדכונים") {
                Text("גרסה ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = C.Text)
                if (!BuildConfig.STORE) SoftButton(if (checking) "בודק…" else "בדוק עדכונים", Modifier.fillMaxWidth(), enabled = !checking) {
                    checking = true
                    scope.launch {
                        updateMsg = runCatching { Updater.check() }.fold(
                            { u -> AppState.update = u; if (u == null) "אתה בגרסה האחרונה" else null },
                            { "הבדיקה נכשלה: ${it.message}" })
                        checking = false
                    }
                }
                updateMsg?.let { Text(it, color = C.Sub, fontSize = 13.sp) }
                UpdateCard()
            }
            Section(Modifier.enter(3), title = "משפטי ופרטיות") {
                NavRow(Icons.Rounded.Gavel, "תנאי שימוש", "אחריות על התוכן שאתה יוצר") { AppState.openLegal(0) }
                RowDivider()
                NavRow(Icons.Rounded.PrivacyTip, "מדיניות פרטיות", "התמונות לא יוצאות מהטלפון") { AppState.openLegal(1) }
                RowDivider()
                NavRow(Icons.Rounded.Description, "רישיונות קוד פתוח", "המודלים והספריות שבאפליקציה") { AppState.openLegal(2) }
            }
            Section(Modifier.enter(4), title = "אודות") {
                Text("תמונה לתלת־ממד ויצירות להדפסה, ישירות בטלפון. המודלים (TripoSR, TripoSG) רצים מקומית עם " +
                    "ONNX Runtime ו-Qualcomm QNN, בלי שרת ובלי להעלות תמונות.", color = C.Sub, fontSize = 13.sp)
                Text("עובד בכל טלפון אנדרואיד 13 ומעלה. בטלפונים בלי מאיץ בינה מלאכותית (NPU) החישוב רץ על המעבד " +
                    "ולוקח יותר זמן, ובטלפון עם מעט זיכרון מודל כבד עלול להיכשל. TripoSG זמין רק בטלפונים עם " +
                    "Snapdragon 8 Elite.", color = C.Sub, fontSize = 13.sp)
                Text("© 2026 Makeo 3D. כל הזכויות שמורות. הקוד, העיצוב, השם והמודלים המומרים שייכים ל-Makeo 3D; " +
                    "רכיבי קוד פתוח שייכים לבעליהם ומופיעים ברישיונות.", color = C.Faint, fontSize = 12.sp)
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
        TopBar("דוחות ותקלות", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section {
                Text("כל תקלה שהאפליקציה מזהה נרשמת כאן: בדיקה אוטומטית בכל הפעלה (ספריות, מודלים, אחסון, מעבדים) " +
                    "וכל שגיאה בזמן עבודה. העתק ושלח לי את הדוח המלא.", color = C.Sub, fontSize = 13.sp)
                GradientButton("העתק דוח מלא", Modifier.fillMaxWidth(), Icons.Rounded.ContentCopy) {
                    Actions.copy(ctx, (crash?.let { it + "\n\n" } ?: "") + report, "הדוח")
                }
            }
            Section(title = if (problems.isEmpty()) null else "תקלות שזוהו (${problems.size})") {
                if (problems.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = C.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("לא זוהו תקלות", color = C.Green, fontWeight = FontWeight.SemiBold)
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
            if (crash != null) ReportSection("קריסה מהפעלה קודמת", crash, C.Red)
            if (lastError != null) ReportSection("שגיאה אחרונה", lastError, C.Orange)
            ReportSection("דוח מצב מלא", report, C.Violet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("רענן", Modifier.weight(1f), Icons.Rounded.Refresh) { tick++ }
                SoftButton("נקה דוחות", Modifier.weight(1f), Icons.Rounded.Delete, color = C.Red) { confirmClear = true }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = C.CardHigh,
        title = { Text("לנקות את כל הדוחות?", color = C.Text) },
        text = { Text("היומן, דוח הקריסה ורשימת התקלות יימחקו. אם יש תקלה שעוד לא שלחת, העתק אותה קודם.", color = C.Sub) },
        confirmButton = { TextButton({ CrashLog.clear(); confirmClear = false; tick++ }) { Text("נקה", color = C.Red) } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("ביטול", color = C.Sub) } },
    )
}

@Composable
private fun ReportSection(title: String, text: String, color: Color) {
    val ctx = LocalContext.current
    Section {
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("העתק", Modifier.weight(1f), Icons.Rounded.ContentCopy) { Actions.copy(ctx, text) }
            SoftButton("שתף", Modifier.weight(1f), Icons.Rounded.Share) { Actions.shareText(ctx, text) }
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
