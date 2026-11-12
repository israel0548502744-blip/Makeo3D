package com.triposr.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.Creations
import com.triposr.mobile.Sculpt
import com.triposr.mobile.tr
import kotlin.math.roundToInt

/**
 * Making something from nothing: start from a cube, a ball or a cylinder, or draw a shape with a
 * finger and give it thickness. The result opens straight in touch editing (pull, stretch, cut...).
 */
@Composable
fun SculptScreen() {
    val density = LocalDensity.current.density
    var size by remember { mutableFloatStateOf(60f) }
    var pen by remember { mutableFloatStateOf(10f) }      // line width, dp
    var thick by remember { mutableFloatStateOf(5f) }     // mm
    var fill by remember { mutableStateOf(true) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<FloatArray>() }  // x, y pairs in pixels of the drawing area
    val widths = remember { mutableStateListOf<Float>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val busy = Creations.busy

    fun commit() {
        if (current.isNotEmpty()) {
            strokes.add(FloatArray(current.size * 2) { i -> if (i % 2 == 0) current[i / 2].x else current[i / 2].y })
            widths.add(pen * density)
        }
        current = emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("ציור ופיסול"), onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            busy?.let { StatusBanner(it, BannerKind.BUSY) }
            Creations.error?.let { StatusBanner(it, BannerKind.ERROR) }

            Section(title = tr("התחל מצורה")) {
                Text(tr("בוחרים צורה ומעצבים אותה באצבע: מושכים, מותחים, חותכים ומחליקים."), color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((shape, icon, label) in listOf(
                        Triple(Sculpt.Shape.CUBE, Icons.Rounded.Inventory2, tr("קובייה")),
                        Triple(Sculpt.Shape.BALL, Icons.Rounded.RadioButtonUnchecked, tr("כדור")),
                        Triple(Sculpt.Shape.CYLINDER, Icons.Rounded.Storage, tr("גליל")),
                    )) Column(Modifier.weight(1f).glass(RoundedCornerShape(20.dp))
                        .pressable(enabled = busy == null) { Creations.sculpt(label) { Sculpt.shape(shape, size) } }
                        .padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(icon, 44.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(label, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("גודל: {0} מ״מ", size.roundToInt()), color = C.Text, fontSize = 13.sp)
                Slider(size, { size = it.roundToInt().toFloat() }, valueRange = 20f..150f, colors = sliderColors())
            }

            Section(title = tr("צייר צורה")) {
                Text(tr("צייר באצבע. קו שסוגר צורה יכול להתמלא, והציור מקבל עובי ונהיה גוף."), color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp)).background(C.Sink)
                    .border(1.dp, C.Stroke, RoundedCornerShape(18.dp))
                    .onSizeChanged { area = it }
                    .pointerInput(Unit) {
                        detectTapGestures { o -> strokes.add(floatArrayOf(o.x, o.y)); widths.add(pen * density) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o -> current = listOf(o) },
                            onDrag = { change, _ -> change.consume(); current = current + change.position },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                        )
                    }) {
                    val ink = C.Violet
                    Canvas(Modifier.fillMaxSize()) {
                        fun line(points: List<Offset>, width: Float) {
                            if (points.size == 1) { drawCircle(ink, width / 2, points[0]); return }
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (k in 1 until points.size) lineTo(points[k].x, points[k].y)
                            }
                            drawPath(path, ink, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        for ((i, s) in strokes.withIndex()) line(List(s.size / 2) { Offset(s[it * 2], s[it * 2 + 1]) }, widths[i])
                        if (current.isNotEmpty()) line(current, pen * density)
                    }
                    if (strokes.isEmpty() && current.isEmpty())
                        Text(tr("צייר כאן"), color = C.Faint, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("עובי הקו"), color = C.Text, fontSize = 13.sp, modifier = Modifier.width(92.dp))
                    Slider(pen, { pen = it }, Modifier.weight(1f), valueRange = 3f..40f, colors = sliderColors())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("עובי הגוף: {0} מ״מ", thick.roundToInt()), color = C.Text, fontSize = 13.sp, modifier = Modifier.width(92.dp))
                    Slider(thick, { thick = it.roundToInt().toFloat() }, Modifier.weight(1f), valueRange = 1f..30f, colors = sliderColors())
                }
                ToggleRow(tr("מלא צורות סגורות"), tr("עיגול שציירת נהיה דיסק מלא, לא רק טבעת"), fill) { fill = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(tr("בטל קו"), Modifier.weight(1f), Icons.AutoMirrored.Rounded.Undo, enabled = strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.size - 1); widths.removeAt(widths.size - 1)
                    }
                    SoftButton(tr("נקה"), Modifier.weight(1f), Icons.Rounded.Delete, enabled = strokes.isNotEmpty()) {
                        strokes.clear(); widths.clear()
                    }
                }
                Spacer(Modifier.height(8.dp))
                GradientButton(tr("צור מהציור"), Modifier.fillMaxWidth(), Icons.Rounded.Edit,
                    enabled = strokes.isNotEmpty() && busy == null && area.width > 0) {
                    val mask = Sculpt.strokes(strokes.toList(), widths.toList(), area.width.toFloat(), area.height.toFloat())
                    val (s, t, f) = Triple(size, thick, fill)
                    Creations.sculpt(tr("ציור")) { Sculpt.drawing(mask, s, t, f) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
