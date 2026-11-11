package com.triposr.mobile.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.BuildConfig
import com.triposr.mobile.Creations
import com.triposr.mobile.Creations.Kind
import com.triposr.mobile.Health
import com.triposr.mobile.Screen
import com.triposr.mobile.tr

/** One thing the user can make: what the lists, the search and "used lately" all show. */
class Entry(val id: String, val icon: ImageVector, val label: () -> String, val blurb: () -> String, val open: () -> Unit)

/** The groups on the Make screen. Texts are Hebrew keys, translated when read. */
enum class Category(private val labelHe: String, private val blurbHe: String, val icon: ImageVector, val warm: Boolean) {
    PHOTO("מתמונה להדפסה", "ליתופן, מגנט, פאזל, תליון ועוד", Icons.Rounded.Image, false),
    TEXT("שם וטקסט", "מחזיק מפתחות, שלט, סימנייה ועוד", Icons.Rounded.TextFields, true),
    MOLD("תבניות יציקה", "נרות, סבון, גבס וסיליקון", Icons.Rounded.Science, true),
    HOME("לבית ולשולחן", "מעמד לטלפון, מסגרת, קופסה", Icons.Rounded.PhoneAndroid, false);

    val label get() = tr(labelHe)
    val blurb get() = tr(blurbHe)
}

/** Everything that can be made, grouped. Nothing is listed anywhere else. */
object Catalog {
    private fun of(k: Kind) = Entry(k.name, k.icon(), { k.label }, { k.blurb() }) { AppState.openExtra(k) }

    val photo3d = Entry("PHOTO3D", Icons.Rounded.ViewInAr, { tr("תמונה לתלת־ממד") }, { tr("מודל מלא מכל זווית, מתמונה אחת") }) { AppState.startNew() }

    private val siliconeMaster = Entry("MOLD_MASTER", Icons.Rounded.Science, { tr("תבנית־אם לסיליקון") },
        { tr("יוצקים סיליקון ומקבלים תבנית גמישה") }) {
        AppState.draft = AppState.Draft(Kind.MOLD, "", Creations.Options(sizeMm = 80f, reliefMm = 12f, moldMaster = true), null)
        AppState.openExtra(Kind.MOLD)
    }
    private val twoHalves = Entry("MOLD2", Icons.Rounded.ViewInAr, { tr("תבנית משני חצאים") },
        { tr("לדגם תלת־ממד קיים: בחר דגם מהפרויקטים") }) {
        AppState.navigateRoot(Screen.Projects)
        Actions.toast(AppState.app, tr("בחר דגם תלת־ממד, ובלשונית \"עריכה\" לחץ \"תבנית יציקה משני חצאים\""))
    }

    fun entries(c: Category): List<Entry> = when (c) {
        Category.PHOTO -> listOf(Kind.LITHOPHANE, Kind.RELIEF, Kind.MAGNET, Kind.PENDANT, Kind.MEDAL, Kind.PUZZLE, Kind.PLANTER,
            Kind.NIGHT, Kind.STENCIL, Kind.COOKIE).map { of(it) }
        Category.TEXT -> listOf(Kind.NAME, Kind.QR, Kind.SIGN, Kind.BOOKMARK, Kind.PETTAG, Kind.TOPPER, Kind.STAMP, Kind.SEAL).map { of(it) }
        Category.MOLD -> listOf(of(Kind.MOLD), siliconeMaster, twoHalves)
        Category.HOME -> listOf(Kind.STAND, Kind.FRAME, Kind.BOX).map { of(it) }
    }

    fun all(): List<Entry> = listOf(photo3d) + Category.entries.flatMap { entries(it) }

    fun find(id: String): Entry? = all().firstOrNull { it.id == id }

    /** Entries whose name or description contains [query]. */
    fun search(query: String, within: List<Entry> = all()): List<Entry> {
        val q = query.trim()
        return within.filter { it.label().contains(q, ignoreCase = true) || it.blurb().contains(q, ignoreCase = true) }
    }
}

@Composable
fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(query, onChange, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
        placeholder = { Text(placeholder, color = C.Sub, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = C.Sub) },
        trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Rounded.Close, tr("נקה"), tint = C.Sub, modifier = Modifier.pressable { onChange("") }) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Violet, unfocusedBorderColor = C.Stroke,
            focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Violet,
            focusedContainerColor = C.veil(0x14), unfocusedContainerColor = C.veil(0x0D)))
}

/** A row that opens something: icon, title, a description that wraps, and a chevron. */
@Composable
fun OpenRow(icon: ImageVector, title: String, detail: String, warm: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).pressable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, 46.dp, if (warm) C.Primary else C.Accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) Text(detail, color = C.Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Faint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun EntryRow(e: Entry, index: Int = 0) = OpenRow(e.icon, e.label(), e.blurb(), warm = index % 2 == 1, onClick = e.open)

/** The main action: a photo into a 3D model. */
@Composable
fun PhotoHero(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Primary)
        .pressable { AppState.startNew() }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ViewInAr, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("תמונה לתלת־ממד"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(tr("מודל מלא מכל זווית, מתמונה אחת"), color = Color(0xE6FFFFFF), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White)
    }
}

@Composable
fun CategoryRow(c: Category) = OpenRow(c.icon, c.label, c.blurb, c.warm) { AppState.openCategory(c.name) }

/** Search results, or a line saying there are none. */
@Composable
fun SearchResults(query: String, within: List<Entry> = Catalog.all()) {
    val hits = Catalog.search(query, within)
    if (hits.isEmpty()) Text(tr("לא נמצאו תוצאות"), color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
    hits.forEachIndexed { i, e -> EntryRow(e, i) }
}

/** One place for "working", "done" and "something went wrong", the same on every screen. */
enum class BannerKind { BUSY, OK, ERROR }

@Composable
fun StatusBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val color = when (kind) { BannerKind.BUSY -> C.Violet; BannerKind.OK -> C.Green; BannerKind.ERROR -> C.Red }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (kind) {
            BannerKind.BUSY -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
            BannerKind.OK -> Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
            BannerKind.ERROR -> Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** A group of less-used options that opens and closes. */
@Composable
fun Advanced(title: String = tr("מתקדם"), content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable { open = !open }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.Violet, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(if (open) tr("הסתר") else tr("הצג"), color = C.Sub, fontSize = 13.sp)
    }
    if (open) content()
}

/** Make: everything that can be created, in groups. (The search field waits until there is more to search.) */
@Composable
fun MakeScreen() {
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("יצירה"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoHero()
            for (c in Category.entries) CategoryRow(c)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** One group: its products as a list. */
@Composable
fun CategoryScreen() {
    val c = runCatching { Category.valueOf(AppState.category) }.getOrDefault(Category.PHOTO)
    val entries = Catalog.entries(c)
    Column(Modifier.fillMaxSize()) {
        TopBar(c.label, onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.forEachIndexed { i, e -> EntryRow(e, i) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** More: everything that is not making or the user's projects. */
@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    val errors = remember(Health.tick.intValue) { Health.errorCount() }
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("עוד"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OpenRow(Icons.Rounded.Memory, tr("מודלים ומעבדים"), tr("הורדה, מחיקה ובדיקת NPU/GPU")) { AppState.navigate(Screen.Models) }
            OpenRow(Icons.Rounded.Settings, tr("הגדרות"), tr("מראה, שפה, ברירות מחדל ועדכונים"), warm = true) { AppState.navigate(Screen.Settings) }
            OpenRow(Icons.Rounded.BugReport, tr("דווח על תקלה"),
                if (errors > 0) tr("{0} שגיאות זוהו · לחץ להעתקה ושליחה", errors) else tr("שולח לנו דוח מצב שעוזר לתקן מהר")) {
                AppState.navigate(Screen.Diagnostics)
            }
            OpenRow(Icons.Rounded.Gavel, tr("משפטי ופרטיות"), tr("תנאי שימוש, פרטיות ורישיונות"), warm = true) { AppState.openLegal(0) }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, null, tint = C.Faint, modifier = Modifier.size(18.dp))
                Text("Makeo 3D · " + tr("גרסה {0} (build {1})", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = C.Faint, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
