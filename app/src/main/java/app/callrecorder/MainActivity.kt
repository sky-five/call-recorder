package app.callrecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.callrecorder.data.Prefs
import app.callrecorder.ui.AppTheme
import app.callrecorder.ui.DetailScreen
import app.callrecorder.ui.LibraryScreen
import app.callrecorder.ui.SettingsScreen
import app.callrecorder.ui.initialCallFolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.callrecorder.data.AppSettings.loadTheme(this)
        // Housekeeping off the main thread: remove files from older builds, apply "delete after X days".
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            app.callrecorder.data.Retention.removeLegacyFiles(this@MainActivity)
            app.callrecorder.data.Retention.runCleanup(this@MainActivity)
        }
        setContent { AppTheme { Surface { App() } } }
    }
}

@Composable
private fun App() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Prefs.setTreeUri(ctx, uri)
        }
    }
    // Material "shared axis X": new screens slide in from the right, going back slides them away.
    val ease = androidx.compose.animation.core.FastOutSlowInEasing
    val dur = 320
    NavHost(
        nav, startDestination = "library",
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(dur, easing = ease)) { it / 4 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(dur))
        },
        exitTransition = {
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(dur, easing = ease)) { -it / 4 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(dur / 2))
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(dur, easing = ease)) { -it / 4 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(dur))
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(dur, easing = ease)) { it / 4 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(dur / 2))
        },
    ) {
        composable("library") {
            LibraryScreen(onOpen = { nav.navigate("detail/$it") }, onSettings = { nav.navigate("settings") }, onScreened = { nav.navigate("screened") })
        }
        composable("screened") {
            LibraryScreen(onOpen = { nav.navigate("detail/$it") }, onSettings = {}, screened = true, onBack = { nav.popBackStack() })
        }
        composable("detail/{id}") { e ->
            DetailScreen(e.arguments?.getString("id")!!.toLong(), onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onPickFolder = { picker.launch(initialCallFolder()) })
        }
    }
}
