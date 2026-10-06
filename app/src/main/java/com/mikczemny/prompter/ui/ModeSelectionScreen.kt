package com.mikczemny.prompter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikczemny.prompter.R
import com.mikczemny.prompter.ui.theme.AppAppearanceButton
import com.mikczemny.prompter.ui.theme.AppPanel
import com.mikczemny.prompter.ui.theme.AppWindow
import com.mikczemny.prompter.ui.theme.LocalAppearance

@Composable
fun ModeSelectionScreen(onSelect: (PrompterMode) -> Unit) {
    Scaffold(containerColor = LocalAppearance.current.colors.desktop) { padding ->
        AppWindow(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.screen_recording_setup),
            actions = { AppAppearanceButton() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.choose_mode_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.choose_mode_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Both launchers remain reachable in landscape; a desktop
                    // layout uses the extra width without shrinking tap targets.
                    if (maxWidth >= 640.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModeCard(
                                icon = Icons.Filled.PhotoCameraFront,
                                title = stringResource(R.string.selfie_prompter),
                                description = stringResource(R.string.selfie_prompter_description),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(PrompterMode.SELFIE) },
                            )
                            ModeCard(
                                icon = Icons.Filled.VideocamOff,
                                title = stringResource(R.string.ext_prompter),
                                description = stringResource(R.string.ext_prompter_description),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(PrompterMode.EXTERNAL) },
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModeCard(
                                icon = Icons.Filled.PhotoCameraFront,
                                title = stringResource(R.string.selfie_prompter),
                                description = stringResource(R.string.selfie_prompter_description),
                                onClick = { onSelect(PrompterMode.SELFIE) },
                            )
                            ModeCard(
                                icon = Icons.Filled.VideocamOff,
                                title = stringResource(R.string.ext_prompter),
                                description = stringResource(R.string.ext_prompter_description),
                                onClick = { onSelect(PrompterMode.EXTERNAL) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppPanel(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
