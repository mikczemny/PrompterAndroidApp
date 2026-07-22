package com.mikczemny.prompter.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Keeps the screen on and pins its brightness while this composable is in the
 * tree. Both of Android's defaults work against a teleprompter: the display
 * dims and sleeps on the inactivity timer precisely because a speaker reading
 * from it isn't touching anything, and auto-brightness drifts under stage or
 * window light mid-take.
 *
 * [brightness] is 0..1. The window override and the keep-awake flag are both
 * released on dispose, so leaving the prompter restores normal system behaviour.
 */
@Composable
fun KeepScreenBright(brightness: Float) {
    val window = LocalContext.current.findActivity()?.window

    DisposableEffect(window, brightness) {
        if (window == null) return@DisposableEffect onDispose { }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = brightness.coerceIn(MIN_BRIGHTNESS, 1f)
        }

        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

/** Never pin the screen fully dark — that would leave the user with no way back. */
const val MIN_BRIGHTNESS = 0.15f
