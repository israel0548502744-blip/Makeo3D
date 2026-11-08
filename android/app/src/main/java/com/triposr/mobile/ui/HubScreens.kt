package com.triposr.mobile.ui

import com.triposr.mobile.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.Backend
import com.triposr.mobile.Capabilities
import com.triposr.mobile.Creations
import com.triposr.mobile.Extras
import com.triposr.mobile.Generator
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Support

fun Creations.Kind.icon(): ImageVector = when (this) {
    Creations.Kind.LITHOPHANE -> Icons.Rounded.Lightbulb
    Creations.Kind.RELIEF -> Icons.Rounded.Terrain
    Creations.Kind.QR -> Icons.Rounded.QrCode2
    Creations.Kind.NAME -> Icons.Rounded.Key
    Creations.Kind.COOKIE -> Icons.Rounded.Cookie
    Creations.Kind.STAMP -> Icons.Rounded.Approval
}

fun Creations.Kind.blurb(): String = when (this) {
    Creations.Kind.LITHOPHANE -> tr("תמונה שנגלית באור")
    Creations.Kind.RELIEF -> tr("תמונה בולטת בצבע")
    Creations.Kind.QR -> tr("קישור, Wi‑Fi, וואטסאפ")
    Creations.Kind.NAME -> tr("שם עם חור לטבעת")
    Creations.Kind.COOKIE -> tr("מציור או מתמונה")
    Creations.Kind.STAMP -> tr("לבצק, חימר ושעווה")
}

/**
 * Bottom sheet opened by the + button: the big "photo to 3D" action on top, then every
 * printable template as a tile. Drawn inside the app's own layout (not a popup window) so it
 * slides over the bottom bar and shows up in screenshot tests.
 */
@Composable
fun CreateMenu() {
    val open = AppState.createMenu
    AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color(0x99050314))
            .clickable(remember { MutableInteractionSource() }, indication = null) { AppState.createMenu = false })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(open, enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))) {
            CreateMenuContent()
        }
    }
}

@Composable
fun CreateMenuContent() {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF241A4D), Color(0xFF120C2C))))
            .border(1.dp, C.GlassEdge, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape)
            .background(Color(0x40FFFFFF)))
        Text(tr("מה ניצור היום?"), color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.Primary)
                .pressable { AppState.createMenu = false; AppState.startNew() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ViewInAr, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("תמונה לתלת־ממד"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(tr("מודל מלא מכל זווית, מתמונה אחת"), color = Color(0xE6FFFFFF), fontSize = 12.sp)
            }
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White)
        }
        Text(tr("יצירות להדפסה"), color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        for (row in Creations.Kind.entries.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in row) Column(
                    Modifier.weight(1f).glass(RoundedCornerShape(20.dp)).pressable { AppState.openExtra(k) }
                        .padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconTile(k.icon(), 40.dp, if (k.ordinal % 2 == 0) C.Accent else C.Primary)
                    Spacer(Modifier.height(8.dp))
                    Text(k.label, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(k.blurb(), color = C.Faint, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Everything about the AI models in one place: what is installed, what can be added, storage. */
@Composable
fun ModelsScreen() {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    val npu = rememberModelStatus(ModelStore.NPU)
    val sg = rememberModelStatus(ModelStore.TRIPOSG)
    var confirmDelete by remember { mutableStateOf(false) }
    var crashTick by remember { mutableIntStateOf(0) }
    val packs = listOf(ModelStore.MAIN, ModelStore.NPU, ModelStore.TRIPOSG) + Extras.all.map { it.pack }
    val usedMb = remember(status, npu, sg) { packs.sumOf { runCatching { ModelStore.sizeOnDisk(ctx, it) }.getOrDefault(0L) } shr 20 }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("מודלים"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().glass().padding(16.dp).enter(0), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Storage, 44.dp, C.Primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("נפח בשימוש"), color = C.Sub, fontSize = 12.sp)
                    GradientText(if (usedMb >= 1024) "%.1f GB".format(usedMb / 1024f) else "${usedMb} MB", 24.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(tr("{0} מותקנים", packs.count { ModelStore.isReady(ctx, it) }), color = C.Text, fontSize = 13.sp)
                    Text(tr("הכל עובד בלי אינטרנט"), color = C.Faint, fontSize = 11.sp)
                }
            }

            val generators = remember(status, sg) { Generator.available(ctx) }
            if (generators.size > 1) Section(Modifier.enter(1), title = tr("מודל ברירת מחדל ליצירה")) {
                var g by remember { mutableStateOf(Settings.generator(ctx)) }
                GeneratorCards(generators, g) { g = it; Settings.setGenerator(ctx, it) }
            }

            Section(Modifier.enter(2), title = tr("TripoSR · המודל הראשי")) {
                Text(tr("תמונה לתלת־ממד עם צבעים. עובד על כל טלפון."), color = C.Sub, fontSize = 12.sp)
                ModelCard(status, compact = false)
                if (status is ModelStore.Status.Ready) NpuPackCard(npu)
                if (status is ModelStore.Status.Ready && !AppState.isRunning) SoftButton(tr("מחק · {0} MB", ModelStore.sizeOnDisk(ctx) shr 20),
                    Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) { confirmDelete = true }
            }
            ModelPacksSection()
            ExtrasSection()

            Section(Modifier.enter(3), title = tr("המעבדים בטלפון")) {
                Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · ${android.os.Build.SOC_MODEL}" +
                    if (Capabilities.isSnapdragon()) " (Snapdragon)" else tr(" (לא Snapdragon — רק CPU)"), color = C.Text, fontSize = 13.sp)
                val results = remember(ProbeUi.tick, crashTick) { Capabilities.results(ctx) }
                for (r in results.values) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(when (r.support) {
                            Support.OK -> C.Green; Support.UNKNOWN -> C.Faint; else -> C.Orange
                        }))
                        Spacer(Modifier.width(10.dp))
                        Text(r.backend.label, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Chip(r.support.label + if (r.ms > 0) " · %.0f ms".format(r.ms) else "",
                            if (r.support == Support.OK) C.Green else if (r.support == Support.UNKNOWN) C.Sub else C.Orange)
                    }
                    if (r.detail.isNotEmpty() && r.support != Support.OK) Text(r.detail.take(160), color = C.Sub, fontSize = 11.sp)
                }
                if (ProbeUi.running) Text(ProbeUi.status, color = C.Violet, fontSize = 12.sp)
                SoftButton(if (ProbeUi.running) tr("בודק…") else tr("בדוק שוב את המעבדים"), Modifier.fillMaxWidth(), Icons.Rounded.Bolt,
                    enabled = !ProbeUi.running && status is ModelStore.Status.Ready) {
                    Backend.entries.forEach { AppState.clearCrashed(it) }
                    crashTick++
                    startProbe(ctx)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = C.CardHigh,
        title = { Text(tr("למחוק את המודל?"), color = C.Text) },
        text = { Text(tr("תצטרך להוריד אותו שוב (1.7GB) כדי ליצור מודלים חדשים. הפרויקטים השמורים נשארים."), color = C.Sub) },
        confirmButton = { TextButton({ AppState.resetEngine(); ModelStore.delete(ctx); Capabilities.reset(ctx); confirmDelete = false }) { Text(tr("מחק"), color = C.Red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(tr("ביטול"), color = C.Sub) } },
    )
}

@Composable
fun GeneratorCards(options: List<Generator>, selected: Generator, onSelect: (Generator) -> Unit) {
    ChoiceCards(options, selected, { it.label }, onSelect,
        subtitle = { when (it) { Generator.TRIPOSR -> tr("מהיר, עם צבעים"); Generator.TRIPOSG -> tr("צורה חדה ומדויקת") } },
        icon = { when (it) { Generator.TRIPOSR -> Icons.Rounded.Bolt; Generator.TRIPOSG -> Icons.Rounded.ViewInAr } },
        badge = { if (it == Generator.TRIPOSG) tr("ניסיוני") else null })
}

private val legalFiles get() = if (com.triposr.mobile.I18n.english)
    listOf("legal/terms_en.txt", "legal/privacy_en.txt", "third_party_licenses.txt")
else listOf("legal/terms_he.txt", "legal/privacy_he.txt", "third_party_licenses.txt")

/** Terms of use, privacy policy and open-source licenses, as tabs. */
@Composable
fun LegalScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(AppState.legalTab) }
    val text = remember(tab) {
        fun read(name: String) = runCatching { ctx.assets.open(name).bufferedReader().readText() }.getOrDefault("")
        // The QNN runtime's third-party notices go with it (Qualcomm's redistribution terms).
        read(legalFiles[tab]) + if (tab == 2) "\n\n==== Qualcomm AI Engine Direct (QNN) notices ====\n\n" + read("qnn_NOTICE.txt") else ""
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("משפטי"), onBack = { AppState.back() })
        Box(Modifier.padding(horizontal = 20.dp)) {
            Segmented(listOf(0, 1, 2), tab, { listOf(tr("תנאי שימוש"), tr("פרטיות"), tr("רישיונות"))[it] }, { tab = it })
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (tab == 2) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(text, color = C.Sub, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().glass().padding(14.dp))
            } else LegalText(text)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Plain-text legal document: first line is the title, numbered lines are headings. */
@Composable
private fun LegalText(text: String) {
    val lines = text.lines()
    Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, line ->
            when {
                i == 0 -> GradientText(line, 22.sp)
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.first().isDigit() -> Text(line, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
                line.startsWith("- ") -> Row {
                    Text("•", color = C.Violet, fontSize = 14.sp, modifier = Modifier.width(14.dp))
                    Text(line.removePrefix("- "), color = C.Sub, fontSize = 14.sp, lineHeight = 21.sp)
                }
                else -> Text(line, color = C.Sub, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** Shown once, before the first use: short summary of the terms with links to the full text. */
@Composable
fun TermsDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, containerColor = C.CardHigh,
        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = C.Violet) },
        title = { Text(tr("לפני שמתחילים"), color = C.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in listOf(
                    tr("התמונות שלך לא יוצאות מהטלפון: הכל מעובד במכשיר."),
                    tr("אתה אחראי למה שאתה יוצר. דמויות, לוגואים ומותגים (למשל של דיסני) שייכים לבעליהם: אל תמכור הדפסות שלהם בלי רשות."),
                    tr("המודלים נוצרים בבינה מלאכותית ועלולים להיות לא מדויקים. בדוק לפני הדפסה."),
                )) Row {
                    Text("•", color = C.Violet, modifier = Modifier.width(14.dp))
                    Text(line, color = C.Sub, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(tr("תנאי שימוש"), color = C.Violet, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { AppState.openLegal(0) })
                    Text(tr("מדיניות פרטיות"), color = C.Violet, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { AppState.openLegal(1) })
                }
            }
        },
        confirmButton = { TextButton(onAccept) { Text(tr("מסכים, בוא נתחיל"), color = C.Violet, fontWeight = FontWeight.Bold) } },
    )
}

/** Shown over everything when the public config switched this build off; it cannot be dismissed. */
@Composable
fun RetiredScreen(r: com.triposr.mobile.RemoteConfig.Retired) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {}, containerColor = C.CardHigh,
        icon = { Icon(Icons.Rounded.Bolt, null, tint = C.Violet) },
        title = { Text(tr("צריך לעדכן"), color = C.Text, fontWeight = FontWeight.Bold) },
        text = { Text(r.message, color = C.Sub, fontSize = 14.sp) },
        confirmButton = {
            if (r.url.isNotBlank()) TextButton({
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.url))) }
            }) { Text(tr("להתקנה"), color = C.Violet, fontWeight = FontWeight.Bold) }
        },
    )
}

/** True when this screen shows the bottom navigation bar. */
val Screen.isRoot: Boolean get() = this in listOf(Screen.Home, Screen.Projects, Screen.Models, Screen.Settings)
