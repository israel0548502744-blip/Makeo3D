package com.triposr.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.triposr.mobile.ui.BottomNav
import com.triposr.mobile.ui.CreateScreen
import com.triposr.mobile.ui.DiagnosticsScreen
import com.triposr.mobile.ui.HomeScreen
import com.triposr.mobile.ui.PrintSection
import com.triposr.mobile.ui.ProcessingScreen
import com.triposr.mobile.ui.ProjectsScreen
import com.triposr.mobile.ui.ResultScreen
import com.triposr.mobile.ui.SettingsScreen
import com.triposr.mobile.ui.SpaceBackground
import com.triposr.mobile.ui.TripoTheme
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Renders every screen with realistic fake data so the design can be reviewed from CI
 * (the images are pushed to the ui-screenshots branch). Phone size ≈ Galaxy S25 Ultra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shot(name: String, nav: Screen? = null, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                TripoTheme(animations = false) {
                    Box(Modifier.fillMaxSize()) {
                        SpaceBackground()
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) { content() }
                            if (nav != null) BottomNav(nav)
                        }
                    }
                }
            }
        }
    }

    private fun sampleImage(hue: Int = 0): Bitmap {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFDDE6F0.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(0xFFE07A3F.toInt(), 0xFF7C5CFF.toInt(), 0xFF2EB88A.toInt(), 0xFFE0457B.toInt())
        p.color = colors[hue % colors.size]
        c.drawOval(RectF(206f, 70f, 306f, 170f), p)
        c.drawRoundRect(RectF(186f, 175f, 326f, 340f), 50f, 50f, p)
        c.drawRoundRect(RectF(196f, 330f, 246f, 470f), 25f, 25f, p)
        c.drawRoundRect(RectF(266f, 330f, 316f, 470f), 25f, 25f, p)
        return bmp
    }

    private fun sphere(): Mesh {
        val res = 48; val r = TripoEngine.RADIUS; val step = 2 * r / (res - 1)
        val f = FloatArray(res * res * res) { idx ->
            val x = -r + idx / (res * res) * step; val y = -r + idx / res % res * step; val z = -r + idx % res * step
            0.5f - sqrt(x * x + y * y + z * z)
        }
        return SurfaceNets.extract(f, res, r)
    }

    @Before
    fun setUp() {
        // The test machine's language is English; the app's main language is Hebrew.
        I18n.setChoice(ctx, I18n.Choice.HE); I18n.init(ctx)
        com.triposr.mobile.ui.Appearance.set(ctx, com.triposr.mobile.ui.Look.DARK)
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", "QTI")
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", "SM8750")
        // Model downloaded.
        val models = ctx.getSharedPreferences("models", Context.MODE_PRIVATE).edit()
        for (n in ModelStore.FILES) {
            val f = ModelStore.file(ctx, n).apply { parentFile?.mkdirs() }
            RandomAccessFile(f, "rw").use { it.setLength(1000) }
            models.putLong("size_$n", 1000)
        }
        models.putBoolean("complete", true).commit()
        // Device test done: NPU failed, GPU works.
        val results = JSONObject()
            .put("NPU", JSONObject().put("support", "FAILED").put("ms", 0.0).put("detail", "QNN graph finalize failed"))
            .put("GPU", JSONObject().put("support", "OK").put("ms", 41.0).put("detail", ""))
            .put("CPU", JSONObject().put("support", "OK").put("ms", 0.0).put("detail", ""))
        ctx.getSharedPreferences("capabilities", Context.MODE_PRIVATE).edit()
            .putString("results", results.toString()).putInt("build", BuildConfig.VERSION_CODE).commit()
        // Saved projects with thumbnails.
        for (i in 0 until 5) {
            val p = Project("p$i", "מודל ${i + 1}", 1_760_000_000_000 + i * 3_600_000L, "GPU", "בינונית", 32.4 + i, 3631, 7284)
            val dir = p.dir(ctx).apply { mkdirs() }
            File(dir, "thumb.png").outputStream().use { sampleImage(i).compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "meta.json").writeText(JSONObject().put("name", p.name).put("created", p.created)
                .put("backend", p.backend).put("quality", p.quality).put("seconds", p.seconds)
                .put("vertices", p.vertices).put("triangles", p.triangles).toString())
        }
        AppState.reloadProjects()
        AppState.job = null
        AppState.input = null
        AppState.framed = null
        AppState.preview = null
    }

    @Test fun home() = shot("01_home", Screen.Home) { HomeScreen() }

    @Test fun createEmpty() = shot("02_create_empty") { CreateScreen({}, {}, {}) }

    @Test fun createWithImage() {
        AppState.input = sampleImage()
        shot("03_create_image") { CreateScreen({}, {}, {}) }
    }

    @Test fun createWithWarnings() {
        val img = sampleImage(1)
        AppState.input = img
        AppState.preview = Preview(img, true, sampleImage(2), listOf(ImageIssue.FLAT, ImageIssue.BACKGROUND_FAILED))
        shot("11_create_warnings") { CreateScreen({}, {}, {}) }
        AppState.preview = null
    }

    @Test fun processing() {
        AppState.framed = sampleImage()
        AppState.job = JobState(stage = Stage.FIELD, stageProgress = 0.62f, status = "בניית שדה תלת־ממד", backend = Backend.GPU)
        shot("04_processing") { ProcessingScreen() }
    }

    @Test fun processingError() {
        AppState.framed = sampleImage()
        AppState.job = JobState(stage = Stage.LOAD, error = "לא הצלחתי לטעון את המודל על NPU", backend = Backend.NPU)
        shot("05_processing_error") { ProcessingScreen() }
    }

    @Test fun result() {
        val m = sphere()
        AppState.mesh = m
        AppState.originalMesh = m
        AppState.project = AppState.projects.first()
        shot("06_result") { ResultScreen() }
    }

    @Test fun print() {
        val pm = Printability.prepare(sphere())
        shot("12_print") { Column { PrintSection(pm, false, false, baseCut = 3f, onShowThin = {}, onExport = {}) } }
    }

    @Test fun extras() = shot("13_extra") { com.triposr.mobile.ui.ExtraScreen() }

    @Test fun gallery() = shot("07_gallery", Screen.Projects) { ProjectsScreen() }

    @Test fun settings() = shot("08_settings", Screen.Settings) { SettingsScreen() }

    @Test fun diagnostics() {
        Health.clear()
        Health.report("ספריות מערכת", "חסרות: libQnnHtp.so")
        Health.report("אחסון", "נשארו 1500 MB פנויים. הורדת מודלים נוספים דורשת יותר", Health.Level.WARNING)
        shot("09_diagnostics") { DiagnosticsScreen() }
        Health.clear()
    }

    @Test fun models() = shot("14_models", Screen.Models) { com.triposr.mobile.ui.ModelsScreen() }

    @Test fun tour() {
        com.triposr.mobile.ui.Tour.step = 2
        AppState.navigateRoot(Screen.Home)  // the tour lights a part up only on its own screen
        // One frame is captured, before a screen part can report where it is: say where the cards are.
        com.triposr.mobile.ui.Tour.bounds["cards"] = androidx.compose.ui.geometry.Rect(60f, 507f, 1176f, 1404f)
        shot("30_tour", Screen.Home) { Box(Modifier.fillMaxSize()) { HomeScreen(); com.triposr.mobile.ui.TourOverlay() } }
        com.triposr.mobile.ui.Tour.step = -1
    }

    @Test fun createMenu() {
        AppState.createMenu = true
        shot("15_create_menu", Screen.Home) { Box(Modifier.fillMaxSize()) { HomeScreen(); com.triposr.mobile.ui.CreateMenu() } }
        AppState.createMenu = false
    }

    /** The new navigation: Make, a group, More, in Hebrew, English and the light look. */
    @Test fun navigation() {
        shot("20_make", Screen.Make) { com.triposr.mobile.ui.MakeScreen() }
        AppState.category = "TEXT"; shot("21_category") { com.triposr.mobile.ui.CategoryScreen() }
        AppState.category = "MOLD"; shot("22_category_molds") { com.triposr.mobile.ui.CategoryScreen() }
        shot("23_more", Screen.More) { com.triposr.mobile.ui.MoreScreen() }
        com.triposr.mobile.ui.Appearance.set(ctx, com.triposr.mobile.ui.Look.LIGHT)
        try {
            shot("light_20_make", Screen.Make) { com.triposr.mobile.ui.MakeScreen() }
            AppState.category = "PHOTO"; shot("light_21_category") { com.triposr.mobile.ui.CategoryScreen() }
            shot("light_23_more", Screen.More) { com.triposr.mobile.ui.MoreScreen() }
        } finally { com.triposr.mobile.ui.Appearance.set(ctx, com.triposr.mobile.ui.Look.DARK) }
    }
    @Test fun navigationEnglish() = english {
        shot("en_20_make", Screen.Make) { com.triposr.mobile.ui.MakeScreen() }
        AppState.category = "PHOTO"; shot("en_21_category") { com.triposr.mobile.ui.CategoryScreen() }
        shot("en_23_more", Screen.More) { com.triposr.mobile.ui.MoreScreen() }
        val m = sphere(); AppState.mesh = m; AppState.originalMesh = m; AppState.project = AppState.projects.first()
        shot("en_06_result") { ResultScreen() }
    }

    /** The main screens in the light look. */
    @Test fun light() {
        com.triposr.mobile.ui.Appearance.set(ctx, com.triposr.mobile.ui.Look.LIGHT)
        try {
            shot("light_01_home", Screen.Home) { HomeScreen() }
            AppState.input = sampleImage(); shot("light_03_create_image") { CreateScreen({}, {}, {}) }; AppState.input = null
            shot("light_07_gallery", Screen.Projects) { ProjectsScreen() }
            shot("light_08_settings", Screen.Settings) { SettingsScreen() }
            shot("light_14_models", Screen.Models) { com.triposr.mobile.ui.ModelsScreen() }
            shot("light_15_create_menu") { com.triposr.mobile.ui.CreateMenuContent() }
            AppState.extraKind = Creations.Kind.MOLD; shot("light_x_mold") { com.triposr.mobile.ui.ExtraScreen() }
            val m = sphere(); AppState.mesh = m; AppState.originalMesh = m; AppState.project = AppState.projects.first()
            shot("light_06_result") { ResultScreen() }
        } finally { com.triposr.mobile.ui.Appearance.set(ctx, com.triposr.mobile.ui.Look.DARK) }
    }

    /** Every print product's screen, and the full product menu scrolled to its end. */
    @Test fun crafts() {
        for (k in Creations.Kind.entries) { AppState.extraKind = k; shot("x_${k.name.lowercase()}") { com.triposr.mobile.ui.ExtraScreen() } }
    }
    @Test fun craftsEnglish() = english {
        for (k in Creations.Kind.entries) { AppState.extraKind = k; shot("en_x_${k.name.lowercase()}") { com.triposr.mobile.ui.ExtraScreen() } }
        shot("en_15_create_menu") { com.triposr.mobile.ui.CreateMenuContent() }
    }

    @Test fun legal() = shot("16_legal") { com.triposr.mobile.ui.LegalScreen() }

    /** The same screens in English (left-to-right). */
    private fun english(block: () -> Unit) {
        I18n.setChoice(ctx, I18n.Choice.EN); I18n.init(ctx)
        try { block() } finally { I18n.setChoice(ctx, I18n.Choice.HE); I18n.init(ctx) }
    }

    @Test fun homeEnglish() = english { shot("en_01_home", Screen.Home) { HomeScreen() } }
    @Test fun createEnglish() = english { AppState.input = sampleImage(); shot("en_03_create_image") { CreateScreen({}, {}, {}) } }
    @Test fun settingsEnglish() = english { shot("en_08_settings", Screen.Settings) { SettingsScreen() } }
    @Test fun modelsEnglish() = english { shot("en_14_models", Screen.Models) { com.triposr.mobile.ui.ModelsScreen() } }
    @Test fun legalEnglish() = english { shot("en_16_legal") { com.triposr.mobile.ui.LegalScreen() } }

    @Test fun homeModelMissing() {
        ctx.getSharedPreferences("models", Context.MODE_PRIVATE).edit().clear().commit()
        shot("10_home_no_model", Screen.Home) { HomeScreen() }
    }
}
