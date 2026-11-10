package com.triposr.mobile.ui

import com.triposr.mobile.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.R
import kotlin.math.sin

/** Design tokens: deep-space purple, glass surfaces, pink→orange primary action. */
/** The app's look: follow the phone, or always dark / light. Chosen in Settings. */
enum class Look { AUTO, DARK, LIGHT }

object Appearance {
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    /** Read by [TripoTheme]; changing it repaints the app at once. */
    var look by mutableStateOf(Look.AUTO); private set
    fun load(ctx: android.content.Context) { look = runCatching { Look.valueOf(prefs(ctx).getString("look", "AUTO")!!) }.getOrDefault(Look.AUTO) }
    fun set(ctx: android.content.Context, l: Look) { look = l; prefs(ctx).edit().putString("look", l.name).apply() }
}

/**
 * The palette. Every color is read through here, so the whole app follows [dark]: the same
 * brand gradients in both looks, with surfaces and text swapped.
 */
object C {
    /** Set by [TripoTheme]. */
    var dark by mutableStateOf(true)
    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    val Bg get() = pick(0xFF07051A, 0xFFF4F0FC)
    val BgMid get() = pick(0xFF150B36, 0xFFFBF9FF)
    val BgLow get() = pick(0xFF0D0824, 0xFFECE6FA)

    // Surfaces: translucent white over the space background, or white cards on the light one.
    val Card get() = pick(0xFF16112F, 0xFFFFFFFF)
    val CardHigh get() = pick(0xFF221A45, 0xFFF1ECFB)
    val Stroke get() = pick(0x29FFFFFF, 0x24372A6E)
    val GlassTop get() = pick(0x1AFFFFFF, 0xFFFFFFFF)
    val GlassBottom get() = pick(0x08FFFFFF, 0xF2FFFFFF)
    /** The bottom sheet's own ground. */
    val SheetTop get() = pick(0xFF241A4D, 0xFFFFFFFF)
    val SheetBottom get() = pick(0xFF120C2C, 0xFFF1ECFB)

    val Purple get() = pick(0xFF8B5CF6, 0xFF7C3AED)
    val Violet get() = pick(0xFFC084FC, 0xFF7C3AED)
    val Magenta get() = pick(0xFFD946EF, 0xFFC026D3)
    val Pink get() = pick(0xFFF0509E, 0xFFE0408F)
    val Orange get() = pick(0xFFFF9554, 0xFFE8742A)
    val Text get() = pick(0xFFF6F3FF, 0xFF1C1637)
    val Sub get() = pick(0xFFB3ABD6, 0xFF5F5880)
    val Faint get() = pick(0xFF7D75A8, 0xFF8C86A8)
    val Green get() = pick(0xFF4ADE80, 0xFF15803D)
    val Red get() = pick(0xFFFB7185, 0xFFE11D48)
    val Amber get() = pick(0xFFFBBF24, 0xFFB45309)

    /** A faint layer on a surface ([alpha] 0..255 as on dark): white on dark, deep purple on light. */
    fun veil(alpha: Int) = if (dark) Color(255, 255, 255, alpha) else Color(55, 42, 110, (alpha * 0.75f).toInt())
    /** A slightly sunken area (picture wells, segmented tracks). */
    val Sink get() = pick(0x33000000, 0x14372A6E)

    val Primary get() = Brush.horizontalGradient(listOf(Color(0xFFE6479F), Color(0xFFFF6F6A), Color(0xFFFFA04C)))
    val Accent get() = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFD946EF)))
    val Title get() = if (dark) Brush.horizontalGradient(listOf(Color(0xFFF1ECFF), Color(0xFFC3B2FF), Color(0xFFEADFFF)))
        else Brush.horizontalGradient(listOf(Color(0xFF2A1F5C), Color(0xFF6D28D9), Color(0xFF2A1F5C)))
    val GlassFill get() = Brush.verticalGradient(listOf(GlassTop, GlassBottom))
    val GlassEdge get() = if (dark) Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF)))
        else Brush.verticalGradient(listOf(Color(0x24372A6E), Color(0x14372A6E)))
}

@OptIn(ExperimentalTextApi::class)
private fun rubik(weight: Int) = Font(R.font.rubik, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700), rubik(800))

/** Screenshot tests turn animations off so frames are deterministic. */
val LocalAnimations = staticCompositionLocalOf { true }

@Composable
fun TripoTheme(animations: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (Appearance.look) { Look.DARK -> true; Look.LIGHT -> false; Look.AUTO -> androidx.compose.foundation.isSystemInDarkTheme() }
    C.dark = dark
    val base = TextStyle(fontFamily = Rubik, color = C.Text)
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = C.Purple, secondary = C.Violet, tertiary = C.Orange,
            background = C.Bg, surface = C.Card, onSurface = C.Text, onBackground = C.Text,
            surfaceVariant = C.CardHigh, outline = C.Stroke, surfaceContainer = C.CardHigh,
        ) else androidx.compose.material3.lightColorScheme(
            primary = C.Purple, secondary = C.Violet, tertiary = C.Orange,
            background = C.Bg, surface = C.Card, onSurface = C.Text, onBackground = C.Text,
            surfaceVariant = C.CardHigh, outline = C.Stroke, surfaceContainer = C.CardHigh,
        ),
        typography = Typography().let { t ->
            Typography(
                displayLarge = t.displayLarge.merge(base), headlineLarge = t.headlineLarge.merge(base),
                headlineMedium = t.headlineMedium.merge(base), titleLarge = t.titleLarge.merge(base),
                titleMedium = t.titleMedium.merge(base), bodyLarge = t.bodyLarge.merge(base),
                bodyMedium = t.bodyMedium.merge(base), bodySmall = t.bodySmall.merge(base),
                labelLarge = t.labelLarge.merge(base), labelMedium = t.labelMedium.merge(base),
                labelSmall = t.labelSmall.merge(base),
            )
        },
    ) {
        // Hebrew is laid out right-to-left and English left-to-right, whatever the phone language.
        CompositionLocalProvider(
            LocalLayoutDirection provides if (com.triposr.mobile.I18n.english) LayoutDirection.Ltr else LayoutDirection.Rtl,
            LocalAnimations provides animations,
            content = content,
        )
    }
}

/** Deep-space gradient with slowly drifting nebula glows. */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val animate = LocalAnimations.current
    val t = if (animate) {
        val inf = rememberInfiniteTransition(label = "space")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "t").value
    } else 0.3f
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(C.BgMid, C.Bg, C.BgLow)))
        val w = size.width; val h = size.height
        val phase = t * 6.2832f
        val glow = if (C.dark) 1f else 0.3f  // the same lights, much softer on the light ground
        drawCircle(Brush.radialGradient(listOf(Color(0x557C3AED).let { it.copy(alpha = it.alpha * glow) }, Color.Transparent),
            center = Offset(w * (0.15f + 0.05f * sin(phase)), h * 0.12f), radius = w * 0.9f),
            radius = w * 0.9f, center = Offset(w * (0.15f + 0.05f * sin(phase)), h * 0.12f))
        drawCircle(Brush.radialGradient(listOf(Color(0x40DB2777).let { it.copy(alpha = it.alpha * glow) }, Color.Transparent),
            center = Offset(w * 0.95f, h * (0.55f + 0.04f * sin(phase + 2f))), radius = w * 0.75f),
            radius = w * 0.75f, center = Offset(w * 0.95f, h * (0.55f + 0.04f * sin(phase + 2f))))
        drawCircle(Brush.radialGradient(listOf(Color(0x332563EB).let { it.copy(alpha = it.alpha * glow) }, Color.Transparent),
            center = Offset(w * 0.1f, h * 0.9f), radius = w * 0.8f),
            radius = w * 0.8f, center = Offset(w * 0.1f, h * 0.9f))
    }
}

/** Frosted-glass surface. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier =
    this.clip(shape)
        .background(if (strong) (if (C.dark) Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0FFFFFFF))) else SolidColor(Color(0xF7FFFFFF))) else C.GlassFill)
        .border(1.dp, C.GlassEdge, shape)

/** Fades and slides content in once, staggered by [index]. */
@Composable
fun Modifier.enter(index: Int = 0): Modifier {
    if (!LocalAnimations.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/** Slight shrink while pressed, for anything clickable. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    return this.scale(s).clickable(source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GradientText(text: String, size: TextUnit, modifier: Modifier = Modifier, brush: Brush = C.Title,
                 weight: FontWeight = FontWeight.ExtraBold) {
    Text(text, modifier = modifier, style = TextStyle(brush = brush, fontSize = size, fontWeight = weight,
        fontFamily = Rubik, letterSpacing = (-0.5).sp))
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) CircleIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("חזרה"), onClick = onBack)
        Text(title, color = C.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun CircleIcon(icon: ImageVector, label: String, size: Dp = 42.dp, tint: Color = C.Text, onClick: () -> Unit) {
    Box(Modifier.size(size).glass(CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Section(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().glass().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

/** Pill-shaped primary action with a glow and a soft light sweep. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    sparkles: Boolean = false,
    onClick: () -> Unit,
) {
    val animate = LocalAnimations.current && enabled
    val sweep = if (animate) {
        val inf = rememberInfiniteTransition(label = "sweep")
        inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)), label = "x").value
    } else -1f
    Box(
        modifier.height(58.dp)
            .shadow(if (enabled) 18.dp else 0.dp, CircleShape, ambientColor = C.Pink, spotColor = C.Pink)
            .clip(CircleShape).background(C.Primary)
            .drawWithContent {
                drawContent()
                if (sweep > -0.5f) {
                    val x = size.width * sweep
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x55FFFFFF), Color.Transparent),
                        start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)))
                }
            }
            .alpha(if (enabled) 1f else 0.45f)
            .pressable(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp)) {
            if (sparkles) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            else if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            if (sparkles) { Spacer(Modifier.width(10.dp)); Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Secondary glass pill button. */
@Composable
fun SoftButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = C.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(52.dp).glass(CircleShape, strong = true).alpha(if (enabled) 1f else 0.45f)
            .pressable(enabled, onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, disabled: (T) -> Boolean = { false }) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(C.Sink).border(1.dp, C.Stroke, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (o in options) {
            val sel = o == selected
            val textColor by animateColorAsState(if (sel) Color.White else if (disabled(o)) C.Faint else C.Sub, label = "seg")
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape)
                    .then(if (sel) Modifier.background(C.Accent) else Modifier)
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), color = textColor, fontSize = if (options.size > 5) 11.sp else 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = C.Sub, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Purple, checkedThumbColor = Color.White,
                checkedBorderColor = C.Violet, uncheckedTrackColor = C.veil(0x22),
                uncheckedThumbColor = C.Sub, uncheckedBorderColor = C.Stroke),
        )
    }
}

enum class StepState { DONE, ACTIVE, PENDING }

@Composable
fun StepRow(label: String, state: StepState, extra: String? = null) {
    val pulse = if (state == StepState.ACTIVE && LocalAnimations.current) {
        rememberInfiniteTransition(label = "pulse").animateFloat(0.5f, 1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p").value
    } else 1f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        when (state) {
            StepState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(20.dp))
            StepState.ACTIVE -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).alpha(0.3f * pulse).clip(CircleShape).background(C.Violet))
                Box(Modifier.size(9.dp).clip(CircleShape).background(C.Violet))
            }
            StepState.PENDING -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = when (state) { StepState.DONE -> C.Sub; StepState.ACTIVE -> C.Text; else -> C.Faint },
            fontSize = 14.sp, fontWeight = if (state == StepState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (extra != null) Text(extra, color = C.Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Gradient progress ring with a soft glow underneath. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 10.dp, content: @Composable () -> Unit) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw * 2
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            val ring = Brush.sweepGradient(listOf(C.Purple, C.Magenta, C.Pink, C.Orange, C.Purple))
            drawArc(C.veil(0x1F), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            drawArc(ring, -90f, 360f * p, false, tl, Size(d, d), alpha = 0.25f, style = Stroke(sw * 2.6f, cap = StrokeCap.Round))
            drawArc(ring, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Chip(text: String, color: Color = C.Violet) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Rounded square with a gradient and an icon (feature/menu tiles). */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 48.dp, brush: Brush = C.Accent) {
    Box(Modifier.size(size).shadow(10.dp, RoundedCornerShape(size / 3.2f), ambientColor = C.Purple, spotColor = C.Purple)
        .clip(RoundedCornerShape(size / 3.2f)).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Thin gradient progress bar. */
@Composable
fun GlowBar(progress: Float, modifier: Modifier = Modifier, brush: Brush = C.Accent) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "bar")
    Box(modifier.height(6.dp).clip(CircleShape).background(C.veil(0x22))) {
        Box(Modifier.fillMaxWidth(p).height(6.dp).clip(CircleShape).background(brush))
    }
}

/**
 * Picker made of glass cards: each option has an optional icon, a title and a short line under
 * it; the chosen one gets a gradient outline and a check badge. Used for choices that need a word
 * of explanation (model, detail level, processor) instead of a cramped segmented bar.
 */
@Composable
fun <T> ChoiceCards(
    options: List<T>,
    selected: T,
    title: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: (T) -> String? = { null },
    icon: (T) -> ImageVector? = { null },
    badge: (T) -> String? = { null },
    disabled: (T) -> Boolean = { false },
    columns: Int = options.size.coerceAtMost(2),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in options.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (o in row) ChoiceCard(o == selected, title(o), subtitle(o), icon(o), badge(o), disabled(o),
                    compact = columns > 2, modifier = Modifier.weight(1f)) { onSelect(o) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean, title: String, subtitle: String?, icon: ImageVector?, badge: String?, disabled: Boolean,
    compact: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "choice")
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(
                C.veil(0x1F).let { it.copy(alpha = it.alpha / 0.12f * (0.10f + 0.10f * glow)) }, C.veil(0x08))))
            .border(if (selected) 1.6.dp else 1.dp,
                if (selected) Brush.linearGradient(listOf(C.Violet, C.Magenta, C.Orange)) else C.GlassEdge, shape)
            .alpha(if (disabled) 0.45f else 1f)
            .pressable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 10.dp else 12.dp),
    ) {
        Column(horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier.fillMaxWidth()) {
            if (icon != null) {
                Box(Modifier.size(if (compact) 30.dp else 34.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) C.Accent else SolidColor(C.veil(0x1A))),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (selected) Color.White else C.Sub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(title, color = if (selected) C.Text else C.Sub, fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = if (selected) C.Sub else C.Faint, fontSize = 11.sp,
                maxLines = 2, lineHeight = 14.sp,
                textAlign = if (compact) androidx.compose.ui.text.style.TextAlign.Center else null)
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = C.Violet,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(16.dp))
        else if (badge != null) Text(badge, color = C.Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(C.Amber.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Scrollable row of pills for quick choices with short labels (QR type, letter style…). */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
                    icon: (T) -> ImageVector? = { null }) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            val on = o == selected
            Row(
                Modifier.clip(CircleShape)
                    .background(if (on) C.Primary else SolidColor(C.veil(0x14)))
                    .border(1.dp, if (on) SolidColor(Color(0x40FFFFFF)) else C.GlassEdge, CircleShape)
                    .pressable { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon(o)?.let { Icon(it, null, tint = if (on) Color.White else C.Sub, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(label(o), color = if (on) Color.White else C.Sub, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** One row of a settings-style list: icon tile, title, optional detail and badge, chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, detail: String? = null, badge: String? = null,
           badgeColor: Color = C.Red, brush: Brush = C.Accent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, 38.dp, brush)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (detail != null) Text(detail, color = C.Sub, fontSize = 12.sp, maxLines = 2)
        }
        if (badge != null) {
            Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(badgeColor).padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Faint, modifier = Modifier.size(22.dp))
    }
}

/** Thin divider between rows inside a [Section]. */
@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.veil(0x12)))
