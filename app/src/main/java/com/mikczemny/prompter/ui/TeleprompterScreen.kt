package com.mikczemny.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mikczemny.prompter.match.ScriptMatcher
import com.mikczemny.prompter.match.isCjkToken
import com.mikczemny.prompter.speech.Language
import com.mikczemny.prompter.speech.ModelStatus
import com.mikczemny.prompter.speech.VoskSpeechRecognizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PX_PER_SEC_MAX = 900f
private const val SCROLL_LERP = 0.12f
// A teleprompter is always light-on-black, independent of the system theme.
private val STAGE_BG = Color(0xFF0B0B0C)
private val STAGE_FG = Color(0xFFE7E7EA)
private val MUTED_FG = Color(0xFFB9B9BD)
private val PANEL_BG = Color(0xFF161618)
private val READ_COLOR = Color(0xFF5A5A5E)
private val UPCOMING_COLOR = Color(0xFFE7E7EA)
private val CURRENT_COLOR = Color(0xFF7EE787)
private val GO_GREEN = Color(0xFF2E9E4F)
private val STOP_RED = Color(0xFFD32F2F)

/** Where the big Start/Stop button sits within the bottom control bar. */
private enum class ButtonPos(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

@Composable
fun TeleprompterScreen(script: String, language: Language, onBack: () -> Unit) {
    val context = LocalContext.current

    val matcher = remember(script) { ScriptMatcher(script) }
    val words = matcher.displayTokens

    var fontSize by remember { mutableFloatStateOf(44f) }
    var margin by remember { mutableFloatStateOf(8f) } // percent
    var mirror by remember { mutableStateOf(false) }
    var buttonPos by remember { mutableStateOf(ButtonPos.CENTER) }
    var showSettings by remember { mutableStateOf(false) }

    var currentIndex by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Model download/prepare UI state (null = idle/ready).
    var modelStatus by remember { mutableStateOf<ModelStatus?>(null) }

    // Non-recomposing shared state read by the frame loop.
    val velocity = remember { mutableFloatStateOf(0f) }
    val wordOffsets = remember(script) { HashMap<Int, Float>() }
    var contentHeight by remember { mutableFloatStateOf(1f) }
    var viewportHeight by remember { mutableFloatStateOf(1f) }

    val scrollState = rememberScrollState()

    val recognizer = remember(script, language) {
        VoskSpeechRecognizer(
            context = context,
            onResult = { text, _, ts ->
                val state = matcher.pushTranscript(text, ts)
                currentIndex = state.currentIndex
                paused = state.paused
                val avgPxPerWord = contentHeight / max(words.size, 1)
                val targetPxPerSec = state.wordsPerSecond.toFloat() * avgPxPerWord
                velocity.floatValue = min(PX_PER_SEC_MAX, max(0f, targetPxPerSec))
            },
            onError = { msg -> errorMsg = msg },
            onListeningChanged = { listening -> isListening = listening },
            onModelStatus = { status -> modelStatus = status },
        )
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            errorMsg = null
            recognizer.start(language)
        } else {
            errorMsg = "Microphone permission denied — the prompter can't follow your voice."
        }
    }

    fun toggleListening() {
        if (isListening) {
            recognizer.stop()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                errorMsg = null
                recognizer.start(language)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Smooth scroll loop: blends velocity-based motion with position correction
    // toward the actually tracked word so drift self-heals.
    LaunchedEffect(Unit) {
        var lastTs = 0L
        while (true) {
            withFrameNanos { ts ->
                val dt = if (lastTs == 0L) 0f else min(0.05f, (ts - lastTs) / 1_000_000_000f)
                lastTs = ts

                if (currentIndex >= 0) {
                    val velocityStep = if (paused) 0f else velocity.floatValue * dt
                    val targetTop = wordOffsets[currentIndex]
                    var correction = 0f
                    if (targetTop != null) {
                        val targetScroll = targetTop - viewportHeight * 0.4f
                        correction = (targetScroll - scrollState.value) * SCROLL_LERP
                    }
                    val next = (scrollState.value + velocityStep + correction)
                        .coerceIn(0f, scrollState.maxValue.toFloat())
                    scrollState.dispatchRawDelta(next - scrollState.value)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = STAGE_BG,
        contentColor = STAGE_FG,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ---- Reading stage (text only) ----
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { viewportHeight = it.size.height.toFloat() },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                            .verticalScroll(scrollState)
                            .padding(
                                start = margin.dp,
                                end = margin.dp,
                                top = 24.dp,
                                bottom = 400.dp,
                            ),
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                words.forEachIndexed { i, w ->
                                    val color = when {
                                        i == currentIndex -> CURRENT_COLOR
                                        i < currentIndex -> READ_COLOR
                                        else -> UPCOMING_COLOR
                                    }
                                    withStyle(
                                        SpanStyle(
                                            color = color,
                                            fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    ) {
                                        append(w)
                                    }
                                    if (!isCjkToken(w)) append(" ")
                                }
                            },
                            style = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.35f).sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onGloballyPositioned { contentHeight = it.size.height.toFloat() },
                            onTextLayout = { layout ->
                                val rendered = layout.layoutInput.text.text
                                var searchStart = 0
                                words.forEachIndexed { i, w ->
                                    val idx = rendered.indexOf(w, searchStart)
                                    if (idx >= 0) {
                                        val line = layout.getLineForOffset(idx)
                                        wordOffsets[i] = layout.getLineTop(line)
                                        searchStart = idx + w.length
                                    }
                                }
                            },
                        )
                    }

                    // Fixed guide line at ~40% marking the "read here" anchor.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .graphicsLayer { translationY = viewportHeight * 0.4f }
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    )
                }

                // ---- Bottom controls ----
                Column(modifier = Modifier.fillMaxWidth().background(STAGE_BG)) {
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SettingsPanel(
                            fontSize = fontSize,
                            margin = margin,
                            mirror = mirror,
                            buttonPos = buttonPos,
                            onFontSize = { fontSize = it },
                            onMargin = { margin = it },
                            onMirror = { mirror = it },
                            onButtonPos = { buttonPos = it },
                        )
                    }

                    if (errorMsg != null) {
                        Text(
                            errorMsg!!,
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    ControlBar(
                        isListening = isListening,
                        buttonPos = buttonPos,
                        settingsOpen = showSettings,
                        statusText = (if (paused) "Paused" else "Tracking") +
                            " · ${currentIndex + 1}/${words.size}",
                        onBack = onBack,
                        onToggle = { toggleListening() },
                        onToggleSettings = { showSettings = !showSettings },
                    )
                }
            }

            modelStatus?.let { status ->
                ModelStatusOverlay(language = language, status = status)
            }
        }
    }
}

@Composable
private fun ControlBar(
    isListening: Boolean,
    buttonPos: ButtonPos,
    settingsOpen: Boolean,
    statusText: String,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    val bigButtonAlignment = when (buttonPos) {
        ButtonPos.LEFT -> Alignment.CenterStart
        ButtonPos.CENTER -> Alignment.Center
        ButtonPos.RIGHT -> Alignment.CenterEnd
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back to main menu
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to menu",
                    tint = STAGE_FG,
                    modifier = Modifier.size(28.dp),
                )
            }

            // Big Start/Stop, positioned per user setting (~1/4 of the bar width).
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = bigButtonAlignment,
            ) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.height(60.dp).widthIn(min = 150.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) STOP_RED else GO_GREEN,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isListening) "Stop" else "Start",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Settings gear
            IconButton(onClick = onToggleSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = if (settingsOpen) CURRENT_COLOR else STAGE_FG,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = statusText,
            fontSize = 12.sp,
            color = MUTED_FG,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun SettingsPanel(
    fontSize: Float,
    margin: Float,
    mirror: Boolean,
    buttonPos: ButtonPos,
    onFontSize: (Float) -> Unit,
    onMargin: (Float) -> Unit,
    onMirror: (Boolean) -> Unit,
    onButtonPos: (ButtonPos) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL_BG, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = STAGE_FG)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Font", fontSize = 13.sp, color = MUTED_FG, modifier = Modifier.width(76.dp))
            Slider(value = fontSize, onValueChange = onFontSize, valueRange = 24f..96f, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Margin", fontSize = 13.sp, color = MUTED_FG, modifier = Modifier.width(76.dp))
            Slider(value = margin, onValueChange = onMargin, valueRange = 0f..30f, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mirror", fontSize = 13.sp, color = MUTED_FG, modifier = Modifier.width(76.dp))
            Switch(checked = mirror, onCheckedChange = onMirror)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Button", fontSize = 13.sp, color = MUTED_FG, modifier = Modifier.width(76.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonPos.entries.forEach { pos ->
                    val selected = pos == buttonPos
                    Button(
                        onClick = { onButtonPos(pos) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) GO_GREEN else Color(0xFF2A2A2E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(pos.label, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusOverlay(language: Language, status: ModelStatus) {
    val downloading = status as? ModelStatus.Downloading
    val title = if (downloading != null) {
        "Downloading ${language.englishName} language pack…"
    } else {
        "Loading ${language.englishName}…"
    }
    val subtitle = if (downloading != null) {
        "One-time, ~${language.approxMb} MB. Works fully offline afterwards."
    } else {
        "Preparing offline recognition…"
    }
    val fraction = downloading?.fraction ?: -1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Text(text = subtitle, color = Color(0xFFB9B9BD), fontSize = 13.sp)
            if (fraction in 0f..1f) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(fraction * 100).roundToInt()}%",
                    color = Color(0xFFB9B9BD),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
