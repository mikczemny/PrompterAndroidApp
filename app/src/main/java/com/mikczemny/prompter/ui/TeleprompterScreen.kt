package com.mikczemny.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.mikczemny.prompter.speech.VoskModelManager
import com.mikczemny.prompter.speech.VoskSpeechRecognizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PX_PER_SEC_MAX = 900f
private const val SCROLL_LERP = 0.12f
private val READ_COLOR = Color(0xFF5A5A5E)
private val UPCOMING_COLOR = Color(0xFFE7E7EA)
private val CURRENT_COLOR = Color(0xFF7EE787)

@Composable
fun TeleprompterScreen(script: String, language: Language, onBack: () -> Unit) {
    val context = LocalContext.current

    val matcher = remember(script) { ScriptMatcher(script) }
    val words = matcher.displayTokens

    var fontSize by remember { mutableFloatStateOf(44f) }
    var margin by remember { mutableFloatStateOf(8f) } // percent
    var mirror by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Model download UI state.
    var downloading by remember { mutableStateOf(false) }
    var downloadFraction by remember { mutableFloatStateOf(-1f) }

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
            onModelProgress = { inProgress, fraction ->
                downloading = inProgress
                downloadFraction = fraction
            },
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

    val modelReady = remember(language) { VoskModelManager.isModelReady(context, language) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (mirror) -1f else 1f },
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { viewportHeight = it.size.height.toFloat() },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                // No space between consecutive CJK characters.
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

            ControlsBar(
                isListening = isListening,
                paused = paused,
                currentIndex = currentIndex,
                totalWords = words.size,
                fontSize = fontSize,
                margin = margin,
                mirror = mirror,
                errorMsg = errorMsg,
                onToggleListening = { toggleListening() },
                onFontSize = { fontSize = it },
                onMargin = { margin = it },
                onMirror = { mirror = it },
                onBack = onBack,
            )
        }

        if (downloading) {
            ModelDownloadOverlay(language = language, fraction = downloadFraction)
        }
    }
}

@Composable
private fun ModelDownloadOverlay(language: Language, fraction: Float) {
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
            Text(
                text = "Downloading ${language.englishName} language pack…",
                color = Color.White,
                fontSize = 16.sp,
            )
            Text(
                text = "One-time, ~${language.approxMb} MB. Works fully offline afterwards.",
                color = Color(0xFFB9B9BD),
                fontSize = 13.sp,
            )
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

@Composable
private fun ControlsBar(
    isListening: Boolean,
    paused: Boolean,
    currentIndex: Int,
    totalWords: Int,
    fontSize: Float,
    margin: Float,
    mirror: Boolean,
    errorMsg: String?,
    onToggleListening: () -> Unit,
    onFontSize: (Float) -> Unit,
    onMargin: (Float) -> Unit,
    onMirror: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = if (mirror) -1f else 1f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (errorMsg != null) {
            Text(errorMsg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Button(onClick = onToggleListening) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
                Text(if (isListening) "  Stop" else "  Start")
            }
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("Edit text")
            }
        }
        Text(
            text = (if (paused) "Paused / no match" else "Tracking") +
                " — word ${currentIndex + 1}/$totalWords",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Font", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = fontSize,
                onValueChange = onFontSize,
                valueRange = 24f..96f,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Margin", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = margin,
                onValueChange = onMargin,
                valueRange = 0f..30f,
                modifier = Modifier.weight(1f),
            )
            Text("  Mirror", fontSize = 12.sp)
            Switch(checked = mirror, onCheckedChange = onMirror)
        }
    }
}
