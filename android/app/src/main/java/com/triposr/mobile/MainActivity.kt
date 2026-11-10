package com.triposr.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.triposr.mobile.ui.Actions
import com.triposr.mobile.ui.BottomNav
import com.triposr.mobile.ui.C
import com.triposr.mobile.ui.CreateMenu
import com.triposr.mobile.ui.CreateScreen
import com.triposr.mobile.ui.LegalScreen
import com.triposr.mobile.ui.ModelsScreen
import com.triposr.mobile.ui.TermsDialog
import com.triposr.mobile.ui.isRoot
import com.triposr.mobile.ui.DiagnosticsScreen
import com.triposr.mobile.ui.HomeScreen
import com.triposr.mobile.ui.ProcessingScreen
import com.triposr.mobile.ui.ProjectsScreen
import com.triposr.mobile.ui.ResultScreen
import com.triposr.mobile.ui.SettingsScreen
import com.triposr.mobile.ui.SpaceBackground
import com.triposr.mobile.ui.TripoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Set by the widget's "create" button: open the gallery as soon as the UI is up. */
    private var pendingPick by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        com.triposr.mobile.ui.Appearance.load(this)
        setContent {
            TripoTheme {
                // Dark icons in the status and navigation bars on the light look.
                val dark = com.triposr.mobile.ui.C.dark
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                    }
                }
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    Actions.loadBitmap(this, uri)?.let { AppState.setImage(it, Actions.sourceInfo(this, uri)); AppState.navigate(Screen.Create) }
                }
            }
            CreateWidget.ACTION_CREATE -> { AppState.startNew(); pendingPick = AppState.input == null }
            CreateWidget.ACTION_PROJECTS -> AppState.navigateRoot(Screen.Projects)
            ProcessingService.ACTION_RESULT -> AppState.showResult()
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val ctx = LocalContext.current
        val onImage: (Uri?) -> Unit = { uri ->
            if (uri != null) Actions.loadBitmap(ctx, uri)?.let { AppState.setImage(it, Actions.sourceInfo(ctx, uri)) }
                ?: Actions.toast(ctx, tr("לא הצלחתי לפתוח את התמונה"))
        }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onImage)
        val files = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onImage)
        val cameraUri = remember { Actions.cameraUri(ctx) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) onImage(cameraUri)
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        var retired by remember { mutableStateOf<RemoteConfig.Retired?>(null) }
        LaunchedEffect(Unit) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            // No network means no answer: the app keeps working offline.
            retired = runCatching { RemoteConfig.retired() }
                .onFailure { CrashLog.log("Remote config failed: ${it.message}") }.getOrNull()
            AppState.update = runCatching { withContext(Dispatchers.IO) { Updater.check() } }
                .onFailure {
                    CrashLog.log("Update check failed: ${it.message}")
                    Health.report(tr("בדיקת עדכונים"), tr("לא הצלחתי לבדוק עדכונים: {0}", it.message), Health.Level.WARNING)
                }.getOrNull()
        }
        LaunchedEffect(pendingPick) {
            if (pendingPick) {
                pendingPick = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        val screen = AppState.screen
        BackHandler(enabled = AppState.createMenu) { AppState.createMenu = false }
        BackHandler(enabled = !AppState.createMenu && AppState.backStack.size > 1) { AppState.back() }
        var termsOk by remember { mutableStateOf(Settings.termsAccepted(ctx)) }

        Box(Modifier.fillMaxSize().background(C.Bg)) {
            SpaceBackground()
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith fadeOut(tween(180))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen()
                        Screen.Create -> CreateScreen(
                            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onCamera = { camera.launch(cameraUri) },
                            onFiles = { files.launch("image/*") },
                        )
                        Screen.Processing -> ProcessingScreen()
                        Screen.Result -> ResultScreen()
                        Screen.Projects -> ProjectsScreen()
                        Screen.Settings -> SettingsScreen()
                        Screen.Diagnostics -> DiagnosticsScreen()
                        Screen.Extra -> com.triposr.mobile.ui.ExtraScreen()
                        Screen.Models -> ModelsScreen()
                        Screen.Legal -> LegalScreen()
                        Screen.Make -> com.triposr.mobile.ui.MakeScreen()
                        Screen.Category -> com.triposr.mobile.ui.CategoryScreen()
                        Screen.More -> com.triposr.mobile.ui.MoreScreen()
                    }
                }
                if (screen.isRoot) BottomNav(screen)
            }
            CreateMenu()
            if (!termsOk && screen != Screen.Legal) TermsDialog { Settings.acceptTerms(ctx); termsOk = true }
            retired?.let { com.triposr.mobile.ui.RetiredScreen(it) }
        }
    }
}
