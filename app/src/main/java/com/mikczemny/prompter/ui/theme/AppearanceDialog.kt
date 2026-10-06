package com.mikczemny.prompter.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikczemny.prompter.R

@Composable
fun UiStyle.displayName(): String = stringResource(when (this) {
    UiStyle.WORKBENCH -> R.string.appearance_workbench
    UiStyle.COMMODORE -> R.string.appearance_commodore
    UiStyle.WINDOWS_311 -> R.string.appearance_windows
    UiStyle.MANUSCRIPT -> R.string.appearance_manuscript
    UiStyle.MODERN -> R.string.appearance_modern
})

@Composable
fun UiPalette.displayName(): String = stringResource(when (this) {
    UiPalette.ORIGINAL -> R.string.appearance_original
    UiPalette.BOTTLE_GOLD -> R.string.appearance_bottle_gold
    UiPalette.PAPER -> R.string.appearance_paper
    UiPalette.GRAPHITE -> R.string.appearance_graphite
    UiPalette.AMBER -> R.string.appearance_amber
    UiPalette.ICE -> R.string.appearance_ice
    UiPalette.PLUM -> R.string.appearance_plum
})

@Composable
private fun UiStyle.description(): String = stringResource(when (this) {
    UiStyle.WORKBENCH -> R.string.appearance_workbench_description
    UiStyle.COMMODORE -> R.string.appearance_commodore_description
    UiStyle.WINDOWS_311 -> R.string.appearance_windows_description
    UiStyle.MANUSCRIPT -> R.string.appearance_manuscript_description
    UiStyle.MODERN -> R.string.appearance_modern_description
})

@Composable
fun AppearanceDialog(onDismissRequest: () -> Unit) {
    val appearance = LocalAppearance.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val columnCount = if (maxWidth < 400.dp || LocalDensity.current.fontScale > 1.3f) 1 else 2
            AppWindow(
                title = stringResource(R.string.appearance_title),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = maxHeight),
                actions = {
                    AppIconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, stringResource(R.string.appearance_close))
                    }
                },
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.appearance_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appearance.colors.muted,
                    )
                    Text(stringResource(R.string.appearance_style), style = MaterialTheme.typography.titleLarge)
                    UiStyle.entries.chunked(columnCount).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { style ->
                                AppearanceChoice(
                                    label = style.displayName(),
                                    selected = style == appearance.style,
                                    modifier = Modifier.weight(1f),
                                    onClick = { appearance.selectStyle(style) },
                                )
                            }
                            if (row.size < columnCount) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        appearance.style.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = appearance.colors.muted,
                    )
                    AppearancePreview()
                    Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleLarge)
                    UiPalette.entries.chunked(columnCount).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { palette ->
                                val swatch = paletteSpec(appearance.style, palette, appearance.systemDarkTheme)
                                AppearanceChoice(
                                    label = palette.displayName(),
                                    selected = palette == appearance.palette,
                                    modifier = Modifier.weight(1f),
                                    onClick = { appearance.selectPalette(palette) },
                                    swatch = Color(swatch.surface) to Color(swatch.accent),
                                )
                            }
                            if (row.size < columnCount) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        stringResource(R.string.appearance_stage_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = appearance.colors.muted,
                    )
                }
                AppButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appearance_done))
                }
            }
        }
    }
}

@Composable
private fun AppearanceChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    swatch: Pair<Color, Color>? = null,
) {
    val semantics = modifier.semantics { this.selected = selected }
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (swatch != null) {
            Row(Modifier.border(1.dp, LocalAppearance.current.colors.outline)) {
                Box(Modifier.size(width = 8.dp, height = 20.dp).background(swatch.first))
                Box(Modifier.size(width = 8.dp, height = 20.dp).background(swatch.second))
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
    if (selected) AppTonalButton(onClick, semantics, content = content)
    else AppOutlinedButton(onClick, semantics, content = content)
}

/** Uses the actual components rather than a screenshot that can drift from the selected UI. */
@Composable
private fun AppearancePreview() {
    val appearance = LocalAppearance.current
    val previewLabel = stringResource(R.string.appearance_preview)
    Column(
        modifier = Modifier.fillMaxWidth().background(appearance.colors.desktop)
            .padding(10.dp).semantics { contentDescription = previewLabel },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            previewLabel,
            color = appearance.colors.onDesktop,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        AppWindow(title = stringResource(R.string.appearance_preview_title)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.appearance_preview_heading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.appearance_preview_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = appearance.colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewButtonSample(
                        stringResource(R.string.appearance_preview_start),
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewButtonSample(
                        stringResource(R.string.appearance_preview_library),
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A visual sample has no click handler, so accessibility does not announce a dead button. */
@Composable
private fun PreviewButtonSample(label: String, primary: Boolean, modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    val colors = appearance.colors
    val windows = appearance.style == UiStyle.WINDOWS_311
    val background = if (windows) colors.surfaceRaised else if (primary) colors.accent else colors.surface
    val foreground = if (windows || !primary) colors.text else colors.onAccent
    Row(
        modifier = modifier.background(background, appSurfaceShape()).appFrame().padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary) {
            Icon(Icons.Filled.PlayArrow, null, tint = foreground, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}
