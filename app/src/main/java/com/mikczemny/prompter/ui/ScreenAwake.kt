package com.mikczemny.prompter.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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

/**
 * Hides the status and navigation bars for as long as this composable is in the
 * tree, giving the script the whole panel. The bars stay reachable with a swipe
 * — a clock and a battery icon are not worth the lines of script they cost, but
 * neither is trapping the speaker.
 */
@Composable
fun ImmersiveStage() {
    val view = LocalView.current
    val window = LocalContext.current.findActivity()?.window

    DisposableEffect(window, view) {
        if (window == null) return@DisposableEffect onDispose { }

        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
        }
    }
}

/** Never pin the screen fully dark — that would leave the user with no way back. */
const val MIN_BRIGHTNESS = 0.15f
