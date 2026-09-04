package com.mikczemny.prompter

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mikczemny.prompter.data.RecordingStore
import com.mikczemny.prompter.speech.Languages
import com.mikczemny.prompter.remote.RemoteCameraSession
import com.mikczemny.prompter.ui.HomeScreen
import com.mikczemny.prompter.ui.LicensesScreen
import com.mikczemny.prompter.ui.ModeSelectionScreen
import com.mikczemny.prompter.ui.PrompterMode
import com.mikczemny.prompter.ui.RecordingsScreen
import com.mikczemny.prompter.ui.RecordingDestinationScreen
import com.mikczemny.prompter.ui.RemoteCameraHostScreen
import com.mikczemny.prompter.ui.RemotePrompterClientScreen
import com.mikczemny.prompter.ui.RemoteRole
import com.mikczemny.prompter.ui.RemoteRoleScreen
import com.mikczemny.prompter.ui.TeleprompterScreen
import com.mikczemny.prompter.ui.theme.PrompterTheme
import com.mikczemny.prompter.ui.theme.ThemeMode
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars. From Android 15 this is enforced rather
        // than opt-in, so the screens below apply window insets themselves.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = androidx.compose.ui.platform.LocalContext.current
                .getSharedPreferences("appearance", MODE_PRIVATE)
            var themeName by androidx.compose.runtime.remember {
                mutableStateOf(preferences.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
            }
            val themeMode = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.SYSTEM)
            PrompterTheme(themeMode = themeMode) {
                PrompterApp(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeName = it.name
                        preferences.edit().putString("theme", it.name).apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun PrompterApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    // Saved rather than merely remembered, so a pasted script survives process
    // death — losing one to a background kill is the sort of thing that only
    // ever happens when someone is already on camera.
    var script by rememberSaveable { mutableStateOf<String?>(null) }
    var languageCode by rememberSaveable {
        mutableStateOf(Languages.byCode(Locale.getDefault().language).code)
    }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showRecordings by rememberSaveable { mutableStateOf(false) }
    var modeName by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteRoleName by rememberSaveable { mutableStateOf<String?>(null) }
    var storageConfigured by rememberSaveable {
        mutableStateOf(RecordingStore(context).isConfigured())
    }
    val remoteSession = androidx.compose.runtime.remember { RemoteCameraSession() }

    val currentScript = script
    val mode = modeName?.let { runCatching { PrompterMode.valueOf(it) }.getOrNull() }
    val remoteRole = remoteRoleName?.let { runCatching { RemoteRole.valueOf(it) }.getOrNull() }

    // System Back follows the same hierarchy as the visible navigation instead
    // of finishing MainActivity from every Compose screen. Back remains owned by
    // Android only at the root mode chooser, where leaving the app is expected.
    BackHandler(enabled = mode != null) {
        when {
            showLicenses -> showLicenses = false
            showRecordings -> showRecordings = false
            currentScript != null -> script = null
            remoteRole != null -> {
                remoteSession.close()
                remoteRoleName = null
            }
            else -> {
                modeName = null
                remoteRoleName = null
            }
        }
    }

    when {
        !storageConfigured -> RecordingDestinationScreen(
            onConfigured = { storageConfigured = true },
        )

        mode == null -> ModeSelectionScreen(
            onSelect = { modeName = it.name },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )

        mode == PrompterMode.REMOTE && remoteRole == null -> RemoteRoleScreen(
            onSelect = { remoteRoleName = it.name },
            onBack = { modeName = null },
        )

        mode == PrompterMode.REMOTE && remoteRole == RemoteRole.CAMERA -> RemoteCameraHostScreen(
            onBack = { remoteRoleName = null },
        )

        mode == PrompterMode.REMOTE && remoteRole == RemoteRole.PROMPTER && !remoteSession.connected -> RemotePrompterClientScreen(
            session = remoteSession,
            onBack = { remoteRoleName = null },
        )

        showLicenses -> LicensesScreen(onBack = { showLicenses = false })

        showRecordings -> RecordingsScreen(onBack = { showRecordings = false })

        currentScript == null -> HomeScreen(
            initialLanguage = Languages.byCode(languageCode),
            onStart = { text, language ->
                languageCode = language.code
                script = text
            },
            onOpenLicenses = { showLicenses = true },
            onOpenRecordings = { showRecordings = true },
            onConfigureStorage = { storageConfigured = false },
            mode = mode,
            onChangeMode = {
                remoteSession.close()
                script = null
                modeName = null
                remoteRoleName = null
            },
        )

        else -> TeleprompterScreen(
            script = currentScript,
            language = Languages.byCode(languageCode),
            mode = mode,
            remoteFrame = remoteSession.frame,
            onBack = { script = null },
        )
    }
}
