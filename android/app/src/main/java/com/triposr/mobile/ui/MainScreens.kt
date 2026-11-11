package com.triposr.mobile.ui

import com.triposr.mobile.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.BuildConfig
import com.triposr.mobile.AppState
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.triposr.mobile.Extras
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import com.triposr.mobile.TripoEngine
import com.triposr.mobile.Backend
import com.triposr.mobile.Capabilities
import com.triposr.mobile.CrashLog
import com.triposr.mobile.Generator
import com.triposr.mobile.ImageIssue
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Project
import com.triposr.mobile.Quality
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Stage
import com.triposr.mobile.Support
import com.triposr.mobile.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls DownloadManager while visible. */
@Composable
fun rememberModelStatus(pack: com.triposr.mobile.Pack = ModelStore.MAIN): ModelStore.Status {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(runCatching { ModelStore.status(ctx, pack) }.getOrDefault(ModelStore.Status.Missing)) }
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { ModelStore.status(ctx, pack) }.getOrDefault(ModelStore.Status.Missing)
            delay(if (status is ModelStore.Status.Downloading) 600 else 2000)
        }
    }
    return status
}

/** Floating glass navigation bar with a raised "create" orb in the middle. */
@Composable
fun BottomNav(current: Screen) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().height(70.dp).glass(RoundedCornerShape(35.dp), strong = true).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Home, tr("בית"), current == Screen.Home, "nav_home") { AppState.navigateRoot(Screen.Home) }
            NavItem(Icons.Rounded.Add, tr("יצירה"), current == Screen.Make, "nav_make") { AppState.navigateRoot(Screen.Make) }
            NavItem(Icons.Rounded.GridView, tr("הפרויקטים"), current == Screen.Projects, "nav_projects") { AppState.navigateRoot(Screen.Projects) }
            val errors = remember(com.triposr.mobile.Health.tick.intValue) { com.triposr.mobile.Health.errorCount() }
            NavItem(Icons.Rounded.MoreHoriz, tr("עוד"), current == Screen.More, "nav_more", dot = errors > 0) { AppState.navigateRoot(Screen.More) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, mark: String, dot: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.tourTarget(mark).clip(RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, label, tint = if (selected) C.Violet else C.Faint, modifier = Modifier.size(24.dp))
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(C.Red))
        }
        Text(label, color = if (selected) C.Text else C.Faint, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape)
            .background(if (selected) C.Violet else Color.Transparent))
    }
}

@Composable
fun Logo(size: Int = 56) {
    Box(
        Modifier.size(size.dp).shadow(16.dp, RoundedCornerShape((size / 3.2).dp), ambientColor = C.Purple, spotColor = C.Purple)
            .clip(RoundedCornerShape((size / 3.2).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6D5BFF), C.Magenta, Color(0xFF52D6FF))))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape((size / 3.2).dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.ViewInAr, null, tint = Color.White, modifier = Modifier.size((size * 0.56).dp)) }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    val npuStatus = rememberModelStatus(ModelStore.NPU)
    LaunchedEffect(status, npuStatus) {
        if (status is ModelStore.Status.Ready && npuStatus !is ModelStore.Status.Downloading && !Capabilities.probed(ctx)) startProbe(ctx)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(tr("מה ניצור היום?"), color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        CrashCard()
        AppState.notice?.let { NoticeCard(it) { AppState.notice = null } }
        UpdateCard()
        if (status !is ModelStore.Status.Ready) ModelCard(status)

        // A model being made, or one that just finished, stays one tap away.
        val job = AppState.job
        if (job != null && AppState.isRunning) FeatureCard(Icons.Rounded.ViewInAr, tr("יוצר מודל…"), job.status,
            onClick = { AppState.navigate(Screen.Processing) }) {
            PreviewPanel(AppState.framed ?: AppState.input, job.overall, "${(job.overall * 100).toInt()}%")
        } else if (job?.done == true && AppState.mesh != null) FeatureCard(Icons.Rounded.CheckCircle, tr("המודל מוכן!"), tr("לחץ לצפייה"),
            onClick = { AppState.showResult() }) {
            PreviewPanel(AppState.framed, 1f, "100%")
        }

        PhotoHero(Modifier.tourTarget("hero"))
        // Search and the full list live under "Create"; home stays four big doors.
        Column(Modifier.tourTarget("cards"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pair in Category.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in pair) CategoryCard(c, Modifier.weight(1f))
            }
        }
        run {
            if (AppState.projects.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("המשך מאיפה שעצרת"), color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(tr("הכל"), color = C.Violet, fontSize = 14.sp, modifier = Modifier.pressable { AppState.navigateRoot(Screen.Projects) })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().tourTarget("recent")) {
                    items(AppState.projects.take(10), key = { it.id }) { p -> ProjectThumb(p, Modifier.width(132.dp)) }
                }
                val keep = Settings.keepProjects(ctx)
                Text(if (keep > 0) tr("נשמרים {0} הפרויקטים האחרונים. ישנים יותר נמחקים לבד, חוץ מאלה שסימנת בלב. אפשר לשנות בהגדרות.", keep)
                    else tr("כל הפרויקטים נשמרים. אפשר לשנות בהגדרות."), color = C.Faint, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One of the four doors on the home screen: icon, name and how many things are inside. */
@Composable
private fun CategoryCard(c: Category, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(24.dp)).pressable { AppState.openCategory(c.name) }.padding(16.dp)) {
        IconTile(c.icon, 46.dp, if (c.warm) C.Primary else C.Accent)
        Spacer(Modifier.height(14.dp))
        Text(c.label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(tr("{0} יצירות", Catalog.entries(c).size), color = C.Sub, fontSize = 13.sp)
    }
}

@Composable
private fun processorSummary(ctx: android.content.Context): String {
    if (ProbeUi.running) return tr("בודק את המעבדים…")
    if (!Capabilities.probed(ctx)) return tr("ייבדק אחרי הורדת המודל")
    return tr("פועל על {0}", Capabilities.best(ctx).label)
}

@Composable
private fun StatusPill(status: ModelStore.Status, modifier: Modifier) {
    val (text, color) = when (status) {
        ModelStore.Status.Ready -> (if (ProbeUi.running) tr("בודק את המעבדים…") else tr("המודל מוכן · עובד בלי אינטרנט")) to C.Green
        is ModelStore.Status.Downloading -> tr("מוריד את המודל {0}%", (status.done * 100 / status.total)) to C.Violet
        ModelStore.Status.Missing -> tr("צריך להוריד את המודל פעם אחת") to C.Amber
        is ModelStore.Status.Failed -> tr("הורדת המודל נכשלה") to C.Red
    }
    Row(modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = C.Text, fontSize = 13.sp)
    }
}

/** Card in the style of the reference: icon tile + title on one side, a preview panel on the other. */
@Composable
private fun FeatureCard(
    icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier,
    onClick: () -> Unit, preview: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().height(124.dp).glass(RoundedCornerShape(26.dp)).pressable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.9f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, 46.dp)
            Column {
                Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = C.Sub, fontSize = 12.sp, maxLines = 1)
            }
        }
        Box(Modifier.weight(1.1f).fillMaxSize().clip(RoundedCornerShape(18.dp)).background(C.Sink)
            .border(1.dp, C.veil(0x14), RoundedCornerShape(18.dp))) { preview() }
    }
}

@Composable
private fun PreviewPanel(image: android.graphics.Bitmap?, progress: Float, label: String) {
    Box(Modifier.fillMaxSize()) {
        if (image != null) Image(image.asImageBitmap(), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.92f))
        else Icon(Icons.Rounded.AddPhotoAlternate, null, tint = C.Faint, modifier = Modifier.size(36.dp).align(Alignment.Center))
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0B0720)))).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowBar(progress, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(label, color = C.Text, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ThumbStack(projects: List<Project>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (projects.isEmpty()) Text(tr("עוד אין מודלים"), color = C.Faint, fontSize = 12.sp)
        projects.reversed().forEachIndexed { idx, p ->
            val k = projects.size - 1 - idx
            val bmp = rememberThumb(p)
            Box(Modifier.offset(x = (k * 22 - 22).dp).size(64.dp).shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).background(C.CardHigh).border(1.dp, C.veil(0x33), RoundedCornerShape(16.dp))) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ProcessorPanel() {
    val ctx = LocalContext.current
    val results = remember(ProbeUi.tick, ProbeUi.running) { Capabilities.results(ctx) }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for (b in Backend.entries) {
            val s = results[b]?.support ?: Support.UNKNOWN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(when (s) {
                    Support.OK -> C.Green; Support.UNKNOWN -> C.Faint; else -> C.Red
                }))
                Spacer(Modifier.width(8.dp))
                Text(b.short, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                Text(s.label, color = C.Sub, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun rememberThumb(p: Project): android.graphics.Bitmap? {
    val ctx = LocalContext.current
    return remember(p.id) { p.thumb(ctx) }
}

@Composable
fun ProjectThumb(p: Project, modifier: Modifier = Modifier) {
    val bmp = rememberThumb(p)
    Column(modifier.glass(RoundedCornerShape(22.dp)).pressable { AppState.open(p) }.padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(C.Sink)) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            HeartButton(p, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp))
        Text(p.date, color = C.Faint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

/** Like / unlike a project. Liked projects are kept for good. */
@Composable
fun HeartButton(p: Project, modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).clip(CircleShape).background(Color(0xAA120B2A)).border(1.dp, Color(0x33FFFFFF), CircleShape)
        .pressable { AppState.toggleFavorite(p) }, contentAlignment = Alignment.Center) {
        Icon(if (p.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tr(if (p.favorite) "הסר מאהבתי" else "אהבתי"),
            tint = if (p.favorite) Color(0xFFFF5C8A) else Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Section {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Warning, null, tint = C.Amber)
            Spacer(Modifier.width(10.dp))
            Text(text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, tr("סגור"), tint = C.Sub, modifier = Modifier.pressable(onClick = onDismiss))
        }
    }
}

@Composable
fun CrashCard() {
    // Store users don't get a crash banner: the report waits quietly in Settings, Support.
    if (BuildConfig.STORE) return
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pendingReport) }
    val r = report ?: return
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BugReport, null, tint = C.Red)
            Spacer(Modifier.width(10.dp))
            Text(tr("האפליקציה נסגרה בפעם הקודמת בגלל תקלה"), color = C.Text, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
        }
        Text(tr("העתק את הדוח והדבק אותו בצ'אט כדי שאוכל לתקן."), color = C.Sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton(tr("העתק דוח"), Modifier.weight(1f), icon = Icons.Rounded.ContentCopy) {
                Actions.copy(ctx, r, tr("דוח הקריסה"))
            }
            SoftButton(tr("סגור"), Modifier.weight(0.6f)) { CrashLog.dismissPending(); report = null }
        }
    }
}

object UpdateFlow {
    var downloadId by mutableStateOf<Long?>(null)
    var progress by mutableFloatStateOf(0f)
}

@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val u = AppState.update ?: return
    val id = UpdateFlow.downloadId
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        while (true) {
            UpdateFlow.progress = Updater.progress(ctx, id)
            if (UpdateFlow.progress >= 1f) { Updater.install(ctx, u); break }
            if (UpdateFlow.progress < 0f) break
            delay(500)
        }
    }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.SystemUpdate, 40.dp, Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6))))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("גרסה חדשה זמינה"), color = C.Text, fontWeight = FontWeight.Bold)
                Text(tr("build {0} · {1} MB · המודל לא יורד שוב", u.build, u.sizeMb), color = C.Sub, fontSize = 12.sp)
            }
        }
        when {
            id == null -> GradientButton(tr("עדכן עכשיו"), Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                UpdateFlow.downloadId = Updater.download(ctx, u)
            }
            UpdateFlow.progress < 0f -> SoftButton(tr("ההורדה נכשלה, נסה שוב"), Modifier.fillMaxWidth()) { UpdateFlow.downloadId = null }
            UpdateFlow.progress >= 1f -> GradientButton(tr("התקן"), Modifier.fillMaxWidth()) { Updater.install(ctx, u) }
            else -> GlowBar(UpdateFlow.progress, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ModelCard(status: ModelStore.Status, compact: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun startDownload() {
        error = null
        scope.launch {
            runCatching { ModelStore.startDownload(ctx) }
                .onFailure { CrashLog.error("model download", it); error = tr("לא הצלחתי להתחיל הורדה: {0}", it.message) }
        }
    }
    when (status) {
        ModelStore.Status.Ready -> if (compact) Unit
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("המודל מותקן · {0} MB", ModelStore.sizeOnDisk(ctx) shr 20), color = C.Green)
            }
        ModelStore.Status.Missing -> Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.CloudDownload, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("הורדת מודל ה-AI"), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(tr("פעם אחת, כ-1.6GB. ממשיך ברקע, מומלץ ב-Wi-Fi"), color = C.Sub, fontSize = 12.sp)
                }
            }
            GradientButton(tr("הורד מודל"), Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) { startDownload() }
            error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
        }
        is ModelStore.Status.Downloading -> Section {
            val pct = status.done.toFloat() / status.total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("מוריד את המודל"), color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GradientText("${(pct * 100).toInt()}%", 20.sp, brush = C.Primary)
            }
            GlowBar(pct, Modifier.fillMaxWidth(), C.Primary)
            Text(tr("{0} / {1} MB · אפשר לצאת מהאפליקציה", status.done shr 20, status.total shr 20), color = C.Sub, fontSize = 12.sp)
            SoftButton(tr("בטל הורדה"), Modifier.fillMaxWidth()) { ModelStore.cancel(ctx) }
        }
        is ModelStore.Status.Failed -> Section(title = tr("הורדת המודל נכשלה")) {
            Text(status.message, color = C.Red, fontSize = 13.sp)
            GradientButton(tr("נסה שוב"), Modifier.fillMaxWidth()) { startDownload() }
        }
    }
}

@Composable
fun CreateScreen(onGallery: () -> Unit, onCamera: () -> Unit, onFiles: () -> Unit) {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    var backend by remember { mutableStateOf(Settings.backendChoice(ctx)) }
    var quality by remember { mutableStateOf(Settings.quality(ctx)) }
    var removeBg by remember { mutableStateOf(Settings.removeBackground(ctx)) }
    var smooth by remember { mutableStateOf(Settings.smooth(ctx)) }
    var generator by remember { mutableStateOf(Settings.generator(ctx)) }
    val generators = remember { Generator.available(ctx) }
    var sourceMenu by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var tapMode by remember { mutableStateOf(false) }
    val tapAvailable = rememberModelStatus(Extras.TAP.pack) is ModelStore.Status.Ready
    val image = AppState.input
    val preview = AppState.preview?.takeIf { it.source === image }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("תמונה לתלת־ממד"), onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.enter(0)) {
                Box(
                    Modifier.fillMaxWidth().height(280.dp).glass(RoundedCornerShape(28.dp))
                        .then(if (tapMode && image != null) Modifier else Modifier.pressable { sourceMenu = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null && tapMode && preview != null) {
                        // Tap-to-select: taps land on the work image; outside the selection is dimmed.
                        val shown = preview.selection ?: preview.work
                        Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(14.dp).pointerInput(shown) {
                                detectTapGestures { o ->
                                    val sc = minOf(size.width / shown.width.toFloat(), size.height / shown.height.toFloat())
                                    val x = (o.x - (size.width - shown.width * sc) / 2) / sc
                                    val y = (o.y - (size.height - shown.height * sc) / 2) / sc
                                    if (x in 0f..shown.width.toFloat() && y in 0f..shown.height.toFloat()) AppState.tap(x, y)
                                }
                            })
                        if (AppState.tapPoints.isEmpty() && !AppState.previewBusy) Text(tr("גע באובייקט שתרצה"), color = C.Text, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).glass(CircleShape, strong = true)
                                .padding(horizontal = 12.dp, vertical = 6.dp))
                        if (AppState.previewBusy) Row(Modifier.align(Alignment.Center).glass(CircleShape, strong = true)
                            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("בוחר…"), color = C.Text, fontSize = 12.sp)
                        }
                    } else if (image != null) {
                        // By default show what the model will get: the cut-out framed on grey.
                        val shown = if (showOriginal || preview == null) image else preview.framed
                        Image(shown.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                        if (AppState.previewBusy) Row(Modifier.align(Alignment.Center).glass(CircleShape, strong = true)
                            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(if (removeBg) tr("מסיר רקע…") else tr("מכין תצוגה…"), color = C.Text, fontSize = 12.sp)
                        }
                        if (preview != null) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp).glass(CircleShape, strong = true)
                            .padding(3.dp)) {
                            for ((label, original) in listOf(tr("מה שהמודל רואה") to false, tr("מקור") to true)) {
                                val on = showOriginal == original
                                Text(label, color = if (on) Color.White else C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(CircleShape).then(if (on) Modifier.background(C.Primary) else Modifier)
                                        .clickable { showOriginal = original }.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                        Row(Modifier.align(Alignment.TopStart).padding(12.dp).glass(CircleShape, strong = true)
                            .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = C.Text, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("החלף"), color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(Icons.Rounded.AddPhotoAlternate, 64.dp, C.Primary)
                        Spacer(Modifier.height(14.dp))
                        Text(tr("העלה תמונה"), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(tr("גלריה · מצלמה · קבצים"), color = C.Sub, fontSize = 13.sp)
                    }
                }
                DropdownMenu(sourceMenu, { sourceMenu = false }, containerColor = C.CardHigh,
                    shape = RoundedCornerShape(18.dp)) {
                    SourceItem(Icons.Rounded.PhotoLibrary, tr("גלריה")) { sourceMenu = false; onGallery() }
                    SourceItem(Icons.Rounded.PhotoCamera, tr("מצלמה")) { sourceMenu = false; onCamera() }
                    SourceItem(Icons.Rounded.FolderOpen, tr("קבצים")) { sourceMenu = false; onFiles() }
                }
            }

            if (tapAvailable && image != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(if (tapMode) tr("סיום בחירה") else tr("בחר אובייקט בנגיעה"), Modifier.weight(1f), Icons.Rounded.TouchApp) {
                    tapMode = !tapMode
                }
                if (AppState.tapPoints.isNotEmpty()) SoftButton(tr("נקה"), Modifier.weight(0.5f)) { AppState.clearTaps() }
            }
            preview?.issues?.forEach { IssueCard(it) }

            Row(Modifier.horizontalScroll(rememberScrollState()).enter(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tip in listOf(tr("אובייקט אחד במרכז"), tr("תאורה טובה"), tr("רקע פשוט"), tr("האובייקט שלם"), tr("זווית מעט מלמעלה"))) {
                    Chip(tip, C.Sub)
                }
            }

            if (generators.size > 1) Section(Modifier.enter(2), title = tr("מודל")) {
                GeneratorCards(generators, generator) { generator = it; Settings.setGenerator(ctx, it) }
                Text(generator.hint, color = C.Sub, fontSize = 12.sp)
            }
            if (generator == Generator.TRIPOSR) {
                Section(Modifier.enter(2), title = tr("מעבד")) {
                    BackendPicker(backend) { backend = it; Settings.setBackendChoice(ctx, it) }
                }
                Section(Modifier.enter(3), title = tr("רמת פירוט")) {
                    QualityCards(quality) { quality = it; Settings.setQuality(ctx, it) }
                    Text("${quality.resolution}³ · ${quality.hint}", color = C.Sub, fontSize = 12.sp)
                }
            }
            Section(Modifier.enter(4)) {
                ToggleRow(tr("הסרת רקע אוטומטית"), tr("מומלץ, אלא אם התמונה כבר בלי רקע"), removeBg) {
                    removeBg = it; Settings.setRemoveBackground(ctx, it); AppState.refreshPreview(it)
                }
                ToggleRow(tr("החלקת משטח"), tr("מסיר מדרגות קטנות ושומר על הצורה"), smooth) {
                    smooth = it; Settings.setSmooth(ctx, it)
                }
            }
            AdvancedOptions(smooth, generator)
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            val modelReady = status is ModelStore.Status.Ready || generator == Generator.TRIPOSG
            if (!modelReady) {
                Text(tr("קודם צריך להוריד את המודל (במסך הבית)"), color = C.Amber, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center)
            }
            GradientButton(tr("צור מודל 3D"), Modifier.fillMaxWidth(), sparkles = true,
                enabled = image != null && modelReady && !AppState.isRunning) {
                AppState.start(backend, quality, removeBg, smooth)
            }
        }
    }
}

/**
 * Settings that measurably change the finish (tested on real reconstructions), hidden until
 * opened. Object ratio, grey level and mask threshold were tested too and only distorted the
 * result, so they stay at TripoSR's values.
 */
@Composable
private fun AdvancedOptions(smoothOn: Boolean, generator: Generator) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var steps by remember { mutableFloatStateOf(Settings.sgSteps(ctx).toFloat()) }
    var smartGuidance by remember { mutableStateOf(Settings.sgGuidanceInterval(ctx)) }
    var strength by remember { mutableFloatStateOf(Settings.smoothStrength(ctx).toFloat()) }
    var density by remember { mutableFloatStateOf(Settings.densityThreshold(ctx)) }
    Section {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, tint = C.Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("הגדרות מתקדמות"), color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = C.Sub)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("עוצמת החלקה: {0}", strength.roundToInt()) + if (strength.roundToInt() == Settings.DEFAULT_SMOOTH) tr(" (ברירת מחדל)") else "",
                    color = C.Text, fontSize = 14.sp)
                Slider(strength, { strength = it }, valueRange = 1f..10f, enabled = smoothOn,
                    onValueChangeFinished = { Settings.setSmoothStrength(ctx, strength.roundToInt()) }, colors = sliderColors())
                Text(if (smoothOn) tr("גבוה מעלים את \"הטבעות\" על המשטח, שמודפסות כפסים. נמוך שומר על פרטים קטנים.")
                    else tr("מופעל רק כשהחלקת משטח דולקת."), color = C.Sub, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                if (generator == Generator.TRIPOSG) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((n, label) in listOf(Settings.DEFAULT_SG_STEPS to tr("רגיל · כ-1.5 דקות"), 25 to tr("איכות גבוהה · כ-3.5 דקות"))) {
                            val on = steps.roundToInt() == n
                            Text(label, color = if (on) Color.White else C.Sub, fontSize = 13.sp,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                    .background(if (on) C.Violet else C.veil(0x14))
                                    .pressable { steps = n.toFloat(); Settings.setSgSteps(ctx, n) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp), textAlign = TextAlign.Center)
                        }
                    }
                    Text(tr("צעדי בנייה: {0}", steps.roundToInt()) + if (steps.roundToInt() == Settings.DEFAULT_SG_STEPS) tr(" (ברירת מחדל)") else "",
                        color = C.Text, fontSize = 14.sp)
                    Slider(steps, { steps = it.roundToInt().toFloat() }, valueRange = 10f..50f,
                        onValueChangeFinished = { Settings.setSgSteps(ctx, steps.roundToInt()) }, colors = sliderColors())
                    Text(tr("יותר צעדים: צורה נקייה יותר אבל לאט יותר. הזמן עולה בערך ביחס ישר."), color = C.Sub, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    ToggleRow(tr("הכוונה חכמה (ניסיוני)"),
                        tr("מדלג על חישוב ההשוואה בחלק מהצעדים: כ-15% מהר יותר, הצורה כמעט זהה (98–99%)."),
                        smartGuidance) { smartGuidance = it; Settings.setSgGuidanceInterval(ctx, it) }
                } else {
                    Text(tr("עובי ומילוי: %.0f").format(density) + if (density == TripoEngine.DENSITY_THRESHOLD) tr(" (ברירת מחדל)") else "",
                        color = C.Text, fontSize = 14.sp)
                    Slider(density, { density = it.roundToInt().toFloat() }, valueRange = 5f..50f,
                        onValueChangeFinished = { Settings.setDensityThreshold(ctx, density) }, colors = sliderColors())
                    Text(tr("נמוך: מודל מלא יותר וחלקים דקים עבים יותר, טוב להדפסה. גבוה: מודל רזה וחד יותר."),
                        color = C.Sub, fontSize = 12.sp)
                }
                SoftButton(tr("אפס לברירת מחדל"), Modifier.fillMaxWidth()) {
                    Settings.resetAdvanced(ctx)
                    strength = Settings.DEFAULT_SMOOTH.toFloat(); density = TripoEngine.DENSITY_THRESHOLD
                    steps = Settings.DEFAULT_SG_STEPS.toFloat()
                }
            }
        }
    }
}

/** A non-blocking warning about the chosen image. */
@Composable
fun IssueCard(issue: ImageIssue) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Warning, null, tint = C.Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(issue.title, color = C.Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(issue.detail, color = C.Sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SourceItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem({ Text(label, color = C.Text, fontSize = 15.sp) }, onClick,
        leadingIcon = { Icon(icon, null, tint = C.Violet) })
}

@Composable
fun ProcessingScreen() {
    val ctx = LocalContext.current
    val job = AppState.job
    if (job == null) {
        LaunchedEffect(Unit) { AppState.back() }
        return
    }
    val img = AppState.framed ?: AppState.input
    val animate = LocalAnimations.current
    val scan = if (animate && job.error == null) rememberInfiniteTransition(label = "scan")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "s").value else 0.5f

    Column(Modifier.fillMaxSize()) {
        TopBar(if (job.error != null) tr("משהו השתבש") else tr("יוצר את המודל שלך"), onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(if (job.error != null) 0f else job.overall, Modifier.size(250.dp).enter(0), stroke = 9.dp) {
                Box(Modifier.size(186.dp).clip(CircleShape).background(C.Sink), contentAlignment = Alignment.TopCenter) {
                    if (img != null) Image(img.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (job.error == null) Box(Modifier.fillMaxWidth().height(3.dp).offset(y = (186 * scan).dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, C.Violet, C.Orange, Color.Transparent))))
                }
            }

            if (job.error != null) {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = C.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("היצירה נכשלה"), color = C.Text, fontWeight = FontWeight.Bold)
                    }
                    Text(job.error, color = C.Sub, fontSize = 14.sp)
                    GradientButton(tr("העתק פרטי שגיאה"), Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentCopy) {
                        Actions.copy(ctx, CrashLog.lastError ?: CrashLog.diagnostics(), tr("פרטי השגיאה"))
                    }
                    if (job.backend != Backend.CPU) {
                        val next = Backend.entries[job.backend.ordinal + 1]
                        SoftButton(tr("נסה שוב עם {0}", next.short), Modifier.fillMaxWidth()) {
                            Settings.setBackendChoice(ctx, next)
                            AppState.back()
                            AppState.start(next, Settings.quality(ctx), Settings.removeBackground(ctx), Settings.smooth(ctx))
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.enter(1)) {
                    GradientText("${(job.overall * 100).toInt()}%", 46.sp, brush = C.Primary)
                    Text(job.status, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    val sec = if (LocalAnimations.current) rememberElapsed(job.startedAt) else 0L
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(tr("רץ על {0}", job.backend.label), C.Violet)
                        Chip("%d:%02d".format(sec / 60, sec % 60), C.Sub)
                    }
                }
            }

            Section(Modifier.enter(2)) {
                for (s in job.plan) {
                    val st = when {
                        job.done || s.ordinal < job.stage.ordinal -> StepState.DONE
                        s == job.stage && job.error == null -> StepState.ACTIVE
                        else -> StepState.PENDING
                    }
                    StepRow(s.label, st, if (st == StepState.ACTIVE && s.ordinal >= Stage.DENOISE.ordinal)
                        "${(job.stageProgress * 100).toInt()}%" else null)
                }
            }
            if (job.error == null) Text(tr("אפשר לצאת מהאפליקציה: העבודה ממשיכה ברקע ותקבל התראה כשהמודל מוכן."),
                color = C.Faint, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (job.error == null) SoftButton(tr("ביטול"), Modifier.fillMaxWidth()) { AppState.cancel() }
            else SoftButton(tr("חזרה"), Modifier.fillMaxWidth()) { AppState.job = null; AppState.back() }
        }
    }
}

object ProbeUi {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")
    var tick by mutableIntStateOf(0)
}

/** Runs the device test once in the background (after the model is downloaded). */
fun startProbe(ctx: android.content.Context) {
    if (ProbeUi.running || AppState.isRunning || !ModelStore.isReady(ctx)) return
    ProbeUi.running = true
    AppState.scope.launch {
        runCatching { Capabilities.probe(ctx) { ProbeUi.status = it } }
            .onFailure { CrashLog.error("device test", it) }
        ProbeUi.running = false
        ProbeUi.tick++
    }
}

@Composable
fun BackendPicker(choice: Backend?, onChoice: (Backend?) -> Unit) {
    val ctx = LocalContext.current
    val results = remember(ProbeUi.tick, ProbeUi.running) { Capabilities.results(ctx) }
    val options = listOf<Backend?>(null) + Backend.entries
    ChoiceCards(options, choice, { it?.short ?: tr("אוטומטי") }, onChoice, columns = 4,
        subtitle = { b -> if (b == null) tr("המהיר") else results[b]?.support?.label ?: tr("לא נבדק") },
        icon = { b -> when (b) { null -> Icons.Rounded.AutoAwesome; Backend.NPU -> Icons.Rounded.Bolt
            Backend.GPU -> Icons.Rounded.ViewInAr; Backend.CPU -> Icons.Rounded.Memory } },
        disabled = { it != null && results[it]?.support in listOf(Support.UNSUPPORTED, Support.FAILED, Support.CRASHED) })
    if (choice == null) {
        Text(if (Capabilities.probed(ctx)) tr("ייבחר אוטומטית: {0}, המהיר שעבד בבדיקה", Capabilities.best(ctx).label)
            else tr("בפעם הראשונה האפליקציה תבדוק אילו מעבדים עובדים ותבחר את המהיר"), color = C.Sub, fontSize = 12.sp)
    }
    if (ProbeUi.running) Text(ProbeUi.status.ifEmpty { tr("בודק את המעבדים…") }, color = C.Violet, fontSize = 12.sp)
    if (choice != null && results[choice]?.support != Support.OK && results[choice]?.support != Support.UNKNOWN) {
        Text(tr("{0} לא עובד בטלפון הזה, היצירה עלולה להיכשל. מומלץ אוטומטי.", choice.short), color = C.Amber, fontSize = 12.sp)
    }
}

/**
 * Offers the NPU pack (models pre-compiled for this phone's Snapdragon) in Settings once the main
 * model is installed. Shows nothing on chips without a pack or when the pack isn't published.
 */
@Composable
fun NpuPackCard(status: ModelStore.Status) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (com.triposr.mobile.NpuPack.chip() == null) return
    var size by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        size = runCatching { ModelStore.availableSize(ModelStore.NPU) }.getOrNull()
    }
    when (status) {
        ModelStore.Status.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("חבילת NPU מותקנת · {0} MB", ModelStore.sizeOnDisk(ctx, ModelStore.NPU) shr 20), color = C.Green)
        }
        is ModelStore.Status.Downloading -> Section {
            val pct = status.done.toFloat() / status.total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("מוריד חבילת NPU"), color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GradientText("${(pct * 100).toInt()}%", 20.sp, brush = C.Primary)
            }
            GlowBar(pct, Modifier.fillMaxWidth(), C.Primary)
            SoftButton(tr("בטל"), Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, ModelStore.NPU) }
        }
        else -> {
            val s = size
            if (s == null) return
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Memory, 44.dp, C.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("האצה עם ה-NPU"), color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(tr("מודל שהוכן מראש לשבב של הטלפון · {0} MB", s shr 20), color = C.Sub, fontSize = 12.sp)
                    }
                }
                if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 13.sp)
                GradientButton(tr("הורד חבילת NPU"), Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                    error = null
                    scope.launch {
                        runCatching { ModelStore.startDownload(ctx, ModelStore.NPU) }
                            .onFailure { CrashLog.error("NPU pack download", it); error = it.message }
                    }
                }
                error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            }
        }
    }
}

/** Detail level as cards with a speed hint. */
@Composable
fun QualityCards(selected: Quality, onSelect: (Quality) -> Unit) {
    ChoiceCards(Quality.entries.toList(), selected, { it.label }, onSelect, columns = 4,
        subtitle = { "${it.resolution}³" },
        icon = { when (it) { Quality.LOW -> Icons.Rounded.Bolt; Quality.MEDIUM -> Icons.Rounded.Tune
            Quality.HIGH -> Icons.Rounded.AutoAwesome; Quality.MAX -> Icons.Rounded.Star } })
}

/** Seconds since [start], ticking every second. */
@Composable
fun rememberElapsed(start: Long): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return (now - start) / 1000
}
