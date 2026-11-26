package com.audionotes.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.audionotes.app.R
import com.audionotes.app.data.local.RecordingEntity
import com.audionotes.app.export.MarkdownExporter
import com.audionotes.app.ui.components.MarkdownContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    recording: RecordingEntity?,
    onBack: () -> Unit,
    onOpenTranscript: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRegenerate: (String) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var titleInput by remember(recording?.id) { mutableStateOf(recording?.title.orEmpty()) }

    val performSave: () -> Unit = {
        val md = recording?.summaryMd.orEmpty()
        if (md.isNotBlank()) {
            val fileName = recording?.title ?: "конспект"
            scope.launch {
                val result = MarkdownExporter.saveToDownloads(context, fileName, md)
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { context.getString(R.string.result_saved, it.lastPathSegment ?: fileName) },
                        onFailure = { context.getString(R.string.result_save_failed) }
                    )
                )
            }
        }
    }

    // На Android 8–9 запись в «Загрузки» требует runtime-разрешения.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            performSave()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_storage_permission))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = recording?.title ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                        recording?.let {
                            Text(
                                text = "${formatDuration(it.durationSec)} · ${it.type.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { recording?.let { onRegenerate(it.id) } }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.result_regenerate)
                        )
                    }
                    IconButton(onClick = { renameOpen = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.result_rename))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED

                            if (needsPermission) {
                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                performSave()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.result_save))
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val path = recording?.audioFilePath
                            if (path.isNullOrBlank()) return@FilledTonalIconButton
                            val current = player
                            if (current == null) {
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(path)
                                        // Подготовка асинхронная: на 5-часовом файле
                                        // синхронный prepare() вешал бы интерфейс.
                                        setOnPreparedListener { prepared ->
                                            prepared.start()
                                            isPlaying = true
                                        }
                                        setOnCompletionListener { isPlaying = false }
                                        prepareAsync()
                                    }
                                }.onSuccess {
                                    player = it
                                }
                            } else if (isPlaying) {
                                current.pause()
                                isPlaying = false
                            } else {
                                current.start()
                                isPlaying = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.result_play)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { recording?.let { onOpenTranscript(it.id) } }
                    ) {
                        Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.result_transcript))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (recording == null) {
                Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier.padding(24.dp)
                )
                return@Column
            }

            MarkdownContent(
                markdown = recording.summaryMd.ifBlank {
                    "Конспект ещё не готов. Нажмите кнопку обновления вверху, чтобы повторить запрос."
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            )
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.result_rename)) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(stringResource(R.string.result_rename_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTitle = titleInput.trim()
                        if (newTitle.isNotEmpty()) {
                            recording?.let { onRename(it.id, newTitle) }
                        }
                        renameOpen = false
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
