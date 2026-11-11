package com.triposr.mobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.Screen
import com.triposr.mobile.tr

/** The first-run walk through the app: a card that moves between the areas and says what each one is for. */
object Tour {
    /** The stop being shown; -1 when the tour is off. */
    var step by mutableIntStateOf(-1)

    /** [target]: the part of the screen to light up (see [tourTarget]); null = nothing in particular. */
    class Stop(val screen: Screen, val target: String?, val title: String, val text: String)

    /** Where each markable part is on screen right now. */
    val bounds = mutableStateMapOf<String, Rect>()

    fun stops() = listOf(
        Stop(Screen.Home, null, tr("ברוך הבא ל־Makeo 3D"), tr("סיור קצר של דקה יראה לך מה יש בכל מקום. אפשר לדלג בכל רגע.")),
        Stop(Screen.Home, "hero", tr("תמונה לתלת־ממד"), tr("הכפתור הצבעוני הגדול למעלה: בוחרים תמונה אחת, והטלפון בונה ממנה דגם תלת־ממד מלא שאפשר לסובב ולהדפיס.")),
        Stop(Screen.Home, "cards", tr("ארבע המשבצות"), tr("כל משבצת היא קבוצה של יצירות להדפסה: מתמונה, משם וטקסט, תבניות יציקה, ודברים לבית ולשולחן. לחיצה פותחת את הרשימה שלה.")),
        Stop(Screen.Home, "recent", tr("המשך מאיפה שעצרת"), tr("בתחתית מסך הבית מחכים הדברים האחרונים שיצרת. לחיצה על אחד פותחת אותו שוב.")),
        Stop(Screen.Make, "nav_make", tr("יצירה"), tr("הכפתור השני בסרגל למטה. כאן כל מה שאפשר ליצור, באותן ארבע קבוצות.")),
        Stop(Screen.Projects, "project", tr("הפרויקטים"), tr("כל מה שיצרת נשמר כאן. הלב שומר פרויקט שאהבת כך שלא יימחק אף פעם, והפח מוחק. למעלה אפשר לסנן, למשל רק את מה שאהבת.")),
        Stop(Screen.Projects, "project", tr("מסך הדגם"), tr("כשפותחים פרויקט רואים את הדגם ומתחתיו ארבע לשוניות: תצוגה, עריכה, בדיקה שהדגם תקין להדפסה, וייצוא. הכפתור הגדול למטה מייצא קובץ למדפסת.")),
        Stop(Screen.More, "nav_more", tr("עוד"), tr("כאן נמצאים המודלים להורדה, ההגדרות (מראה כהה או בהיר, שפה, כמה פרויקטים לשמור) ודיווח על בעיה.")),
        Stop(Screen.More, null, tr("זהו, אפשר להתחיל!"), tr("רוצה לראות את הסיור שוב? עוד ← הגדרות ← הדרכה.")),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun start() { step = 0 }

    /** Starts the tour the first time the app is used. */
    fun startOnce(ctx: Context) { if (!prefs(ctx).getBoolean("tour_done", false)) start() }

    fun finish(ctx: Context) {
        step = -1
        prefs(ctx).edit().putBoolean("tour_done", true).apply()
        AppState.navigateRoot(Screen.Home)
    }
}

/** Marks a part of the screen the tour can point at. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { Tour.bounds.remove(id) } }
    onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        if (Tour.bounds[id] != r) Tour.bounds[id] = r
    }
}

@Composable
fun TourOverlay() {
    val ctx = LocalContext.current
    val stops = Tour.stops()
    val i = Tour.step
    if (i !in stops.indices) return
    val stop = stops[i]
    LaunchedEffect(i) { if (AppState.screen != stop.screen) AppState.navigateRoot(stop.screen) }
    BackHandler { Tour.finish(ctx) }
    // Only point at something once its screen is the one on show.
    val spot = if (AppState.screen == stop.screen) stop.target?.let { Tour.bounds[it] } else null
    // The screen behind is dimmed and does not take taps; the part being explained stays lit, with a bright ring.
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0x99000000))
            if (spot != null) {
                val pad = 8.dp.toPx()
                val at = Offset(spot.left - pad, spot.top - pad)
                val size = Size(spot.width + 2 * pad, spot.height + 2 * pad)
                val round = CornerRadius(minOf(26.dp.toPx(), size.minDimension / 2))
                drawRoundRect(Color.Black, at, size, round, blendMode = BlendMode.Clear)
                drawRoundRect(Color(0x55FFFFFF), at, size, round, style = Stroke(9.dp.toPx()))
                drawRoundRect(Color.White, at, size, round, style = Stroke(3.dp.toPx()))
            }
        }
        // The card keeps out of the way: at the top when the lit part is in the lower half.
        val low = spot != null && spot.center.y > constraints.maxHeight / 2f
        Column(Modifier.align(if (low) Alignment.TopCenter else Alignment.BottomCenter).systemBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (spot == null) 104.dp else 16.dp)
            .fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.CardHigh)
            .border(1.dp, C.Violet, RoundedCornerShape(24.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stop.title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${i + 1} / ${stops.size}", color = C.Sub, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(stop.text, color = C.Text, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i < stops.size - 1) Text(tr("דלג"), color = C.Sub, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressable { Tour.finish(ctx) }.padding(horizontal = 10.dp, vertical = 10.dp))
                Spacer(Modifier.weight(1f))
                if (i > 0) SoftButton(tr("הקודם")) { Tour.step = i - 1 }
                GradientButton(if (i == stops.size - 1) tr("סיום") else tr("הבא")) {
                    if (i == stops.size - 1) Tour.finish(ctx) else Tour.step = i + 1
                }
            }
        }
    }
}
