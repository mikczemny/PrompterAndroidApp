package com.mikczemny.prompter

import android.os.Bundle
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mikczemny.prompter.speech.Languages
import com.mikczemny.prompter.ui.HomeScreen
import com.mikczemny.prompter.ui.LicensesScreen
import com.mikczemny.prompter.ui.ModeSelectionScreen
import com.mikczemny.prompter.ui.PrompterMode
import com.mikczemny.prompter.ui.RecordingsScreen
import com.mikczemny.prompter.ui.TeleprompterScreen
import com.mikczemny.prompter.ui.theme.PrompterTheme
import com.mikczemny.prompter.ui.theme.LocalAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars. From Android 15 this is enforced rather
        // than opt-in, so the screens below apply window insets themselves.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PrompterTheme {
                PrompterApp(window)
            }
        }
    }
}

@Composable
private fun PrompterApp(window: Window) {
    // Saved rather than merely remembered, so a pasted script survives process
    // death — losing one to a background kill is the sort of thing that only
    // ever happens when someone is already on camera.
    var script by rememberSaveable { mutableStateOf<String?>(null) }
    var languageCode by rememberSaveable { mutableStateOf(Languages.DEFAULT.code) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showRecordings by rememberSaveable { mutableStateOf(false) }
    var modeName by rememberSaveable { mutableStateOf<String?>(null) }
    val editorState = rememberSaveableStateHolder()

    val currentScript = script
    val mode = modeName?.let { runCatching { PrompterMode.valueOf(it) }.getOrNull() }
    val appearance = LocalAppearance.current
    val view = LocalView.current
    val stageVisible = mode != null && currentScript != null && !showLicenses && !showRecordings
    SideEffect {
        // The retro desktop can be dark even with light windows. Match the
        // actual backdrop, including transient bars above the black stage.
        val darkIcons = !stageVisible && appearance.colors.onDesktop.luminance() < 0.5f
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }

    // System Back follows the same hierarchy as the visible navigation instead
    // of finishing MainActivity from every Compose screen. Back remains owned by
    // Android only at the root mode chooser, where leaving the app is expected.
    BackHandler(enabled = mode != null) {
        when {
            showLicenses -> showLicenses = false
            showRecordings -> showRecordings = false
            currentScript != null -> script = null
            else -> modeName = null
        }
    }

    when {
        mode == null -> ModeSelectionScreen(onSelect = { modeName = it.name })

        showLicenses -> LicensesScreen(onBack = { showLicenses = false })

        showRecordings -> RecordingsScreen(onBack = { showRecordings = false })

        // Keep the editor's draft and selection while another screen is open.
        // Both reading modes share one draft; the live capture is never saved.
        currentScript == null -> editorState.SaveableStateProvider("editor") {
            HomeScreen(
                initialLanguage = Languages.byCode(languageCode),
                onStart = { text, language ->
                    languageCode = language.code
                    script = text
                },
                onOpenLicenses = { showLicenses = true },
                onOpenRecordings = { showRecordings = true },
                mode = mode,
                onChangeMode = {
                    script = null
                    modeName = null
                },
            )
        }

        else -> TeleprompterScreen(
            script = currentScript,
            language = Languages.byCode(languageCode),
            mode = mode,
            onBack = { script = null },
        )
    }
}
