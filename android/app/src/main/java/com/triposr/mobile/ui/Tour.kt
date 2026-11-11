package com.triposr.mobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

    class Stop(val screen: Screen, val title: String, val text: String)

    fun stops() = listOf(
        Stop(Screen.Home, tr("ברוך הבא ל־Makeo 3D"), tr("סיור קצר של דקה יראה לך מה יש בכל מקום. אפשר לדלג בכל רגע.")),
        Stop(Screen.Home, tr("תמונה לתלת־ממד"), tr("הכפתור הצבעוני הגדול למעלה: בוחרים תמונה אחת, והטלפון בונה ממנה דגם תלת־ממד מלא שאפשר לסובב ולהדפיס.")),
        Stop(Screen.Home, tr("ארבע המשבצות"), tr("כל משבצת היא קבוצה של יצירות להדפסה: מתמונה, משם וטקסט, תבניות יציקה, ודברים לבית ולשולחן. לחיצה פותחת את הרשימה שלה.")),
        Stop(Screen.Home, tr("המשך מאיפה שעצרת"), tr("בתחתית מסך הבית מחכים הדברים האחרונים שיצרת. לחיצה על אחד פותחת אותו שוב.")),
        Stop(Screen.Make, tr("יצירה"), tr("הכפתור השני בסרגל למטה. כאן כל מה שאפשר ליצור, באותן ארבע קבוצות.")),
        Stop(Screen.Projects, tr("הפרויקטים"), tr("כל מה שיצרת נשמר כאן. הלב שומר פרויקט שאהבת כך שלא יימחק אף פעם, והפח מוחק. למעלה אפשר לסנן, למשל רק את מה שאהבת.")),
        Stop(Screen.Projects, tr("מסך הדגם"), tr("כשפותחים פרויקט רואים את הדגם ומתחתיו ארבע לשוניות: תצוגה, עריכה, בדיקה שהדגם תקין להדפסה, וייצוא. הכפתור הגדול למטה מייצא קובץ למדפסת.")),
        Stop(Screen.More, tr("עוד"), tr("כאן נמצאים המודלים להורדה, ההגדרות (מראה כהה או בהיר, שפה, כמה פרויקטים לשמור) ודיווח על בעיה.")),
        Stop(Screen.More, tr("זהו, אפשר להתחיל!"), tr("רוצה לראות את הסיור שוב? עוד ← הגדרות ← הדרכה.")),
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

@Composable
fun TourOverlay() {
    val ctx = LocalContext.current
    val stops = Tour.stops()
    val i = Tour.step
    if (i !in stops.indices) return
    val stop = stops[i]
    LaunchedEffect(i) { if (AppState.screen != stop.screen) AppState.navigateRoot(stop.screen) }
    BackHandler { Tour.finish(ctx) }
    // The screen behind stays visible but does not take taps while the tour is on.
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
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
