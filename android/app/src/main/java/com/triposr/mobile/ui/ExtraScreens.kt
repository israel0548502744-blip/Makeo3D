package com.triposr.mobile.ui

import com.triposr.mobile.tr
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.SolidColor
import com.triposr.mobile.Templates
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.CrashLog
import com.triposr.mobile.Creations
import com.triposr.mobile.Enhance
import com.triposr.mobile.Extras
import com.triposr.mobile.Generator
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lithophane, relief and QR plates: printable products that don't depend on 3D reconstruction. */
@Composable
fun ExtraScreen() {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(AppState.extraKind) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var text by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var qrType by remember { mutableStateOf(Templates.QrType.LINK) }
    var frame by remember { mutableStateOf(true) }
    var invert by remember { mutableStateOf(false) }
    var maxMm by remember { mutableFloatStateOf(3f) }
    var reliefMm by remember { mutableFloatStateOf(8f) }
    var flatBg by remember { mutableStateOf(true) }
    var letterMm by remember { mutableFloatStateOf(12f) }
    var cookieMm by remember { mutableFloatStateOf(80f) }
    var stampMm by remember { mutableFloatStateOf(50f) }
    var keyring by remember { mutableStateOf(true) }
    var tagStyle by remember { mutableStateOf(Templates.TagStyle.OUTLINE) }
    var lamp by remember { mutableStateOf(false) }
    var lampMm by remember { mutableFloatStateOf(80f) }
    var stampImage by remember { mutableStateOf(false) }
    var moldMm by remember { mutableFloatStateOf(12f) }
    var moldMaster by remember { mutableStateOf(false) }
    var moldRelief by remember { mutableStateOf(false) }
    var wick by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf(1) }
    var alt by remember { mutableStateOf(false) }
    val sizes = remember { mutableStateMapOf<Creations.Kind, Float>() }
    val size = sizes[kind] ?: kind.def
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = Actions.loadBitmap(ctx, uri) ?: run { Actions.toast(ctx, tr("לא הצלחתי לפתוח את התמונה")); null }
    }
    val busy = Creations.busy
    val usesImage = kind.needsImage || (kind == Creations.Kind.STAMP && stampImage) || kind.need == Creations.Need.EITHER
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.Text, unfocusedTextColor = C.Text,
        focusedBorderColor = C.Violet, unfocusedBorderColor = C.Faint, focusedLabelColor = C.Violet, unfocusedLabelColor = C.Sub)

    @Composable
    fun Field(value: String, label: String, onChange: (String) -> Unit, type: KeyboardType = KeyboardType.Text) =
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = type), colors = fieldColors)

    Column(Modifier.fillMaxSize()) {
        TopBar(kind.label, onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChoiceChips(Creations.Kind.entries.toList(), kind, { it.label }, { kind = it; AppState.extraKind = it }, icon = { it.icon() })
            Text(when (kind) {
                Creations.Kind.LITHOPHANE -> tr("לוח דק (או מנורה גלילית) שהתמונה מופיעה בו כשמאירים מאחור. מודפס בלבן, הכי פשוט ותמיד מצליח.")
                Creations.Kind.RELIEF -> tr("התמונה בולטת מהלוח לפי העומק שלה, עם הצבעים שלה.")
                Creations.Kind.QR -> tr("לוח עם קוד QR בולט שנסרק בטלפון: קישור, רשת Wi‑Fi, וואטסאפ או טלפון. החלף פילמנט בגובה 2 מ״מ לשני צבעים.")
                Creations.Kind.NAME -> tr("שם או מילה בולטים על בסיס, עם חור לטבעת. החלף פילמנט בגובה 2.4 מ״מ כדי שהאותיות יצאו בצבע אחר.")
                Creations.Kind.COOKIE -> tr("חותכן עוגיות בצורה של הציור או האובייקט בתמונה. מומלץ ציור פשוט עם קו מתאר ברור. ") +
                    tr("הקובץ אינו אישור בטיחות למזון: הבטיחות תלויה בחומר, במדפסת ובניקוי.")
                Creations.Kind.STAMP -> tr("חותמת לעוגיות, בצק, חימר או שעווה. ההדפסה הפוכה (מראה), כך שהחותם יוצא נכון. ") +
                    tr("במגע עם מזון: הבטיחות תלויה בחומר, במדפסת ובניקוי.")
                Creations.Kind.MOLD -> tr("תבנית בצורה של האובייקט או הציור שבתמונה. מדפיסים ויוצקים פנימה שעוות נרות, סבון, גבס, בטון או שרף. ") +
                    tr("הדפנות משופעות כדי שהיציקה תשתחרר; כדאי למרוח חומר מפריד (שמן או וזלין). לשעווה חמה עדיף PETG, כי PLA מתרכך בחום. לא למזון.")
                else -> kind.about
            }, color = C.Sub, fontSize = 13.sp)

            when (kind) {
                Creations.Kind.QR -> {
                    ChoiceChips(Templates.QrType.entries.toList(), qrType, { it.label }, { qrType = it; text = ""; second = "" })
                    when (qrType) {
                        Templates.QrType.LINK -> Field(text, tr("קישור"), { text = it }, KeyboardType.Uri)
                        Templates.QrType.WIFI -> { Field(text, tr("שם הרשת"), { text = it }); Field(second, tr("סיסמה (ריק לרשת פתוחה)"), { second = it }, KeyboardType.Password) }
                        Templates.QrType.WHATSAPP -> { Field(text, tr("מספר טלפון"), { text = it }, KeyboardType.Phone); Field(second, tr("הודעה מוכנה (לא חובה)"), { second = it }) }
                        Templates.QrType.PHONE -> Field(text, tr("מספר טלפון"), { text = it }, KeyboardType.Phone)
                        Templates.QrType.TEXT -> Field(text, tr("טקסט"), { text = it })
                    }
                    Field(caption, tr("כיתוב מתחת לקוד (לא חובה)"), { caption = it })
                    val payload = Templates.qrPayload(qrType, text, second)
                    val qr = remember(payload) { runCatching { if (text.isBlank()) null else Creations.qrBitmap(payload, 6) }.getOrNull() }
                    Box(Modifier.fillMaxWidth().height(240.dp).glass(RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr.asImageBitmap(), null, Modifier.size(200.dp).clip(RoundedCornerShape(10.dp)))
                        else Text(tr("מלא את הפרטים"), color = C.Sub)
                    }
                    if (qrType == Templates.QrType.WIFI) Text(tr("הסיסמה נשמרת בתוך הקוד המודפס: כל מי שסורק אותו יכול לראות אותה."),
                        color = C.Amber, fontSize = 12.sp)
                }
                Creations.Kind.NAME -> Field(text, tr("שם או טקסט"), { text = it })
                Creations.Kind.STAMP -> {
                    ToggleRow(tr("מתמונה"), tr("ציור כהה על רקע לבן במקום טקסט"), stampImage) { stampImage = it }
                    if (!stampImage) Field(text, tr("טקסט לחותמת"), { text = it })
                }
                else -> {}
            }
            kind.textLabel?.let { label -> Field(text, label, { text = it }) }
            if (kind != Creations.Kind.QR) kind.secondLabel?.let { label -> Field(second, label, { second = it }, KeyboardType.Phone) }

            if (usesImage) {
                Box(Modifier.fillMaxWidth().height(260.dp).glass(RoundedCornerShape(26.dp))
                    .pressable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center) {
                    val img = image
                    if (img != null) Image(img.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(Icons.Rounded.AddPhotoAlternate, 56.dp, C.Primary)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("בחר תמונה"), color = C.Text, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (kind) {
                Creations.Kind.LITHOPHANE -> Section(title = tr("אפשרויות")) {
                    ToggleRow(tr("גלילי (מנורה)"), tr("התמונה מסביב לגליל. שמים בפנים נורת LED"), lamp) { lamp = it }
                    if (lamp) {
                        Text(tr("קוטר: %.0f מ״מ (הגובה לפי יחס התמונה)").format(lampMm), color = C.Text, fontSize = 14.sp)
                        Slider(lampMm, { lampMm = (it / 5).toInt() * 5f }, valueRange = 50f..120f, colors = sliderColors())
                    }
                    ToggleRow(tr("מסגרת"), if (lamp) tr("שוליים עבים למעלה ולמטה") else tr("שוליים עבים שמחזיקים את הלוח ישר"), frame) { frame = it }
                    ToggleRow(tr("נגטיב"), tr("הופך בהיר וכהה"), invert) { invert = it }
                    Text(tr("עובי מקסימלי: %.1f מ״מ").format(maxMm), color = C.Text, fontSize = 14.sp)
                    Slider(maxMm, { maxMm = (it * 2).toInt() / 2f }, valueRange = 2f..5f, colors = sliderColors())
                    Text(tr("עבה יותר = ניגודיות גבוהה יותר, אבל צריך אור חזק יותר. הדק ביותר 0.8 מ״מ."), color = C.Sub, fontSize = 12.sp)
                }
                Creations.Kind.RELIEF -> {
                    ExtraNeeded(Extras.DEPTH)
                    Section(title = tr("אפשרויות")) {
                        ToggleRow(tr("רקע שטוח"), tr("רק האובייקט בולט"), flatBg) { flatBg = it }
                        Text(tr("גובה התבליט: %.0f מ״מ").format(reliefMm), color = C.Text, fontSize = 14.sp)
                        Slider(reliefMm, { reliefMm = it.toInt().toFloat() }, valueRange = 3f..15f, colors = sliderColors())
                    }
                }
                Creations.Kind.NAME -> Section(title = tr("אפשרויות")) {
                    ChoiceCards(Templates.TagStyle.entries.toList(), tagStyle, {
                        when (it) { Templates.TagStyle.OUTLINE -> tr("לפי האותיות"); Templates.TagStyle.PLATE -> tr("מלבן"); Templates.TagStyle.ROUND -> tr("עגול") }
                    }, { tagStyle = it }, columns = 3, subtitle = {
                        when (it) { Templates.TagStyle.OUTLINE -> tr("קו מתאר סביב"); Templates.TagStyle.PLATE -> tr("לוחית קלאסית"); Templates.TagStyle.ROUND -> tr("קצוות מעוגלים") }
                    })
                    ToggleRow(tr("חור לטבעת"), tr("למחזיק מפתחות. כבוי: שלט"), keyring) { keyring = it }
                    Text(tr("גובה אותיות: %.0f מ״מ").format(letterMm), color = C.Text, fontSize = 14.sp)
                    Slider(letterMm, { letterMm = it.toInt().toFloat() }, valueRange = 8f..30f, colors = sliderColors())
                }
                Creations.Kind.COOKIE -> Section(title = tr("אפשרויות")) {
                    ToggleRow(tr("הסרת רקע"), tr("לתמונה של אובייקט. כבוי: הצורה היא הקווים הכהים בציור"), flatBg) { flatBg = it }
                    Text(tr("גודל: %.0f מ״מ").format(cookieMm), color = C.Text, fontSize = 14.sp)
                    Slider(cookieMm, { cookieMm = (it / 5).toInt() * 5f }, valueRange = 40f..140f, colors = sliderColors())
                    Text(tr("להב 0.9 מ״מ בגובה 14 מ״מ, עם שפה רחבה למטה לאחיזה."), color = C.Sub, fontSize = 12.sp)
                }
                Creations.Kind.STAMP -> Section(title = tr("אפשרויות")) {
                    Text(tr("אורך: %.0f מ״מ").format(stampMm), color = C.Text, fontSize = 14.sp)
                    Slider(stampMm, { stampMm = (it / 5).toInt() * 5f }, valueRange = 25f..120f, colors = sliderColors())
                }
                Creations.Kind.MOLD -> Section(title = tr("אפשרויות")) {
                    ToggleRow(tr("תבנית־אם לסיליקון"), tr("הצורה בולטת בתוך קופסה: יוצקים סיליקון ומקבלים תבנית גמישה. כבוי: יוצקים ישר לתוך ההדפסה"), moldMaster) { moldMaster = it }
                    ToggleRow(tr("הסרת רקע"), tr("לתמונה של אובייקט. כבוי: הצורה היא הקווים הכהים בציור"), flatBg) { flatBg = it }
                    ToggleRow(tr("פנים עם עומק"), if (Extras.ready(ctx, Extras.DEPTH)) tr("היציקה יוצאת תבליט לפי העומק בתמונה. כבוי: פנים שטוחות")
                        else tr("צריך את \"מפת עומק\" מהשיפורים בהגדרות"), moldRelief) { moldRelief = it }
                    Text(tr("גודל: %.0f מ״מ").format(cookieMm), color = C.Text, fontSize = 14.sp)
                    Slider(cookieMm, { cookieMm = (it / 5).toInt() * 5f }, valueRange = 40f..140f, colors = sliderColors())
                    Text(tr("עומק היציקה: %.0f מ״מ").format(moldMm), color = C.Text, fontSize = 14.sp)
                    Slider(moldMm, { moldMm = it.toInt().toFloat() }, valueRange = 5f..30f, colors = sliderColors())
                    if (!moldMaster) ToggleRow(tr("חור לפתיל"), tr("לנרות: חור קטן בתחתית התבנית להשחלת הפתיל"), wick) { wick = it }
                    Text(tr("כמה תבניות במגש"), color = C.Text, fontSize = 14.sp)
                    ChoiceChips(listOf(1, 2, 4, 6), copies, { it.toString() }, { copies = it })
                }
                Creations.Kind.QR -> {}
                else -> if (kind.max > 0f || kind.toggle != null || kind == Creations.Kind.STENCIL || kind == Creations.Kind.MEDAL)
                    Section(title = tr("אפשרויות")) {
                        if (kind == Creations.Kind.STENCIL) ToggleRow(tr("הסרת רקע"), tr("לתמונה של אובייקט. כבוי: הצורה היא הקווים הכהים בציור"), flatBg) { flatBg = it }
                        if (kind == Creations.Kind.MEDAL) ToggleRow(tr("רקע שטוח"), tr("רק הדמות בולטת"), flatBg) { flatBg = it }
                        kind.toggle?.let { ToggleRow(it, "", alt) { v -> alt = v } }
                        if (kind.max > 0f) {
                            Text((kind.sizeLabel ?: "%.0f").format(size), color = C.Text, fontSize = 14.sp)
                            Slider(size, { sizes[kind] = it.toInt().toFloat() }, valueRange = kind.min..kind.max, colors = sliderColors())
                        }
                    }
            }
            Text(if ((kind.need == Creations.Need.OLD && kind.needsImage && kind != Creations.Kind.COOKIE && kind != Creations.Kind.MOLD && !(kind == Creations.Kind.LITHOPHANE && lamp)) || kind == Creations.Kind.QR) tr("גודל: 100 מ״מ בצלע הארוכה. אחרי היצירה תמצא ייצוא 3MF/STL להדפסה.")
                else tr("המודל נשמר בגודל האמיתי שבחרת. אחרי היצירה תמצא ייצוא 3MF/STL להדפסה."), color = C.Faint, fontSize = 12.sp)
            Creations.error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (busy != null) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(busy, color = C.Sub, fontSize = 13.sp)
            }
            val ready = if (kind.need != Creations.Need.OLD) when (kind.need) {
                Creations.Need.IMAGE -> image != null
                Creations.Need.TEXT -> text.isNotBlank()
                Creations.Need.EITHER -> text.isNotBlank() || image != null
                else -> true
            } else when (kind) {
                Creations.Kind.QR, Creations.Kind.NAME -> text.isNotBlank()
                Creations.Kind.RELIEF -> image != null && Extras.ready(ctx, Extras.DEPTH)
                Creations.Kind.STAMP -> if (stampImage) image != null else text.isNotBlank()
                Creations.Kind.MOLD -> image != null && (!moldRelief || Extras.ready(ctx, Extras.DEPTH))
                else -> image != null
            }
            GradientButton(tr("צור {0}", kind.label), Modifier.fillMaxWidth(), sparkles = true, enabled = ready && busy == null) {
                val mm = if (kind.max > 0f) size else when (kind) {
                    Creations.Kind.NAME -> letterMm
                    Creations.Kind.COOKIE, Creations.Kind.MOLD -> cookieMm
                    else -> stampMm
                }
                Creations.create(kind, image, text, Creations.Options(frame, maxMm, invert, if (kind == Creations.Kind.MOLD) moldMm else reliefMm, flatBg, mm, keyring, tagStyle,
                    if (lamp) lampMm else 0f, stampImage, qrType, second, caption, moldMaster, moldRelief, wick, copies, alt))
            }
        }
    }
}

/** Inline download card when a creation needs an extra that isn't installed. */
@Composable
private fun ExtraNeeded(e: Extras.Extra) {
    val status = rememberModelStatus(e.pack)
    if (status is ModelStore.Status.Ready) return
    ExtraRow(e, status)
}

/** Settings section: the optional enhancement models, each downloaded only on request. */
@Composable
fun ExtrasSection() {
    Section(title = tr("שיפורים להורדה (לא חובה)")) {
        Text(tr("מודלים נוספים שמשפרים את התוצאה. כל אחד יורד רק אם תבחר בו, ואפשר למחוק בכל רגע."), color = C.Sub, fontSize = 12.sp)
        for (e in Extras.all) ExtraRow(e, rememberModelStatus(e.pack))
    }
}

/** Extra image-to-3D models (Settings). Only on chips with a pack compiled in CI. */
@Composable
fun ModelPacksSection() {
    if (com.triposr.mobile.NpuPack.chip() == null) return
    val ctx = LocalContext.current
    if (!com.triposr.mobile.Region.triposgAllowed(ctx) && !ModelStore.isReady(ctx, ModelStore.TRIPOSG)) return
    val scope = rememberCoroutineScope()
    val pack = ModelStore.TRIPOSG
    val status = rememberModelStatus(pack)
    var size by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val probe = rememberModelStatus(ModelStore.SG_PROBE)
    var probeSize by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { probeSize = runCatching { ModelStore.availableSize(ModelStore.SG_PROBE) }.getOrNull() }
    LaunchedEffect(Unit) { size = runCatching { ModelStore.availableSize(pack) }.getOrNull() }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        err = null
        scope.launch {
            importing = tr("בודק את התיקייה…")
            runCatching { ModelStore.importFrom(ctx, pack, tree) { importing = it } }
                .onSuccess { bad ->
                    err = if (bad.isEmpty()) null else tr("חסרים או לא שלמים {0} קבצים: {1}", bad.size, bad.take(4).joinToString()) +
                        if (bad.size > 4) "…" else ""
                }
                .onFailure { CrashLog.error("TripoSG import", it); err = tr("הייבוא נכשל: {0}", it.message) }
            importing = null
        }
    }
    Section(title = tr("מודלים נוספים")) {
        Text(tr("אחרי ההורדה אפשר לבחור במסך היצירה איזה מודל יבנה את התלת־ממד."), color = C.Sub, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Memory, 36.dp, C.Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("TripoSG · ניסיוני"), color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(tr("צורה מדויקת וחדה יותר מ-TripoSR, עם הצבעים מהתמונה. רץ על ה-NPU, כ-1.5 דקות למודל."), color = C.Sub, fontSize = 12.sp)
                }
            }
            when (status) {
                ModelStore.Status.Ready -> {
                    SoftButton(if (testing) tr("בודק טעינה…") else tr("בדיקת טעינה ל-NPU"), Modifier.fillMaxWidth(), enabled = !testing) {
                        testing = true
                        scope.launch {
                            testResult = withContext(Dispatchers.Default) {
                                runCatching { com.triposr.mobile.SgEngine(ctx).use { it.loadTest() } }
                                    .getOrElse { tr("הבדיקה נכשלה: {0}", it.message) }
                            }
                            testing = false
                        }
                    }
                    testResult?.let { Text(it + tr("\n(נשמר גם בדוח המלא)"), color = C.Sub, fontSize = 11.sp) }
                    when (probe) {
                        ModelStore.Status.Ready -> Text(tr("קבצי הבדיקה מותקנים ונכללים בבדיקה"), color = C.Faint, fontSize = 11.sp)
                        is ModelStore.Status.Downloading -> GlowBar(probe.done.toFloat() / probe.total, Modifier.fillMaxWidth(), C.Primary)
                        else -> SoftButton(tr("הורד קבצי בדיקה") + (probeSize?.let { " · ${it shr 20} MB" } ?: ""), Modifier.fillMaxWidth(),
                            enabled = probeSize != null) {
                            scope.launch {
                                runCatching { ModelStore.startDownload(ctx, ModelStore.SG_PROBE) }
                                    .onFailure { CrashLog.error("TripoSG probe download", it); err = it.message }
                            }
                        }
                    }
                    SoftButton(tr("מחק · {0} MB", ModelStore.sizeOnDisk(ctx, pack) shr 20), Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) {
                        if (Settings.generator(ctx) == Generator.TRIPOSG) Settings.setGenerator(ctx, Generator.TRIPOSR)
                        ModelStore.delete(ctx, pack)
                    }
                }
                is ModelStore.Status.Downloading -> {
                    GlowBar(status.done.toFloat() / status.total, Modifier.fillMaxWidth(), C.Primary)
                    SoftButton(tr("בטל"), Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, pack) }
                }
                else -> if (importing != null) {
                    Text(importing!!, color = C.Sub, fontSize = 12.sp)
                } else {
                    if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 12.sp)
                    if (size == null) Text(tr("החבילה עוד לא פורסמה לשבב הזה"), color = C.Faint, fontSize = 12.sp)
                    SoftButton(tr("הורד") + (size?.let { " · ${it shr 20} MB" } ?: ""), Modifier.fillMaxWidth(),
                        Icons.Rounded.CloudDownload, enabled = size != null) {
                        err = null
                        scope.launch {
                            runCatching { ModelStore.startDownload(ctx, pack) }
                                .onFailure { CrashLog.error("TripoSG download", it); err = it.message }
                        }
                    }
                    Text(tr("מעדיף להוריד עם אפליקציית הורדות (למשל 1DM)? העתק את הקישורים, הורד את כל הקבצים לתיקייה אחת ובחר אותה."),
                        color = C.Faint, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(tr("העתק קישורים"), Modifier.weight(1f)) {
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("TripoSG", ModelStore.links(pack).joinToString("\n")))
                            android.widget.Toast.makeText(ctx, tr("{0} קישורים הועתקו", pack.files().size), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        SoftButton(tr("ייבוא מתיקייה"), Modifier.weight(1f)) { pickFolder.launch(null) }
                    }
                }
            }
            err?.let { Text(it, color = C.Red, fontSize = 12.sp) }
            Text(tr("רישיון: MIT (VAST-AI-Research). חלקים מקוד המודל מבוססים על Tencent Hunyuan."), color = C.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ExtraRow(e: Extras.Extra, status: ModelStore.Status) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(Extras.active(ctx, e)) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { size = runCatching { ModelStore.availableSize(e.pack) }.getOrNull() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(when (e) {
                Extras.DEPTH -> Icons.Rounded.Terrain
                Extras.TAP -> Icons.Rounded.CheckCircle
                Extras.UPSCALE -> Icons.Rounded.Lightbulb
                else -> Icons.Rounded.AddPhotoAlternate
            }, 36.dp, C.Accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(e.detail, color = C.Sub, fontSize = 12.sp)
            }
        }
        when (status) {
            ModelStore.Status.Ready -> {
                if (e.usesToggle) ToggleRow(tr("פעיל"), null, active) { active = it; Extras.setActive(ctx, e, it) }
                SoftButton(tr("מחק · {0} MB", ModelStore.sizeOnDisk(ctx, e.pack) shr 20), Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) {
                    Enhance.close(); ModelStore.delete(ctx, e.pack)
                }
            }
            is ModelStore.Status.Downloading -> {
                GlowBar(status.done.toFloat() / status.total, Modifier.fillMaxWidth(), C.Primary)
                SoftButton(tr("בטל"), Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, e.pack) }
            }
            else -> {
                if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 12.sp)
                SoftButton(tr("הורד") + (size?.let { " · ${maxOf(1, it shr 20)} MB" } ?: ""), Modifier.fillMaxWidth(),
                    Icons.Rounded.CloudDownload, enabled = size != null) {
                    err = null
                    scope.launch {
                        runCatching { ModelStore.startDownload(ctx, e.pack) }
                            .onFailure { CrashLog.error("extra download ${e.pack.key}", it); err = it.message }
                    }
                }
            }
        }
        err?.let { Text(it, color = C.Red, fontSize = 12.sp) }
        Text(tr("רישיון: {0}", e.license), color = C.Faint, fontSize = 11.sp, textAlign = TextAlign.Start)
    }
}
