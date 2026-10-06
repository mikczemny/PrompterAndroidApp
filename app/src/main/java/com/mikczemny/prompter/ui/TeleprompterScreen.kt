package com.mikczemny.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording as CameraRecording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikczemny.prompter.R
import com.mikczemny.prompter.data.RecordingStore
import com.mikczemny.prompter.match.ScriptMatcher
import com.mikczemny.prompter.speech.Language
import com.mikczemny.prompter.speech.ModelStatus
import com.mikczemny.prompter.speech.VoskSpeechRecognizer
import com.mikczemny.prompter.ui.theme.LocalAppearance
import com.mikczemny.prompter.ui.theme.AppearanceDialog
import com.mikczemny.prompter.ui.theme.AppHeader
import com.mikczemny.prompter.ui.theme.AppOutlinedButton
import com.mikczemny.prompter.ui.theme.appFrame
import com.mikczemny.prompter.ui.theme.appSurfaceShape
import com.mikczemny.prompter.ui.theme.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PX_PER_SEC_MAX = 900f
private const val SCROLL_LERP = 0.12f

/** Half-height of the fully lit reading band, as a fraction of viewport height. */
private const val BAND_HALF_HEIGHT = 0.11f

/** How far the dimming fades in above and below the band. */
private const val BAND_FADE = 0.11f

/** How dark the script goes outside the band. */
private const val DIM_ALPHA = 0.78f

private const val COUNTDOWN_FROM = 3

/** Height of the read-through progress bar when stage controls are visible. */
private val PROGRESS_BAR_HEIGHT = 3.dp

/** Where the big Start/Stop button sits within the bottom control bar. */
private enum class ButtonPos(@StringRes val labelRes: Int) {
    LEFT(R.string.button_pos_left),
    CENTER(R.string.button_pos_center),
    RIGHT(R.string.button_pos_right),
}

/**
 * Token whose rendered text contains [offset], via binary search over the
 * per-token start offsets. Returns the last token starting at or before the
 * offset, so a tap in the trailing space lands on the word just read.
 */
private fun tokenIndexForOffset(starts: IntArray, offset: Int): Int {
    if (starts.isEmpty()) return -1
    var low = 0
    var high = starts.size - 1
    var found = 0
    while (low <= high) {
        val mid = (low + high) / 2
        if (starts[mid] <= offset) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/**
 * First index in [offsets] (sorted ascending, per-token line-top Y positions)
 * whose value is >= [y]. Returns [offsets].size if every entry is smaller.
 * Used to turn the current scroll position into a range of visible token
 * indices, so the matcher can be told which words are actually on screen.
 */
private fun firstIndexAtOrAfter(offsets: FloatArray, y: Float): Int {
    var low = 0
    var high = offsets.size
    while (low < high) {
        val mid = (low + high) / 2
        if (offsets[mid] < y) low = mid + 1 else high = mid
    }
    return low
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeleprompterScreen(
    script: String,
    language: Language,
    mode: PrompterMode,
    onBack: () -> Unit,
) {
    val stage = LocalAppearance.current.stage
    val appearanceColors = LocalAppearance.current.colors
    val context = LocalContext.current

    val matcher = remember(script) { ScriptMatcher(script) }
    val words = matcher.displayTokens

    val preferences = remember(context) { StagePreferences(context) }
    var settings by remember(mode) { mutableStateOf(preferences.load(mode)) }
    val fontSize = settings.fontSize
    val margin = settings.marginPercent
    val mirror = settings.mirror
    val anchorFraction = settings.anchorFraction
    val currentAnchorFraction by rememberUpdatedState(anchorFraction)
    val useCountdown = settings.useCountdown
    val buttonPos = ButtonPos.valueOf(settings.buttonPosition)
    val brightness = settings.brightness
    var showSettings by remember { mutableStateOf(false) }
    // Always enter with controls visible, even if the previous take was clean.
    var controlsVisible by remember { mutableStateOf(true) }
    val stageFocus = remember { FocusRequester() }

    fun updateSettings(updated: ReadingSettings) {
        settings = updated.sanitized(mode)
        preferences.save(mode, settings)
    }

    KeepScreenBright(brightness)
    ImmersiveStage()

    var currentIndex by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    // The user's intent includes countdown/model preparation, before the
    // recognizer owns the mic. This also rejects late results after Stop.
    var sessionRequested by remember { mutableStateOf(false) }
    var sessionStopping by remember { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var acceptResultsAfterMs by remember { mutableStateOf(0L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    // Read progress through the script, 0..1, from MatchState.progress — drives
    // the thin always-visible bar at the top of the stage.
    var progress by remember { mutableFloatStateOf(0f) }

    // Model download/prepare UI state (null = idle/ready).
    var modelStatus by remember { mutableStateOf<ModelStatus?>(null) }

    // Audio recording: the recognizer tees the mic stream to a WAV file while it
    // tracks. Recording follows tracking automatically — it starts with the mic
    // and, when tracking ends, the finished file waits in pendingRecording for
    // the keep/discard choice before it is saved anywhere.
    val scope = rememberCoroutineScope()
    val recordingStore = remember { RecordingStore(context) }
    var recording by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var pendingAudio by remember { mutableStateOf<File?>(null) }
    var decidingRecording by remember { mutableStateOf(false) }

    // Selfie preview: a draggable camera window floating over the script, so the
    // speaker can frame themselves while reading. Off until enabled in settings.
    // Key camera UI state by mode so switching from SelfiePrompter can never
    // carry an open preview into ExtPrompter. External mode starts text-only,
    // but keeps the camera button as an explicit opt-in.
    var cameraEnabled by remember(mode) { mutableStateOf(false) }
    var cameraBounds by remember(mode) { mutableStateOf<CameraWindowBounds?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraEnabled = granted }

    // One camera session shared by the preview and video capture. Video is
    // recorded WITHOUT audio — the mic is already taken by the tee — and paired
    // with the audio file by a shared timestamp for easy sync in editing.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
        }
    }
    var videoRecording by remember { mutableStateOf<CameraRecording?>(null) }
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    // True between Stop and the camera finishing the MP4, so the keep/discard
    // prompt waits for the video file to be complete before offering to save it.
    var awaitingVideo by remember { mutableStateOf(false) }

    fun toggleCamera(on: Boolean) {
        // Unbinding CameraX while it is finalizing a recording corrupts the MP4.
        // Keep the session alive until Stop; the switch becomes effective again
        // as soon as the paired audio/video recording has finished.
        if (!on && (recording || awaitingVideo || videoRecording != null)) return
        if (!on) {
            cameraEnabled = false
            cameraBounds = null
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraEnabled = true else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // The start-screen choice is a working preset, not merely a label. Selfie
    // mode enters the stage ready to frame and record; external-camera mode
    // never opens CameraX and leaves the full display to the script.
    LaunchedEffect(mode) {
        if (mode == PrompterMode.SELFIE) toggleCamera(true) else toggleCamera(false)
    }

    // Non-recomposing shared state read by the frame loop. The offsets are a
    // primitive array rather than a map: it is written once per layout for every
    // token and read on every frame, so boxing tens of thousands of floats would
    // be pure waste. NaN marks a token that has not been laid out yet.
    val velocity = remember { mutableFloatStateOf(0f) }
    val wordOffsets = remember(words) { FloatArray(words.size) { Float.NaN } }
    var contentHeight by remember { mutableFloatStateOf(1f) }
    var viewportHeight by remember { mutableFloatStateOf(1f) }
    var viewportWidth by remember { mutableFloatStateOf(1f) }
    var viewportLeft by remember { mutableFloatStateOf(0f) }
    var alignToPosition by remember { mutableStateOf(false) }
    var manualScrolling by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val textTopPaddingPx = with(density) { 24.dp.toPx() }

    // The script is rendered exactly as written — line breaks, blank lines and
    // spacing intact — because how a speaker lays out their text is part of how
    // they read it. The matcher hands back each token's offset into that same
    // string, so highlighting needs no reflowed copy.
    //
    // The string is immutable and the highlight is painted over it rather than
    // expressed as text spans, so tracking a new word repaints but never
    // re-measures, which is what keeps long scripts cheap.
    val tokenCharStarts = matcher.tokenOffsets
    var textLayout by remember(words) { mutableStateOf<TextLayoutResult?>(null) }

    val scrollState = rememberScrollState()

    // Vosk delivers every callback (results, errors, listening state, model
    // status) from its own background/audio thread, never the main thread.
    // Compose state can only be written safely from the composition's thread,
    // so every callback below hops back onto it via this handler instead of
    // writing MutableState directly from wherever Vosk happens to call in.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val recognizer = remember(script, language) {
        VoskSpeechRecognizer(
            context = context,
            onResult = { text, isFinal, ts ->
                mainHandler.post {
                    if (!sessionRequested || manualScrolling || scrollState.isScrollInProgress || ts < acceptResultsAfterMs) {
                        return@post
                    }
                    val state = matcher.pushTranscript(text, isFinal, ts)
                    currentIndex = state.currentIndex
                    paused = state.paused
                    progress = state.progress.toFloat()
                    val avgPxPerWord = contentHeight / max(words.size, 1)
                    val targetPxPerSec = state.wordsPerSecond.toFloat() * avgPxPerWord
                    velocity.floatValue = min(PX_PER_SEC_MAX, max(0f, targetPxPerSec))
                }
            },
            onError = { msg -> mainHandler.post { errorMsg = msg; controlsVisible = true } },
            onListeningChanged = { listening ->
                mainHandler.post {
                    isListening = listening
                    if (!listening) {
                        sessionRequested = false
                        sessionStopping = false
                        paused = true
                        velocity.floatValue = 0f
                    }
                }
            },
            onModelStatus = { status ->
                mainHandler.post { if (sessionRequested || status == null) modelStatus = status }
            },
            onInterrupted = {
                mainHandler.post {
                    errorMsg = context.getString(R.string.recording_interrupted)
                    sessionRequested = false
                    paused = true
                    velocity.floatValue = 0f
                    controlsVisible = true
                }
            },
        )
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionPending = false
        if (granted && sessionRequested) {
            errorMsg = null
            if (useCountdown) countdown = COUNTDOWN_FROM else recognizer.start(language)
        } else if (!granted) {
            sessionRequested = false
            errorMsg = context.getString(R.string.mic_permission_denied)
            controlsVisible = true
        }
    }

    fun stopSession() {
        sessionRequested = false
        sessionStopping = recognizer.isActive
        countdown = 0
        paused = true
        alignToPosition = false
        modelStatus = null
        velocity.floatValue = 0f
        recognizer.stop()
    }

    fun toggleListening() {
        if (sessionRequested || isListening || countdown > 0) {
            stopSession()
            return
        }
        // A remote can send another press before the old take has finalized.
        // Keep that press from overwriting the files awaiting Save/Discard.
        if (recognizer.isActive || sessionStopping || permissionPending || recording || recordingFile != null ||
            pendingAudio != null || pendingVideo != null || awaitingVideo || decidingRecording
        ) return
        sessionRequested = true
        matcher.jumpTo(currentIndex)
        acceptResultsAfterMs = System.currentTimeMillis()
        paused = true
        velocity.floatValue = 0f
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionPending = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        errorMsg = null
        if (useCountdown) countdown = COUNTDOWN_FROM else recognizer.start(language)
    }

    // Back always reveals a clean stage first, without ending the take.
    BackHandler(enabled = !controlsVisible && !showSettings) { controlsVisible = true }

    val lifecycleOwner = LocalLifecycleOwner.current
    val stopOnBackground by rememberUpdatedState { stopSession() }
    DisposableEffect(lifecycleOwner, recognizer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                stopOnBackground()
                controlsVisible = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recording follows tracking: it auto-starts once the mic is live, and when
    // tracking ends (Stop or an interruption, which finalizes the WAV inside the
    // recognizer) the finished file is parked for the keep/discard prompt.
    LaunchedEffect(isListening) {
        if (isListening && !sessionRequested) {
            recognizer.stop()
        } else if (isListening && !recording) {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
            val audioFile = recordingStore.newTempFile("prompter_$stamp.wav")
            recordingFile = audioFile
            recognizer.startRecording(audioFile)
            recording = true

            // If the selfie preview is up, record video (no audio) alongside.
            if (cameraEnabled) {
                val videoFile = recordingStore.newTempFile("prompter_$stamp.mp4")
                runCatching {
                    videoRecording = cameraController.startRecording(
                        FileOutputOptions.Builder(videoFile).build(),
                        AudioConfig.AUDIO_DISABLED,
                        ContextCompat.getMainExecutor(context),
                    ) { event ->
                        // The MP4 is only complete at Finalize; only then is it
                        // offered to the keep/discard prompt.
                        if (event is VideoRecordEvent.Finalize) {
                            videoRecording = null
                            if (event.hasError()) videoFile.delete() else pendingVideo = videoFile
                            awaitingVideo = false
                        }
                    }
                }.onFailure {
                    videoRecording = null
                    videoFile.delete()
                }
            }
        } else if (!isListening && recording) {
            recording = false
            pendingAudio = recordingFile
            recordingFile = null
            // Stop video and wait for its Finalize before prompting to save.
            if (videoRecording != null) {
                awaitingVideo = true
                runCatching { videoRecording?.stop() }
                videoRecording = null
            }
        }
    }

    // The countdown gives the speaker a beat to draw breath and look up before
    // the microphone opens.
    LaunchedEffect(countdown) {
        if (countdown <= 0 || !sessionRequested) return@LaunchedEffect
        delay(1000)
        countdown -= 1
        if (countdown == 0 && sessionRequested) recognizer.start(language)
    }

    /** Sends the pointer to [index] (-1 = back to the top) and parks tracking there. */
    fun moveTo(index: Int, align: Boolean = true) {
        val target = index.coerceIn(-1, words.lastIndex)
        acceptResultsAfterMs = System.currentTimeMillis() + 1
        recognizer.resetTranscript()
        matcher.jumpTo(target)
        currentIndex = target
        paused = true
        alignToPosition = align
        velocity.floatValue = 0f
        progress = if (words.isEmpty()) 0f else (target + 1).toFloat() / words.size
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            manualScrolling = true
            alignToPosition = false
            paused = true
            velocity.floatValue = 0f
        } else if (manualScrolling) {
            manualScrolling = false
            // A drag chooses a new reading position. Remember it before the
            // voice loop resumes, otherwise it would pull the text back.
            val readingY = scrollState.value + viewportHeight * currentAnchorFraction
            moveTo(tokenAtReadingLine(wordOffsets, readingY), align = false)
        }
    }

    fun performRemoteAction(action: RemoteAction) {
        when (action) {
            RemoteAction.TOGGLE -> toggleListening()
            RemoteAction.PREVIOUS_LINE -> moveTo(lineJumpTarget(wordOffsets, currentIndex, -1))
            RemoteAction.NEXT_LINE -> moveTo(lineJumpTarget(wordOffsets, currentIndex, 1))
            RemoteAction.PREVIOUS_PAGE -> moveTo(pageJumpTarget(wordOffsets, currentIndex, -1, viewportHeight))
            RemoteAction.NEXT_PAGE -> moveTo(pageJumpTarget(wordOffsets, currentIndex, 1, viewportHeight))
            RemoteAction.RESTART -> moveTo(-1)
            RemoteAction.TOGGLE_CONTROLS -> controlsVisible = !controlsVisible
        }
    }

    val stageInputBlocked = showSettings || permissionPending || pendingAudio != null ||
        awaitingVideo || decidingRecording
    LaunchedEffect(stageInputBlocked, controlsVisible) {
        if (!stageInputBlocked) stageFocus.requestFocus()
    }

    // Smooth scroll loop: blends velocity-based motion with position correction
    // toward the actually tracked word so drift self-heals.
    LaunchedEffect(matcher, textTopPaddingPx) {
        var lastTs = 0L
        var lastPauseCheck = 0L
        while (true) {
            withFrameNanos { ts ->
                val dt = if (lastTs == 0L) 0f else min(0.05f, (ts - lastTs) / 1_000_000_000f)
                lastTs = ts

                // Empty/unchanged recognition results are not emitted during
                // silence. Refresh the pause clock independently of speech.
                if (sessionRequested && isListening && ts - lastPauseCheck >= 100_000_000L) {
                    val state = matcher.getState()
                    paused = state.paused
                    if (paused) velocity.floatValue = 0f
                    lastPauseCheck = ts
                }

                val started = currentIndex >= 0 && currentIndex < wordOffsets.size
                val canTrack = sessionRequested && isListening && !manualScrolling
                val velocityStep = if (paused || !started || !canTrack) 0f else velocity.floatValue * dt

                // Before the first match — and after a reset — the anchor is the
                // top of the script rather than a tracked word. A token that has
                // not been laid out yet has no anchor at all, so only the
                // velocity term applies until layout catches up.
                val anchorTop = if (started) wordOffsets[currentIndex] else 0f
                var correction = 0f
                if (!anchorTop.isNaN() && (canTrack || alignToPosition) && !scrollState.isScrollInProgress) {
                    val targetScroll =
                        if (started) anchorTop - viewportHeight * currentAnchorFraction else 0f
                    correction = (targetScroll - scrollState.value) * SCROLL_LERP
                    if (abs(targetScroll.coerceIn(0f, scrollState.maxValue.toFloat()) - scrollState.value) < 1f) {
                        alignToPosition = false
                    }
                }
                val next = (scrollState.value + velocityStep + correction)
                    .coerceIn(0f, scrollState.maxValue.toFloat())
                if (!scrollState.isScrollInProgress) scrollState.dispatchRawDelta(next - scrollState.value)

                // Tell the matcher which words are actually on screen, so a
                // match further down the script can't win while it's still
                // scrolled out of view. Skipped until the first layout pass
                // has populated wordOffsets (all-NaN before that).
                if (wordOffsets.isNotEmpty() && !wordOffsets[0].isNaN()) {
                    val visibleTop = scrollState.value.toFloat()
                    val visibleBottom = visibleTop + viewportHeight
                    val first = firstIndexAtOrAfter(wordOffsets, visibleTop)
                        .coerceIn(0, wordOffsets.size - 1)
                    val last = (firstIndexAtOrAfter(wordOffsets, visibleBottom) - 1)
                        .coerceIn(first, wordOffsets.size - 1)
                    matcher.visibleRange = first..last
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (stageInputBlocked) {
                    false
                } else {
                    val result = handleRemoteKey(event.nativeKeyEvent, settings.remoteEnabled, settings.volumeKeysEnabled)
                    result.action?.let(::performRemoteAction)
                    result.handled
                }
            }
            .focusRequester(stageFocus)
            .focusable(),
        color = stage.Background,
        contentColor = stage.Foreground,
    ) {
        val camera = cameraBounds.takeIf { cameraEnabled }
        val cameraGapPx = with(density) { 12.dp.toPx() }
        val visualStartPx = if (camera != null && camera.centerX <= viewportLeft + viewportWidth / 2f) {
            (camera.right + cameraGapPx - viewportLeft).coerceAtLeast(0f)
        } else {
            0f
        }
        val visualEndPx = if (camera != null && camera.centerX > viewportLeft + viewportWidth / 2f) {
            (viewportLeft + viewportWidth - camera.left + cameraGapPx).coerceAtLeast(0f)
        } else {
            0f
        }
        // The entire script layer is mirrored for beam-splitter glass, so its
        // logical padding has to be swapped to preserve the visual exclusion.
        val logicalStartPx = if (mirror) visualEndPx else visualStartPx
        val logicalEndPx = if (mirror) visualStartPx else visualEndPx
        val minimumTextWidth = with(density) {
            maxOf(120.dp.toPx(), fontSize.sp.toPx() * 2.5f).coerceAtMost(viewportWidth * 0.65f)
        }
        val insets = readingInsets(viewportWidth, margin, logicalStartPx, logicalEndPx, minimumTextWidth)
        val textStartPadding = with(density) { insets.start.toDp() }
        val textEndPadding = with(density) { insets.end.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {

                // ---- Reading stage (text only) ----
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            viewportHeight = it.size.height.toFloat()
                            viewportWidth = it.size.width.toFloat()
                            viewportLeft = it.positionInRoot().x
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                            .verticalScroll(scrollState)
                            .padding(
                                start = textStartPadding,
                                end = textEndPadding,
                                top = 24.dp,
                                // The last line must reach the reading band on
                                // a tall tablet just as it does on a phone.
                                bottom = with(density) { (viewportHeight * (1f - anchorFraction)).toDp() },
                            ),
                    ) {
                        Text(
                            text = script,
                            color = stage.Foreground,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * settings.lineSpacing).sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .onGloballyPositioned { contentHeight = it.size.height.toFloat() }
                                .drawBehind {
                                    val layout = textLayout ?: return@drawBehind
                                    val index = currentIndex
                                    if (index < 0 || index >= tokenCharStarts.size) {
                                        return@drawBehind
                                    }
                                    val start = tokenCharStarts[index]
                                    val end = start + words[index].length
                                    if (end > layout.layoutInput.text.length) return@drawBehind

                                    val line = layout.getLineForOffset(start)
                                    val top = layout.getLineTop(line)
                                    val bottom = layout.getLineBottom(line)
                                    val left = layout.getHorizontalPosition(start, true)
                                    // A word split across a line break has no single
                                    // box, so fall back to the rest of the line.
                                    val right = layout.getHorizontalPosition(end, true)
                                        .let { if (it <= left) layout.getLineRight(line) else it }
                                    val pad = 6.dp.toPx()

                                    // Backlit pill behind the last recognized word. Painted
                                    // stronger than a hint: on stage, at distance, the whole
                                    // point is to see at a glance which word was just heard.
                                    drawRoundRect(
                                        color = stage.Live.copy(alpha = 0.32f),
                                        topLeft = Offset(left - pad, top),
                                        size = Size(right - left + pad * 2, bottom - top),
                                        cornerRadius = CornerRadius(10.dp.toPx()),
                                    )
                                    // Solid underline pins the exact word, so the eye reads a
                                    // single marked word rather than just a lit line.
                                    val underline = 3.dp.toPx()
                                    drawRoundRect(
                                        color = stage.Live,
                                        topLeft = Offset(left - pad, bottom - underline),
                                        size = Size(right - left + pad * 2, underline),
                                        cornerRadius = CornerRadius(underline / 2f),
                                    )
                                }
                                // Tap any word to read from there — the fast way to
                                // recover a lost position, or to line up a retake.
                                .pointerInput(words, controlsVisible) {
                                    detectTapGestures(
                                        onDoubleTap = { controlsVisible = !controlsVisible },
                                        onTap = { pos ->
                                            val layout = textLayout ?: return@detectTapGestures
                                            val offset = layout.getOffsetForPosition(pos)
                                            moveTo(tokenIndexForOffset(tokenCharStarts, offset))
                                        },
                                    )
                                },
                            onTextLayout = { layout ->
                                textLayout = layout
                                val length = layout.layoutInput.text.length
                                words.indices.forEach { i ->
                                    val offset = tokenCharStarts[i]
                                    if (offset < length) {
                                        val line = layout.getLineForOffset(offset)
                                        wordOffsets[i] = textTopPaddingPx + layout.getLineTop(line)
                                    }
                                }
                                // A slider can trigger a frame before reflow is
                                // complete. Align again using the new geometry.
                                if (currentIndex >= 0 && !manualScrolling) alignToPosition = true
                            },
                        )
                    }

                    if (settings.focusBandEnabled) FocusBand(anchor = anchorFraction)
                }

                // ---- Bottom controls ----
                if (controlsVisible) {
                    Column(modifier = Modifier.fillMaxWidth().background(appearanceColors.surface)) {
                        if (errorMsg != null) {
                            Text(
                                errorMsg!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        ControlBar(
                            isListening = sessionRequested || isListening,
                            counting = countdown > 0,
                            canToggle = !sessionStopping && !stageInputBlocked,
                            buttonPos = buttonPos,
                            statusText = stringResource(
                                R.string.status_line,
                                stringResource(
                                    when {
                                        sessionStopping -> R.string.status_stopping
                                        sessionRequested && !isListening -> R.string.status_preparing
                                        paused -> R.string.status_paused
                                        else -> R.string.status_tracking
                                    }
                                ),
                                currentIndex + 1,
                                words.size,
                            ),
                            onBack = onBack,
                            onToggle = { toggleListening() },
                            cameraEnabled = cameraEnabled,
                            onToggleCamera = { toggleCamera(!cameraEnabled) },
                            onRestart = { moveTo(-1) },
                            onToggleSettings = { showSettings = true },
                            onHideControls = { controlsVisible = false },
                            recording = recording,
                        )
                    }
                }
            }

            if (countdown > 0) {
                CountdownOverlay(countdown)
            }

            modelStatus?.let { status ->
                ModelStatusOverlay(language = language, status = status)
            }

            // The clean stage hides chrome only; its reading and recording
            // state continue unchanged.
            if (controlsVisible) {
                ReadingProgressBar(progress = progress, modifier = Modifier.align(Alignment.TopCenter))
            }

            // Drawn last so the selfie window floats above script and overlays.
            if (cameraEnabled) {
                FloatingCameraWindow(
                    controller = cameraController,
                    onBoundsChange = { cameraBounds = it },
                    onClose = { toggleCamera(false) },
                    controlsVisible = controlsVisible,
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = appSurfaceShape(),
            containerColor = appearanceColors.surface,
            contentColor = appearanceColors.text,
        ) {
            SettingsPanel(
                fontSize = fontSize,
                margin = margin,
                brightness = brightness,
                anchorFraction = anchorFraction,
                mirror = mirror,
                useCountdown = useCountdown,
                buttonPos = buttonPos,
                lineSpacing = settings.lineSpacing,
                focusBandEnabled = settings.focusBandEnabled,
                remoteEnabled = settings.remoteEnabled,
                volumeKeysEnabled = settings.volumeKeysEnabled,
                onFontSize = { updateSettings(settings.copy(fontSize = it)); alignToPosition = true },
                onMargin = { updateSettings(settings.copy(marginPercent = it)); alignToPosition = true },
                onBrightness = { updateSettings(settings.copy(brightness = it)) },
                onAnchorFraction = { updateSettings(settings.copy(anchorFraction = it)); alignToPosition = true },
                onMirror = { updateSettings(settings.copy(mirror = it)) },
                onCountdown = { updateSettings(settings.copy(useCountdown = it)) },
                onButtonPos = { updateSettings(settings.copy(buttonPosition = it.name)) },
                onLineSpacing = { updateSettings(settings.copy(lineSpacing = it)); alignToPosition = true },
                onFocusBand = { updateSettings(settings.copy(focusBandEnabled = it)) },
                onRemoteEnabled = { updateSettings(settings.copy(remoteEnabled = it)) },
                onVolumeKeys = { updateSettings(settings.copy(volumeKeysEnabled = it)) },
                onResetSettings = { updateSettings(ReadingSettings.defaults(mode)); alignToPosition = true },
            )
        }
    }

    // After tracking ends, let the speaker keep or throw away what was recorded
    // before it is written anywhere permanent. Waits for the video (if any) to
    // finish encoding so the pair is saved or discarded together.
    val audioToDecide = pendingAudio
    if (audioToDecide != null && !awaitingVideo) {
        val video = pendingVideo
        val withVideo = video != null
        AlertDialog(
            // No outside-tap dismiss: a recording must be explicitly kept or not.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.save_recording_title)) },
            text = {
                Text(
                    stringResource(
                        if (withVideo) R.string.save_recording_message_av
                        else R.string.save_recording_message
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (decidingRecording) return@TextButton
                    decidingRecording = true
                    pendingAudio = null
                    pendingVideo = null
                    scope.launch {
                        try {
                            val savedAudio = withContext(Dispatchers.IO) { recordingStore.save(audioToDecide) }
                            val savedVideo = video?.let { withContext(Dispatchers.IO) { recordingStore.save(it) } }
                            val msg = if (savedVideo != null) {
                                context.getString(R.string.recording_saved_av, savedAudio.name, savedVideo.name)
                            } else {
                                context.getString(R.string.audio_saved, savedAudio.name)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } finally {
                            decidingRecording = false
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (decidingRecording) return@TextButton
                    decidingRecording = true
                    pendingAudio = null
                    pendingVideo = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                recordingStore.discard(audioToDecide)
                                video?.let { recordingStore.discard(it) }
                            }
                        } finally {
                            decidingRecording = false
                        }
                    }
                }) { Text(stringResource(R.string.discard)) }
            },
            shape = appSurfaceShape(),
            containerColor = appearanceColors.surface,
            titleContentColor = appearanceColors.text,
            textContentColor = appearanceColors.muted,
        )
    }
}

/**
 * Thin bar pinned to the top edge showing how far through the script the
 * speaker has read. Deliberately minimal — a hairline, not a Material
 * progress control — so it reads at a glance without competing with the
 * script for attention, and stays visible regardless of what else is on
 * screen (settings sheet aside, since that's a deliberate full takeover).
 */
@Composable
private fun ReadingProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val stage = LocalAppearance.current.stage
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .background(stage.PanelRaised),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(stage.Live),
        )
    }
}

/**
 * Dims the script above and below the line being read. Professional prompters
 * light a band rather than colouring the current word: the eye follows a steady
 * lit region instead of chasing a moving marker, which is calmer to read from
 * and forgiving when the recogniser is a word or two out.
 *
 * Painted as a scrim over the text rather than as text colour, so scrolling
 * never touches layout. It carries no pointer handler, leaving taps to the
 * script beneath it.
 */
@Composable
private fun FocusBand(anchor: Float) {
    val stage = LocalAppearance.current.stage
    val dim = stage.Background.copy(alpha = DIM_ALPHA)

    // Gradient stops must stay inside 0..1 and never run backwards. With the
    // band near an edge the raw offsets fall outside that range, so each one is
    // clamped against the previous rather than against 0 alone.
    val fadeInStart = (anchor - BAND_HALF_HEIGHT - BAND_FADE).coerceIn(0f, 1f)
    val bandStart = (anchor - BAND_HALF_HEIGHT).coerceIn(fadeInStart, 1f)
    val bandEnd = (anchor + BAND_HALF_HEIGHT).coerceIn(bandStart, 1f)
    val fadeOutEnd = (anchor + BAND_HALF_HEIGHT + BAND_FADE).coerceIn(bandEnd, 1f)

    val stops = buildList {
        add(0f to dim)
        if (fadeInStart > 0f) add(fadeInStart to dim)
        add(bandStart to Color.Transparent)
        add(bandEnd to Color.Transparent)
        if (fadeOutEnd < 1f) add(fadeOutEnd to dim)
        add(1f to dim)
    }.toTypedArray()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = stops))
    )
}

@Composable
private fun CountdownOverlay(value: Int) {
    val stage = LocalAppearance.current.stage
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC00B0B0C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                color = stage.Live,
                fontSize = 140.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.get_ready),
                color = stage.Muted,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun ControlBar(
    isListening: Boolean,
    counting: Boolean,
    canToggle: Boolean,
    buttonPos: ButtonPos,
    statusText: String,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    cameraEnabled: Boolean,
    onToggleCamera: () -> Unit,
    onRestart: () -> Unit,
    onToggleSettings: () -> Unit,
    onHideControls: () -> Unit,
    recording: Boolean,
) {
    val appearance = LocalAppearance.current
    val stage = appearance.stage
    val colors = appearance.colors
    val bigButtonAlignment = when (buttonPos) {
        ButtonPos.LEFT -> Alignment.CenterStart
        ButtonPos.CENTER -> Alignment.Center
        ButtonPos.RIGHT -> Alignment.CenterEnd
    }
    val live = isListening || counting

    Column(modifier = Modifier.fillMaxWidth().background(colors.surface).appFrame()) {
        AppHeader(
            title = stringResource(R.string.stage_controls_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onHideControls, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Fullscreen, stringResource(R.string.hide_controls))
                }
                IconButton(onClick = onToggleSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                }
            },
        )

        // A separate action row leaves room for every control on narrow phones.
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = bigButtonAlignment,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onToggleCamera,
                    enabled = !recording,
                    modifier = Modifier.size(48.dp).appFrame(),
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(
                            if (cameraEnabled) R.string.hide_camera else R.string.show_camera
                        ),
                        tint = if (cameraEnabled) colors.accent else colors.text,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Button(
                    onClick = onToggle,
                    enabled = canToggle,
                    modifier = Modifier.heightIn(min = 60.dp).widthIn(min = 144.dp).appFrame(),
                    shape = appSurfaceShape(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (live) stage.Stop else stage.Go,
                        contentColor = if (live) stage.OnStop else stage.OnGo,
                    ),
                ) {
                    Icon(
                        if (live) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (live) R.string.stop else R.string.start),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StageIconButton(
                    icon = Icons.Filled.RestartAlt,
                    description = stringResource(R.string.restart_script),
                    onClick = onRestart,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            // A steady red dot means the mic is being recorded to a file — the
            // recording is automatic, so this is a status light, not a control.
            if (recording) {
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = stringResource(R.string.recording_in_progress),
                    tint = stage.Stop,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StageIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalAppearance.current.colors
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).appFrame()) {
        Icon(
            icon,
            contentDescription = description,
            tint = colors.text,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SettingsPanel(
    fontSize: Float,
    margin: Float,
    brightness: Float,
    anchorFraction: Float,
    mirror: Boolean,
    useCountdown: Boolean,
    buttonPos: ButtonPos,
    lineSpacing: Float,
    focusBandEnabled: Boolean,
    remoteEnabled: Boolean,
    volumeKeysEnabled: Boolean,
    onFontSize: (Float) -> Unit,
    onMargin: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onAnchorFraction: (Float) -> Unit,
    onMirror: (Boolean) -> Unit,
    onCountdown: (Boolean) -> Unit,
    onButtonPos: (ButtonPos) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onFocusBand: (Boolean) -> Unit,
    onRemoteEnabled: (Boolean) -> Unit,
    onVolumeKeys: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
) {
    val appearance = LocalAppearance.current
    val colors = appearance.colors
    var showAppearance by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        AppOutlinedButton(
            onClick = { showAppearance = true },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Icon(Icons.Filled.Palette, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.stage_appearance_title))
                Text(
                    stringResource(
                        R.string.stage_appearance_summary,
                        appearance.style.displayName(),
                        appearance.palette.displayName(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SettingSlider(
            label = stringResource(R.string.setting_font),
            value = fontSize,
            range = 24f..96f,
            readout = stringResource(R.string.readout_sp, fontSize.roundToInt()),
            onValueChange = onFontSize,
        )
        SettingSlider(
            label = stringResource(R.string.setting_margin),
            value = margin,
            range = 0f..40f,
            readout = stringResource(R.string.readout_percent, margin.roundToInt()),
            onValueChange = onMargin,
        )
        Text(
            stringResource(R.string.setting_margin_caption),
            fontSize = 12.sp,
            color = colors.muted,
        )
        SettingSlider(
            label = stringResource(R.string.setting_line_spacing),
            value = lineSpacing,
            range = 1f..2f,
            readout = stringResource(R.string.readout_line_spacing, lineSpacing),
            onValueChange = onLineSpacing,
        )
        SettingSlider(
            label = stringResource(R.string.setting_screen),
            value = brightness,
            range = MIN_BRIGHTNESS..1f,
            readout = stringResource(R.string.readout_percent, (brightness * 100).roundToInt()),
            onValueChange = onBrightness,
        )
        SettingSlider(
            label = stringResource(R.string.setting_read_line),
            value = anchorFraction,
            range = 0.05f..0.6f,
            readout = stringResource(R.string.readout_percent, (anchorFraction * 100).roundToInt()),
            onValueChange = onAnchorFraction,
        )

        SettingSwitch(
            stringResource(R.string.setting_focus_band),
            stringResource(R.string.setting_focus_band_caption),
            focusBandEnabled,
            onFocusBand,
        )
        SettingSwitch(
            stringResource(R.string.setting_mirror),
            stringResource(R.string.setting_mirror_caption),
            mirror,
            onMirror,
        )
        SettingSwitch(
            stringResource(R.string.setting_countdown),
            stringResource(R.string.setting_countdown_caption),
            useCountdown,
            onCountdown,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.setting_button),
                fontSize = 14.sp,
                color = colors.muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ButtonPos.entries.forEach { pos ->
                    val selected = pos == buttonPos
                    Button(
                        onClick = { onButtonPos(pos) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).appFrame(),
                        shape = appSurfaceShape(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (selected) colors.accent else colors.surfaceRaised,
                            contentColor = if (selected) colors.onAccent else colors.text,
                        ),
                    ) {
                        Text(stringResource(pos.labelRes), fontSize = 13.sp)
                    }
                }
            }
        }
        SettingSwitch(
            stringResource(R.string.setting_remote),
            stringResource(R.string.setting_remote_caption),
            remoteEnabled,
            onRemoteEnabled,
        )
        if (remoteEnabled) {
            SettingSwitch(
                stringResource(R.string.setting_volume_keys),
                stringResource(R.string.setting_volume_keys_caption),
                volumeKeysEnabled,
                onVolumeKeys,
            )
            Text(
                stringResource(R.string.remote_shortcuts),
                fontSize = 13.sp,
                color = colors.muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Text(
            stringResource(R.string.clean_screen_hint),
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.settings_saved_caption),
            fontSize = 12.sp,
            color = colors.muted,
        )
        TextButton(onClick = onResetSettings) {
            Text(stringResource(R.string.reset_reading_settings), color = colors.accent)
        }
    }
    if (showAppearance) AppearanceDialog(onDismissRequest = { showAppearance = false })
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onValueChange: (Float) -> Unit,
) {
    val colors = LocalAppearance.current.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.muted, modifier = Modifier.width(96.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surfaceRaised,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            readout,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalAppearance.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = colors.text)
            Text(caption, fontSize = 12.sp, color = colors.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
            ),
        )
    }
}

@Composable
private fun ModelStatusOverlay(language: Language, status: ModelStatus) {
    val stage = LocalAppearance.current.stage
    val downloading = status as? ModelStatus.Downloading
    val title = if (downloading != null) {
        stringResource(R.string.model_downloading, language.englishName)
    } else {
        stringResource(R.string.model_loading, language.englishName)
    }
    val subtitle = if (downloading != null) {
        stringResource(R.string.model_downloading_subtitle, language.approxMb)
    } else {
        stringResource(R.string.model_loading_subtitle)
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
            CircularProgressIndicator(color = stage.Live)
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Text(text = subtitle, color = stage.Muted, fontSize = 13.sp)
            if (fraction in 0f..1f) {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = stage.Live,
                    trackColor = stage.PanelRaised,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.readout_percent, (fraction * 100).roundToInt()),
                    color = stage.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
