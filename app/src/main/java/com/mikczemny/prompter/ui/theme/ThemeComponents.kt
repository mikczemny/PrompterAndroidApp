package com.mikczemny.prompter.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikczemny.prompter.R

@Composable
fun appSurfaceShape(): Shape = when (LocalAppearance.current.style) {
    UiStyle.WORKBENCH, UiStyle.COMMODORE, UiStyle.WINDOWS_311 -> RoundedCornerShape(0.dp)
    UiStyle.MANUSCRIPT -> RoundedCornerShape(4.dp)
    UiStyle.MODERN -> RoundedCornerShape(18.dp)
}

/** Geometry is independent of colour: changing a palette does not erase the selected desktop. */
@Composable
fun Modifier.appFrame(sunken: Boolean = false): Modifier {
    val appearance = LocalAppearance.current
    val colors = appearance.colors
    val style = appearance.style
    return drawWithContent {
        drawContent()
        val pixel = 1.dp.toPx()
        fun rectangle(color: Color, inset: Float, width: Float) {
            if (size.width > inset * 2 && size.height > inset * 2) {
                drawRect(color, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(width))
            }
        }
        when (style) {
            UiStyle.WORKBENCH -> {
                rectangle(colors.outline, pixel, pixel * 2)
                if (sunken) rectangle(colors.accent, pixel * 4, pixel)
            }
            UiStyle.COMMODORE -> {
                rectangle(colors.outline, pixel, pixel * 2)
                rectangle(colors.outline, pixel * 5, pixel)
            }
            UiStyle.WINDOWS_311 -> {
                rectangle(colors.bevelDark, pixel / 2, pixel)
                val light = if (sunken) colors.bevelDark else colors.bevelLight
                val dark = if (sunken) colors.bevelLight else colors.bevelDark
                val inset = pixel * 2
                drawLine(light, Offset(inset, size.height - inset), Offset(inset, inset), pixel * 2)
                drawLine(light, Offset(inset, inset), Offset(size.width - inset, inset), pixel * 2)
                drawLine(dark, Offset(size.width - inset, inset), Offset(size.width - inset, size.height - inset), pixel * 2)
                drawLine(dark, Offset(size.width - inset, size.height - inset), Offset(inset, size.height - inset), pixel * 2)
            }
            UiStyle.MANUSCRIPT, UiStyle.MODERN -> {
                val radius = (if (style == UiStyle.MANUSCRIPT) 4.dp else 18.dp).toPx()
                if (size.width > pixel && size.height > pixel) {
                    drawRoundRect(
                        color = colors.outline.copy(alpha = if (sunken) 1f else 0.7f),
                        topLeft = Offset(pixel / 2, pixel / 2),
                        size = Size(size.width - pixel, size.height - pixel),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(pixel),
                    )
                }
            }
        }
    }
}

@Composable
fun AppWindow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppearance.current.colors
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        Column(modifier.clip(appSurfaceShape()).background(colors.surface).appFrame()) {
            AppHeader(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
            content()
        }
    }
}

@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val appearance = LocalAppearance.current
    val colors = appearance.colors
    val retro = appearance.style in listOf(UiStyle.WORKBENCH, UiStyle.COMMODORE, UiStyle.WINDOWS_311)
    CompositionLocalProvider(LocalContentColor provides colors.onTitleBar) {
        Column(modifier.fillMaxWidth().background(colors.titleBar)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (retro) 8.dp else 16.dp,
                    vertical = if (retro) 8.dp else 14.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack != null) {
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.appearance_back))
                    }
                } else if (appearance.style == UiStyle.WORKBENCH || appearance.style == UiStyle.WINDOWS_311) {
                    Box(
                        Modifier.size(26.dp).background(colors.surfaceRaised).appFrame(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("P", color = colors.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    if (appearance.style == UiStyle.COMMODORE) {
                        Text(
                            stringResource(R.string.appearance_commodore_header),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = colors.onTitleBar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        color = colors.onTitleBar,
                        style = if (appearance.style == UiStyle.MANUSCRIPT) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            color = colors.onTitleBar,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
            val lineColor = when (appearance.style) {
                UiStyle.WORKBENCH -> colors.bevelLight
                UiStyle.COMMODORE -> colors.outline
                UiStyle.WINDOWS_311 -> colors.bevelDark
                else -> colors.outline.copy(alpha = 0.6f)
            }
            Box(Modifier.fillMaxWidth().height(if (retro) 2.dp else 1.dp).background(lineColor))
        }
    }
}

@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppearance.current.colors
    val clickableModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        Column(
            modifier.clip(appSurfaceShape()).background(colors.surfaceRaised)
                .then(clickableModifier).appFrame().padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = AppActionButton(onClick, modifier, enabled, ButtonKind.PRIMARY, content)

@Composable
fun AppTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = AppActionButton(onClick, modifier, enabled, ButtonKind.TONAL, content)

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = AppActionButton(onClick, modifier, enabled, ButtonKind.OUTLINED, content)

private enum class ButtonKind { PRIMARY, TONAL, OUTLINED }

@Composable
private fun AppActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    kind: ButtonKind,
    content: @Composable RowScope.() -> Unit,
) {
    val appearance = LocalAppearance.current
    val colors = appearance.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val windows = appearance.style == UiStyle.WINDOWS_311
    val background = when {
        windows && kind != ButtonKind.OUTLINED -> colors.surfaceRaised
        kind == ButtonKind.PRIMARY -> colors.accent
        kind == ButtonKind.TONAL -> colors.accentSoft
        else -> colors.surface
    }
    val foreground = when {
        windows && kind != ButtonKind.OUTLINED -> colors.text
        kind == ButtonKind.PRIMARY -> colors.onAccent
        kind == ButtonKind.TONAL -> colors.onAccentSoft
        else -> colors.text
    }
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp).appFrame(sunken = pressed),
        enabled = enabled,
        shape = appSurfaceShape(),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = colors.surfaceRaised,
            disabledContentColor = colors.muted.copy(alpha = 0.65f),
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        interactionSource = interactions,
        content = content,
    )
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppearance.current.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    CompositionLocalProvider(LocalContentColor provides if (enabled) colors.text else colors.muted.copy(alpha = 0.65f)) {
        Box(
            modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(appSurfaceShape()).background(colors.surfaceRaised)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ).appFrame(sunken = pressed),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun AppAppearanceButton(modifier: Modifier = Modifier) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    AppIconButton(onClick = { showDialog = true }, modifier = modifier) {
        Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.appearance_title))
    }
    if (showDialog) AppearanceDialog(onDismissRequest = { showDialog = false })
}
