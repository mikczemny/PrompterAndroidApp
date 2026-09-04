package com.mikczemny.prompter.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mikczemny.prompter.R
import com.mikczemny.prompter.data.Recording
import com.mikczemny.prompter.data.RecordingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Browses saved recordings: play, delete, and choose the folder they are saved
 * to. Recordings live either in the app's own folder or in a folder the user
 * picks through the system file picker — [RecordingStore] hides which, so this
 * screen just lists, plays and deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RecordingStore(context) }
    val appFolderInAppOnlyMessage = stringResource(R.string.app_folder_in_app_only)
    val folderOpenUnavailableMessage = stringResource(R.string.folder_open_unavailable)

    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var folderLabel by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // One player for the screen; whichever row is playing owns it.
    val player = remember { MediaPlayer() }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    fun stopPlayback() {
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        playingUri = null
    }

    fun openVideo(recording: Recording) {
        val viewUri = if (recording.uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(recording.uri.path!!),
            )
        } else {
            recording.uri
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(viewUri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
    }

    fun togglePlay(recording: Recording) {
        if (recording.name.endsWith(".mp4", ignoreCase = true)) {
            openVideo(recording)
            return
        }
        if (playingUri == recording.uri) {
            stopPlayback()
            return
        }
        runCatching {
            player.reset()
            player.setDataSource(context, recording.uri)
            player.setOnCompletionListener {
                it.reset()
                playingUri = null
            }
            player.prepare()
            player.start()
            playingUri = recording.uri
        }.onFailure { playingUri = null }
    }

    fun openSaveFolder() {
        val uri = store.folderUri()
        if (uri == null) {
            Toast.makeText(
                context,
                appFolderInAppOnlyMessage,
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    context,
                    folderOpenUnavailableMessage,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist read+write so the folder survives across app restarts.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            store.setFolder(uri)
            stopPlayback()
            refresh++
        }
    }

    LaunchedEffectRefresh(refresh) {
        recordings = withContext(Dispatchers.IO) { store.list() }
        folderLabel = withContext(Dispatchers.IO) { store.folderLabel() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recordings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_menu),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        stopPlayback()
                        store.useAppFolder()
                        refresh++
                    }) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(R.string.use_app_folder),
                        )
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = stringResource(R.string.choose_folder),
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
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.saving_to,
                    folderLabel ?: stringResource(R.string.folder_app_default),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedButton(
                onClick = { openSaveFolder() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Text(
                    stringResource(R.string.open_save_folder),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (recordings.isEmpty()) {
                Text(
                    stringResource(R.string.recordings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recordings, key = { it.uri.toString() }) { recording ->
                    RecordingRow(
                        recording = recording,
                        playing = playingUri == recording.uri,
                        onTogglePlay = { togglePlay(recording) },
                        onDelete = {
                            if (playingUri == recording.uri) stopPlayback()
                            scope.launch {
                                withContext(Dispatchers.IO) { store.delete(recording) }
                                refresh++
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTogglePlay)
            .padding(vertical = 8.dp),
    ) {
        val isVideo = recording.name.endsWith(".mp4", ignoreCase = true)
        IconButton(onClick = onTogglePlay) {
            Icon(
                when {
                    isVideo -> Icons.Filled.Movie
                    playing -> Icons.Filled.Stop
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = stringResource(
                    if (playing) R.string.stop_playback else R.string.play
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                recording.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                "${formatSize(recording.sizeBytes)} · ${formatDate(recording.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.delete_recording, recording.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small helper so the refresh trigger reads clearly at the call site. */
@Composable
private fun LaunchedEffectRefresh(key: Int, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { block() }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
