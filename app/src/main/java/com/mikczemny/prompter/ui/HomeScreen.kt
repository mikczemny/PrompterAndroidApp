package com.mikczemny.prompter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikczemny.prompter.BuildConfig
import com.mikczemny.prompter.document.DocumentImporter
import com.mikczemny.prompter.document.oneSentencePerLine
import com.mikczemny.prompter.match.splitWords
import com.mikczemny.prompter.speech.Language
import com.mikczemny.prompter.speech.Languages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Past this many characters the script is long enough that layout cost starts
 * to show, so the user is told rather than left to discover it on stage. It is
 * roughly 25 minutes of speaking — beyond that a script usually wants splitting.
 */
private const val SOFT_CHAR_LIMIT = 10_000

/**
 * Hard ceiling on script length. The whole script is laid out as a single text
 * node, so this is a real constraint rather than a policy one.
 */
private const val HARD_CHAR_LIMIT = 20_000

/** Unhurried presenting pace, used only for the reading-time estimate. */
private const val SPEAKING_WORDS_PER_MINUTE = 140.0

/**
 * Quick insertions offered under the script field. Punctuation and breaks are
 * fiddly to reach on a phone keyboard, and they are exactly what a pasted or
 * imported script is missing — line breaks especially, since where a line ends
 * is where the speaker will pause.
 */
private val QUICK_INSERTS = listOf(
    "." to ".",
    "," to ",",
    "?" to "?",
    "!" to "!",
    "—" to " — ",
    "New line" to "\n",
    "New paragraph" to "\n\n",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    initialLanguage: Language = Languages.DEFAULT,
    onStart: (script: String, language: Language) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var language by remember { mutableStateOf(initialLanguage) }
    // A TextFieldValue rather than a String, because the editing controls insert
    // at the cursor and need to know where it is.
    var script by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialLanguage.sample))
    }
    // Tracks whether the user has hand-edited the script; if not, switching
    // language swaps in that language's sample so the picker is easy to try.
    var edited by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val text = script.text
    val wordCount = remember(text) { splitWords(text).size }
    val overSoftLimit = text.length > SOFT_CHAR_LIMIT

    /** Replaces the whole script, parking the cursor at the start. */
    fun replaceScript(newText: String) {
        script = TextFieldValue(newText.take(HARD_CHAR_LIMIT), TextRange(0))
        edited = true
    }

    fun insertAtCursor(snippet: String) {
        val start = script.selection.min
        val end = script.selection.max
        val updated = script.text.replaceRange(start, end, snippet)
        if (updated.length > HARD_CHAR_LIMIT) return
        script = script.copy(text = updated, selection = TextRange(start + snippet.length))
        edited = true
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            // Extraction reads the whole file and, for a long PDF, does real
            // work — never on the main thread.
            val outcome = withContext(Dispatchers.IO) { DocumentImporter.import(context, uri) }
            importing = false
            when (outcome) {
                is DocumentImporter.Outcome.Success -> {
                    replaceScript(outcome.text)
                    if (outcome.text.length > HARD_CHAR_LIMIT) {
                        snackbarHostState.showSnackbar(
                            "${outcome.fileName} was longer than $HARD_CHAR_LIMIT " +
                                "characters and has been cut to fit."
                        )
                    }
                }

                is DocumentImporter.Outcome.Failure ->
                    snackbarHostState.showSnackbar(outcome.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            StartBar(
                enabled = text.isNotBlank() && !importing,
                onStart = { onStart(text, language) },
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Prompter",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Paste, import or type your script, then press Start. The " +
                        "prompter listens and scrolls at your own pace — fully offline " +
                        "once the language pack is downloaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = "${language.displayName}  ·  ${language.englishName}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        supportingText = {
                            Text("Voice pack ~${language.approxMb} MB, downloaded once")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        Languages.ALL.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lang.displayName, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${lang.englishName}  ·  ~${lang.approxMb} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    language = lang
                                    if (!edited) script = TextFieldValue(lang.sample)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { importLauncher.launch(DocumentImporter.SUPPORTED_MIME_TYPES) },
                        enabled = !importing,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (importing) "Reading…" else "Import file")
                    }
                    OutlinedButton(
                        onClick = { replaceScript(oneSentencePerLine(text)) },
                        enabled = text.isNotBlank() && !importing,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.WrapText, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("One line per sentence")
                    }
                }

                OutlinedTextField(
                    value = script,
                    onValueChange = { new ->
                        // Truncate rather than reject the whole edit, so a long
                        // paste still lands and the user can see what fitted.
                        script = if (new.text.length > HARD_CHAR_LIMIT) {
                            new.copy(text = new.text.take(HARD_CHAR_LIMIT))
                        } else {
                            new
                        }
                        edited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Script") },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 8,
                    isError = overSoftLimit,
                )

                QuickInsertBar(onInsert = ::insertAtCursor)

                ScriptStats(
                    wordCount = wordCount,
                    charCount = text.length,
                    overSoftLimit = overSoftLimit,
                )

                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))
            }

            if (importing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * Inserts punctuation and breaks at the cursor. Line breaks matter most: the
 * stage renders the script exactly as written, so this is how the speaker
 * decides where lines fall and where the eye rests.
 */
@Composable
private fun QuickInsertBar(onInsert: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QUICK_INSERTS.forEach { (label, snippet) ->
            OutlinedButton(
                onClick = { onInsert(snippet) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 6.dp,
                ),
            ) {
                Text(label, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ScriptStats(wordCount: Int, charCount: Int, overSoftLimit: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat(Icons.AutoMirrored.Outlined.Notes, "$wordCount words", "$charCount characters")
                Stat(Icons.Filled.Schedule, formatDuration(wordCount), "at 140 wpm")
            }

            if (overSoftLimit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Long script — scrolling gets heavier, and input stops at " +
                            "$HARD_CHAR_LIMIT characters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, caption: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartBar(enabled: Boolean, onStart: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Button(
            onClick = onStart,
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(60.dp),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Start", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Reading time as a short human string; under a minute is not worth digits. */
private fun formatDuration(wordCount: Int): String {
    val totalSeconds = (wordCount / SPEAKING_WORDS_PER_MINUTE * 60).roundToInt()
    if (totalSeconds < 60) return "under 1 min"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0) "$minutes min" else "$minutes min $seconds s"
}
