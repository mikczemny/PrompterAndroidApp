package com.mikczemny.prompter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikczemny.prompter.R

/**
 * A single attributed dependency. The name and provider are proper nouns and
 * stay untranslated; only the license label is a string resource, since every
 * component here happens to share the same one.
 */
private data class Component(val name: String, val provider: String)

/**
 * Everything the app ships or downloads that carries an attribution obligation.
 * All Apache 2.0 — see docs/MODEL-LICENSES.md for the per-model verification.
 * The Vosk engine reaches us via the vosk-android dependency; the language
 * models are fetched at runtime from Alpha Cephei's own endpoints.
 */
private val COMPONENTS = listOf(
    Component("Vosk Speech Recognition Toolkit", "Alpha Cephei Inc."),
    Component("Vosk language models (11 languages)", "Alpha Cephei Inc."),
    Component("Jetpack Compose & AndroidX", "The Android Open Source Project"),
    Component("Kotlin Standard Library", "JetBrains s.r.o."),
)

/**
 * Attribution screen required by the Apache 2.0 terms the engine, models and
 * AndroidX all ship under: name the components and carry a copy of the license
 * text. The text is bundled as a raw resource rather than linked, because the
 * app is built to work with no network once a language pack is in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember {
        context.resources.openRawResource(R.raw.apache_2_0)
            .bufferedReader()
            .use { it.readText() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_menu),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.licenses_components),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    COMPONENTS.forEachIndexed { index, component ->
                        if (index > 0) HorizontalDivider()
                        Column(modifier = Modifier.padding(vertical = 14.dp)) {
                            Text(
                                component.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                component.provider,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.license_apache),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.licenses_full_text_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // The full license text, verbatim. Monospace keeps the boilerplate's
            // original layout readable; it is selectable so a reviewer can copy it.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = licenseText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
