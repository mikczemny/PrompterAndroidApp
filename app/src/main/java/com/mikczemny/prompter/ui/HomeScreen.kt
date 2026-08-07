package com.mikczemny.prompter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikczemny.prompter.BuildConfig
import com.mikczemny.prompter.R
import com.mikczemny.prompter.data.SavedScript
import com.mikczemny.prompter.data.ScriptStore
import com.mikczemny.prompter.document.DocumentImporter
import com.mikczemny.prompter.document.oneSentencePerLine
import com.mikczemny.prompter.match.splitWords
import com.mikczemny.prompter.speech.Language
import com.mikczemny.prompter.speech.Languages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
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

/** A labelled snippet the quick-insert bar drops in at the cursor. */
private data class QuickInsert(@StringRes val labelRes: Int, val snippet: String)

/**
 * Quick insertions offered under the script field. Punctuation and breaks are
 * fiddly to reach on a phone keyboard, and they are exactly what a pasted or
 * imported script is missing — line breaks especially, since where a line ends
 * is where the speaker will pause.
 */
private val QUICK_INSERTS = listOf(
    QuickInsert(R.string.qi_period, "."),
    QuickInsert(R.string.qi_comma, ","),
    QuickInsert(R.string.qi_question, "?"),
    QuickInsert(R.string.qi_exclamation, "!"),
    QuickInsert(R.string.qi_dash, " — "),
    QuickInsert(R.string.qi_new_line, "\n"),
    QuickInsert(R.string.qi_new_paragraph, "\n\n"),
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

    val store = remember { ScriptStore(File(context.filesDir, "scripts")) }
    var showLibrary by remember { mutableStateOf(false) }
    var savedScripts by remember { mutableStateOf(emptyList<SavedScript>()) }
    // Which stored script the field currently holds, so saving again updates it
    // instead of leaving a trail of near-identical copies.
    var currentScriptId by rememberSaveable { mutableStateOf<String?>(null) }

    val text = script.text
    val wordCount = remember(text) { splitWords(text).size }
    val overSoftLimit = text.length > SOFT_CHAR_LIMIT

    /** Replaces the whole script, parking the cursor at the start. */
    fun replaceScript(newText: String) {
        script = TextFieldValue(newText.take(HARD_CHAR_LIMIT), TextRange(0))
        edited = true
    }

    fun saveScript() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                store.save(text = script.text, id = currentScriptId)
            }
            currentScriptId = saved.id
            snackbarHostState.showSnackbar(context.getString(R.string.saved_as, saved.title))
        }
    }

    // Reading the list is cheap, but it can go stale while the sheet is closed,
    // so it is refreshed each time the library opens rather than cached.
    LaunchedEffect(showLibrary) {
        if (showLibrary) {
            savedScripts = withContext(Dispatchers.IO) { store.list() }
        }
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
                            context.getString(
                                R.string.import_truncated,
                                outcome.fileName,
                                HARD_CHAR_LIMIT,
                            )
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(
                            R.string.language_selected,
                            language.displayName,
                            language.englishName,
                        ),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_language)) },
                        supportingText = {
                            Text(stringResource(R.string.voice_pack_size, language.approxMb))
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
                                            stringResource(
                                                R.string.language_option_detail,
                                                lang.englishName,
                                                lang.approxMb,
                                            ),
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

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { importLauncher.launch(DocumentImporter.SUPPORTED_MIME_TYPES) },
                        enabled = !importing,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (importing) R.string.reading else R.string.import_button
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = { saveScript() },
                        enabled = text.isNotBlank() && !importing,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save))
                    }
                    OutlinedButton(
                        onClick = { showLibrary = true },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.library))
                    }
                    OutlinedButton(
                        onClick = { replaceScript(oneSentencePerLine(text)) },
                        enabled = text.isNotBlank() && !importing,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.WrapText,
                            null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.one_line_per_sentence))
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
                    label = { Text(stringResource(R.string.label_script)) },
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
                    text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
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

    if (showLibrary) {
        ModalBottomSheet(onDismissRequest = { showLibrary = false }) {
            ScriptLibrary(
                scripts = savedScripts,
                onOpen = { saved ->
                    replaceScript(saved.text)
                    currentScriptId = saved.id
                    showLibrary = false
                },
                onDelete = { saved ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(saved.id) }
                        if (currentScriptId == saved.id) currentScriptId = null
                        savedScripts = withContext(Dispatchers.IO) { store.list() }
                    }
                },
            )
        }
    }
}

@Composable
private fun ScriptLibrary(
    scripts: List<SavedScript>,
    onOpen: (SavedScript) -> Unit,
    onDelete: (SavedScript) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            stringResource(R.string.saved_scripts),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (scripts.isEmpty()) {
            Text(
                stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }

        scripts.forEachIndexed { index, saved ->
            if (index > 0) HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen(saved) }
                        .padding(vertical = 14.dp),
                ) {
                    Text(saved.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    val savedWordCount = splitWords(saved.text).size
                    Text(
                        stringResource(
                            R.string.script_meta,
                            pluralStringResource(
                                R.plurals.words_count,
                                savedWordCount,
                                savedWordCount,
                            ),
                            formatTimestamp(saved.updatedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(saved) }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_script, saved.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

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
        QUICK_INSERTS.forEach { item ->
            OutlinedButton(
                onClick = { onInsert(item.snippet) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 6.dp,
                ),
            ) {
                Text(stringResource(item.labelRes), fontSize = 15.sp)
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
                Stat(
                    Icons.AutoMirrored.Outlined.Notes,
                    pluralStringResource(R.plurals.words_count, wordCount, wordCount),
                    stringResource(R.string.characters_caption, charCount),
                )
                Stat(
                    Icons.Filled.Schedule,
                    formatDuration(wordCount),
                    stringResource(R.string.reading_pace, SPEAKING_WORDS_PER_MINUTE.toInt()),
                )
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
                        stringResource(R.string.long_script_warning, HARD_CHAR_LIMIT),
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
            Text(stringResource(R.string.start), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Reading time as a short human string; under a minute is not worth digits. */
@Composable
private fun formatDuration(wordCount: Int): String {
    val totalSeconds = (wordCount / SPEAKING_WORDS_PER_MINUTE * 60).roundToInt()
    if (totalSeconds < 60) return stringResource(R.string.duration_under_min)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (seconds == 0) {
        stringResource(R.string.duration_min, minutes)
    } else {
        stringResource(R.string.duration_min_sec, minutes, seconds)
    }
}
