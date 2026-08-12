package com.mikczemny.prompter.ui

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikczemny.prompter.R
import com.mikczemny.prompter.ui.theme.StageColors
import kotlin.math.roundToInt

data class CameraWindowBounds(
    val left: Float,
    val width: Float,
    val containerWidth: Float,
) {
    val right: Float get() = left + width
    val centerX: Float get() = left + width / 2f
}

/**
 * Live front-camera preview driven by a [LifecycleCameraController]. The
 * controller is owned by the caller so the same camera session can also record
 * video; here it only feeds the viewfinder. PreviewView mirrors the front
 * camera the way a mirror would, which is what a selfie preview should look
 * like.
 */
@Composable
fun CameraPreview(controller: LifecycleCameraController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        previewView.controller = controller
        onDispose { controller.unbind() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * A draggable, pinch-resizable selfie preview floating over the prompter. Drag
 * with one finger to move it clear of the line being read; pinch to make it
 * bigger or smaller. [onClose] hides it.
 */
@Composable
fun FloatingCameraWindow(
    controller: LifecycleCameraController,
    onBoundsChange: (CameraWindowBounds) -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        // scale 1f is the smallest window; pinch grows it up to most of the
        // screen. windowSize turns a scale into a bounded, 3:4 portrait frame.
        var scale by remember(maxW, maxH) { mutableFloatStateOf(1.8f) }
        val (winW, winH) = windowSize(scale, maxW, maxH)

        var offset by remember(maxW, maxH) {
            mutableStateOf(Offset((maxW - winW - 16f).coerceAtLeast(0f), 48f))
        }

        LaunchedEffect(offset.x, winW, maxW) {
            onBoundsChange(CameraWindowBounds(offset.x, winW, maxW))
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(with(density) { winW.toDp() }, with(density) { winH.toDp() })
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(2.dp, StageColors.Live), RoundedCornerShape(14.dp)),
        ) {
            CameraPreview(controller = controller, modifier = Modifier.fillMaxSize())

            // PreviewView is an Android view and consumes pinch gestures as
            // camera zoom. This Compose layer sits above it and deliberately
            // owns all transforms: one finger moves the window, two fingers
            // resize the window. The camera's field of view stays unchanged.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(maxW, maxH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val (w, h) = windowSize(scale, maxW, maxH)
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(0f, (maxW - w).coerceAtLeast(0f)),
                            (offset.y + pan.y).coerceIn(0f, (maxH - h).coerceAtLeast(0f)),
                        )
                    }
                },
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color(0x99000000), RoundedCornerShape(50)),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.hide_camera),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f

/**
 * Turns a pinch [scale] into a portrait (3:4) window size in pixels, bounded so
 * it never gets smaller than a fifth of the width nor larger than most of the
 * screen in either dimension.
 */
private fun windowSize(scale: Float, maxW: Float, maxH: Float): Pair<Float, Float> {
    var w = (maxW * 0.22f * scale).coerceIn(maxW * 0.20f, maxW * 0.85f)
    var h = w * 4f / 3f
    if (h > maxH * 0.85f) {
        h = maxH * 0.85f
        w = h * 3f / 4f
    }
    return w to h
}
