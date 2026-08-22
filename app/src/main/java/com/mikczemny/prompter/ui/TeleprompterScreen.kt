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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.annotation.StringRes
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.mikczemny.prompter.ui.theme.StageColors
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
import kotlin.math.roundToInt

private const val PX_PER_SEC_MAX = 900f
private const val SCROLL_LERP = 0.12f

/**
 * Where in the viewport the line being spoken is held, as a fraction of height.
 * Near the top by default so the bulk of the panel shows what is coming next —
 * the speaker needs to read ahead, not to admire what they already said.
 * Adjustable, because the right spot depends on where the camera sits.
 */
private const val DEFAULT_ANCHOR = 0.15f

/** Half-height of the fully lit reading band, as a fraction of viewport height. */
private const val BAND_HALF_HEIGHT = 0.11f

/** How far the dimming fades in above and below the band. */
private const val BAND_FADE = 0.11f

/** How dark the script goes outside the band. */
private const val DIM_ALPHA = 0.78f

private const val COUNTDOWN_FROM = 3

/** Height of the always-visible read-through progress bar at the top edge. */
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
    val context = LocalContext.current
    val resources = LocalResources.current

    val matcher = remember(script) { ScriptMatcher(script) }
    val words = matcher.displayTokens

    var fontSize by remember { mutableFloatStateOf(44f) }
    var margin by remember { mutableFloatStateOf(8f) } // percent
    var mirror by remember { mutableStateOf(false) }
    var anchorFraction by remember { mutableFloatStateOf(DEFAULT_ANCHOR) }
    var useCountdown by remember { mutableStateOf(true) }
    var buttonPos by remember { mutableStateOf(ButtonPos.CENTER) }
    var showSettings by remember { mutableStateOf(false) }
    var showMicDisclosure by remember { mutableStateOf(false) }
    var showCameraDisclosure by remember { mutableStateOf(false) }
    var leaveAfterRecordingDecision by remember { mutableStateOf(false) }
    // Full brightness by default: the prompter is normally read at arm's length
    // and often against daylight.
    var brightness by remember { mutableFloatStateOf(1f) }

    KeepScreenBright(brightness)
    ImmersiveStage()

    var currentIndex by remember { mutableIntStateOf(-1) }
    var paused by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
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
        if (!on && recording) return
        if (!on) {
            cameraEnabled = false
            cameraBounds = null
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) cameraEnabled = true else showCameraDisclosure = true
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
                    val state = matcher.pushTranscript(text, isFinal, ts)
                    currentIndex = state.currentIndex
                    paused = state.paused
                    progress = state.progress.toFloat()
                    val avgPxPerWord = contentHeight / max(words.size, 1)
                    val targetPxPerSec = state.wordsPerSecond.toFloat() * avgPxPerWord
                    velocity.floatValue = min(PX_PER_SEC_MAX, max(0f, targetPxPerSec))
                }
            },
            onError = { msg -> mainHandler.post { errorMsg = msg } },
            onListeningChanged = { listening -> mainHandler.post { isListening = listening } },
            onModelStatus = { status -> mainHandler.post { modelStatus = status } },
            onInterrupted = {
                mainHandler.post {
                    errorMsg = resources.getString(R.string.recording_interrupted)
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
        if (granted) {
            errorMsg = null
            if (useCountdown) countdown = COUNTDOWN_FROM else recognizer.start(language)
        } else {
            errorMsg = resources.getString(R.string.mic_permission_denied)
        }
    }

    fun toggleListening() {
        if (isListening || countdown > 0) {
            countdown = 0
            recognizer.stop()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            showMicDisclosure = true
            return
        }
        errorMsg = null
        if (useCountdown) countdown = COUNTDOWN_FROM else recognizer.start(language)
    }

    fun requestBack() {
        if (isListening || countdown > 0 || recording) {
            leaveAfterRecordingDecision = true
            countdown = 0
            recognizer.stop()
        } else if (pendingAudio != null || awaitingVideo) {
            leaveAfterRecordingDecision = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::requestBack)

    // Never keep the mic or camera recording after the app leaves the
    // foreground. The finished take is offered for Save/Discard on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, recognizer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (isListening || countdown > 0)) {
                countdown = 0
                recognizer.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Recording follows tracking: it auto-starts once the mic is live, and when
    // tracking ends (Stop or an interruption, which finalizes the WAV inside the
    // recognizer) the finished file is parked for the keep/discard prompt.
    LaunchedEffect(isListening) {
        if (isListening && !recording) {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
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
        if (countdown <= 0) return@LaunchedEffect
        delay(1000)
        countdown -= 1
        if (countdown == 0) recognizer.start(language)
    }

    /** Sends the pointer to [index] (-1 = back to the top) and parks tracking there. */
    fun moveTo(index: Int) {
        matcher.jumpTo(index)
        currentIndex = index
        paused = true
        velocity.floatValue = 0f
        progress = if (words.isEmpty()) 0f else (index + 1).toFloat() / words.size
    }

    // Smooth scroll loop: blends velocity-based motion with position correction
    // toward the actually tracked word so drift self-heals.
    LaunchedEffect(Unit) {
        var lastTs = 0L
        while (true) {
            withFrameNanos { ts ->
                val dt = if (lastTs == 0L) 0f else min(0.05f, (ts - lastTs) / 1_000_000_000f)
                lastTs = ts

                val started = currentIndex >= 0 && currentIndex < wordOffsets.size
                val velocityStep = if (paused || !started) 0f else velocity.floatValue * dt

                // Before the first match — and after a reset — the anchor is the
                // top of the script rather than a tracked word. A token that has
                // not been laid out yet has no anchor at all, so only the
                // velocity term applies until layout catches up.
                val anchorTop = if (started) wordOffsets[currentIndex] else 0f
                var correction = 0f
                if (!anchorTop.isNaN()) {
                    val targetScroll =
                        if (started) anchorTop - viewportHeight * anchorFraction else 0f
                    correction = (targetScroll - scrollState.value) * SCROLL_LERP
                }
                val next = (scrollState.value + velocityStep + correction)
                    .coerceIn(0f, scrollState.maxValue.toFloat())
                scrollState.dispatchRawDelta(next - scrollState.value)

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
        modifier = Modifier.fillMaxSize(),
        color = StageColors.Background,
        contentColor = StageColors.Foreground,
    ) {
        val density = LocalDensity.current
        val camera = cameraBounds.takeIf { cameraEnabled }
        val cameraGapPx = with(density) { 12.dp.toPx() }
        val visualStartPx = if (camera != null && camera.centerX <= camera.containerWidth / 2f) {
            camera.right + cameraGapPx
        } else {
            0f
        }
        val visualEndPx = if (camera != null && camera.centerX > camera.containerWidth / 2f) {
            camera.containerWidth - camera.left + cameraGapPx
        } else {
            0f
        }
        // The entire script layer is mirrored for beam-splitter glass, so its
        // logical padding has to be swapped to preserve the visual exclusion.
        val logicalStartPx = if (mirror) visualEndPx else visualStartPx
        val logicalEndPx = if (mirror) visualStartPx else visualEndPx
        val textStartPadding = with(density) { maxOf(margin.dp.toPx(), logicalStartPx).toDp() }
        val textEndPadding = with(density) { maxOf(margin.dp.toPx(), logicalEndPx).toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {

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
                                start = textStartPadding,
                                end = textEndPadding,
                                top = 24.dp,
                                bottom = 400.dp,
                            ),
                    ) {
                        Text(
                            text = script,
                            color = StageColors.Foreground,
                            style = TextStyle(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.35f).sp,
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
                                        color = StageColors.Live.copy(alpha = 0.32f),
                                        topLeft = Offset(left - pad, top),
                                        size = Size(right - left + pad * 2, bottom - top),
                                        cornerRadius = CornerRadius(10.dp.toPx()),
                                    )
                                    // Solid underline pins the exact word, so the eye reads a
                                    // single marked word rather than just a lit line.
                                    val underline = 3.dp.toPx()
                                    drawRoundRect(
                                        color = StageColors.Live,
                                        topLeft = Offset(left - pad, bottom - underline),
                                        size = Size(right - left + pad * 2, underline),
                                        cornerRadius = CornerRadius(underline / 2f),
                                    )
                                }
                                // Tap any word to read from there — the fast way to
                                // recover a lost position, or to line up a retake.
                                .pointerInput(words) {
                                    detectTapGestures { pos ->
                                        val layout = textLayout ?: return@detectTapGestures
                                        val offset = layout.getOffsetForPosition(pos)
                                        moveTo(tokenIndexForOffset(tokenCharStarts, offset))
                                    }
                                },
                            onTextLayout = { layout ->
                                textLayout = layout
                                val length = layout.layoutInput.text.length
                                words.indices.forEach { i ->
                                    val offset = tokenCharStarts[i]
                                    if (offset < length) {
                                        val line = layout.getLineForOffset(offset)
                                        wordOffsets[i] = layout.getLineTop(line)
                                    }
                                }
                            },
                        )
                    }

                    FocusBand(anchor = anchorFraction)
                }

                // ---- Bottom controls ----
                Column(modifier = Modifier.fillMaxWidth().background(StageColors.Background)) {
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
                        counting = countdown > 0,
                        buttonPos = buttonPos,
                        statusText = stringResource(
                            R.string.status_line,
                            stringResource(
                                if (paused) R.string.status_paused else R.string.status_tracking
                            ),
                            currentIndex + 1,
                            words.size,
                        ),
                        onBack = ::requestBack,
                        onToggle = { toggleListening() },
                        cameraEnabled = cameraEnabled,
                        onToggleCamera = { toggleCamera(!cameraEnabled) },
                        onRestart = { moveTo(-1) },
                        onToggleSettings = { showSettings = true },
                        recording = recording,
                    )
                }
            }

            if (countdown > 0) {
                CountdownOverlay(countdown)
            }

            modelStatus?.let { status ->
                ModelStatusOverlay(language = language, status = status)
            }

            // Thin, always-on read-through indicator pinned to the top edge —
            // drawn last so it stays visible over the countdown and model
            // status overlays too, not just the reading stage.
            ReadingProgressBar(progress = progress, modifier = Modifier.align(Alignment.TopCenter))

            // Drawn last so the selfie window floats above script and overlays.
            if (cameraEnabled) {
                FloatingCameraWindow(
                    controller = cameraController,
                    onBoundsChange = { cameraBounds = it },
                    onClose = { toggleCamera(false) },
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StageColors.Panel,
            contentColor = StageColors.Foreground,
        ) {
            SettingsPanel(
                fontSize = fontSize,
                margin = margin,
                brightness = brightness,
                anchorFraction = anchorFraction,
                mirror = mirror,
                useCountdown = useCountdown,
                buttonPos = buttonPos,
                onFontSize = { fontSize = it },
                onMargin = { margin = it },
                onBrightness = { brightness = it },
                onAnchorFraction = { anchorFraction = it },
                onMirror = { mirror = it },
                onCountdown = { useCountdown = it },
                onButtonPos = { buttonPos = it },
            )
        }
    }

    if (showMicDisclosure) {
        AlertDialog(
            onDismissRequest = { showMicDisclosure = false },
            title = { Text(stringResource(R.string.mic_disclosure_title)) },
            text = { Text(stringResource(R.string.mic_disclosure_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showMicDisclosure = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showMicDisclosure = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }

    if (showCameraDisclosure) {
        AlertDialog(
            onDismissRequest = { showCameraDisclosure = false },
            title = { Text(stringResource(R.string.camera_disclosure_title)) },
            text = { Text(stringResource(R.string.camera_disclosure_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCameraDisclosure = false
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showCameraDisclosure = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
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
                    pendingAudio = null
                    pendingVideo = null
                    scope.launch {
                        val savedAudio = withContext(Dispatchers.IO) { recordingStore.save(audioToDecide) }
                        val savedVideo = video?.let { withContext(Dispatchers.IO) { recordingStore.save(it) } }
                        val msg = if (savedVideo != null) {
                            resources.getString(R.string.recording_saved_av, savedAudio.name, savedVideo.name)
                        } else {
                            resources.getString(R.string.audio_saved, savedAudio.name)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (leaveAfterRecordingDecision) onBack()
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingAudio = null
                    pendingVideo = null
                    scope.launch(Dispatchers.IO) {
                        recordingStore.discard(audioToDecide)
                        video?.let { recordingStore.discard(it) }
                        if (leaveAfterRecordingDecision) {
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    }
                }) { Text(stringResource(R.string.discard)) }
            },
            containerColor = StageColors.Panel,
            titleContentColor = StageColors.Foreground,
            textContentColor = StageColors.Muted,
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_BAR_HEIGHT)
            .background(StageColors.PanelRaised),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(StageColors.Live),
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
    val dim = StageColors.Background.copy(alpha = DIM_ALPHA)

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xC00B0B0C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                color = StageColors.Live,
                fontSize = 140.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.get_ready),
                color = StageColors.Muted,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun ControlBar(
    isListening: Boolean,
    counting: Boolean,
    buttonPos: ButtonPos,
    statusText: String,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    cameraEnabled: Boolean,
    onToggleCamera: () -> Unit,
    onRestart: () -> Unit,
    onToggleSettings: () -> Unit,
    recording: Boolean,
) {
    val bigButtonAlignment = when (buttonPos) {
        ButtonPos.LEFT -> Alignment.CenterStart
        ButtonPos.CENTER -> Alignment.Center
        ButtonPos.RIGHT -> Alignment.CenterEnd
    }
    val live = isListening || counting

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.back_to_menu),
                onClick = onBack,
            )

            // Big Start/Stop, positioned per user setting.
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = bigButtonAlignment,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleCamera,
                        enabled = !recording,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(
                                if (cameraEnabled) R.string.hide_camera else R.string.show_camera
                            ),
                            tint = if (cameraEnabled) StageColors.Live else StageColors.Foreground,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.height(60.dp).widthIn(min = 150.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (live) StageColors.Stop else StageColors.Go,
                            contentColor = Color.White,
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
                }
            }

            StageIconButton(
                icon = Icons.Filled.RestartAlt,
                description = stringResource(R.string.restart_script),
                onClick = onRestart,
            )
            StageIconButton(
                icon = Icons.Filled.Settings,
                description = stringResource(R.string.settings),
                onClick = onToggleSettings,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
        ) {
            // A steady red dot means the mic is being recorded to a file — the
            // recording is automatic, so this is a status light, not a control.
            if (recording) {
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = stringResource(R.string.recording_in_progress),
                    tint = StageColors.Stop,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(text = statusText, fontSize = 12.sp, color = StageColors.Muted)
        }
    }
}

@Composable
private fun StageIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = StageColors.Foreground,
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
    onFontSize: (Float) -> Unit,
    onMargin: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onAnchorFraction: (Float) -> Unit,
    onMirror: (Boolean) -> Unit,
    onCountdown: (Boolean) -> Unit,
    onButtonPos: (ButtonPos) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.settings),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = StageColors.Foreground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

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
            range = 0f..30f,
            readout = stringResource(R.string.readout_dp, margin.roundToInt()),
            onValueChange = onMargin,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                stringResource(R.string.setting_button),
                fontSize = 14.sp,
                color = StageColors.Muted,
                modifier = Modifier.width(96.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonPos.entries.forEach { pos ->
                    val selected = pos == buttonPos
                    Button(
                        onClick = { onButtonPos(pos) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (selected) StageColors.Go else StageColors.PanelRaised,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(pos.labelRes), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = StageColors.Muted, modifier = Modifier.width(96.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            // Pinned to the stage palette: the sheet sits over the black stage,
            // where the wallpaper-derived accent would clash with the controls.
            colors = SliderDefaults.colors(
                thumbColor = StageColors.Live,
                activeTrackColor = StageColors.Go,
                inactiveTrackColor = StageColors.PanelRaised,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            readout,
            fontSize = 12.sp,
            color = StageColors.Muted,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = StageColors.Foreground)
            Text(caption, fontSize = 12.sp, color = StageColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StageColors.Go,
            ),
        )
    }
}

@Composable
private fun ModelStatusOverlay(language: Language, status: ModelStatus) {
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
            CircularProgressIndicator(color = StageColors.Live)
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Text(text = subtitle, color = StageColors.Muted, fontSize = 13.sp)
            if (fraction in 0f..1f) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.readout_percent, (fraction * 100).roundToInt()),
                    color = StageColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
