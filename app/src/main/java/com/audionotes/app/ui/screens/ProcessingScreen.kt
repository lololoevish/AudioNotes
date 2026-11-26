package com.audionotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.audionotes.app.R
import com.audionotes.app.ui.ProcessingState
import com.audionotes.app.ui.ProcessingStep
import com.audionotes.app.ui.components.AudioPlaybackControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    processing: ProcessingState?,
    completedRecordingId: String?,
    onGenerate: (String) -> Unit,
    onEditTranscript: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCompleted: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNotice: (String) -> Unit
) {
    LaunchedEffect(completedRecordingId) {
        completedRecordingId?.let {
            onCompleted(it)
        }
    }

    var playbackError by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Заголовок отражает текущий шаг: во время распознавания
                    // это ещё не «генерация конспекта».
                    val titleRes = when (processing?.step) {
                        ProcessingStep.TRANSCRIBING -> R.string.processing_title_transcribing
                        else -> R.string.processing_title_generating
                    }
                    Text(stringResource(titleRes))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (processing == null) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.processing_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            StepRow(
                title = stringResource(R.string.processing_transcribing),
                state = when (processing.step) {
                    ProcessingStep.TRANSCRIBING -> StepState.Active
                    ProcessingStep.AWAITING_DECISION, ProcessingStep.GENERATING, ProcessingStep.DONE -> StepState.Done
                }
            )

            StepRow(
                title = stringResource(R.string.processing_generating),
                state = when (processing.step) {
                    ProcessingStep.GENERATING -> StepState.Active
                    ProcessingStep.DONE -> StepState.Done
                    else -> StepState.Pending
                }
            )

            if (processing.step == ProcessingStep.GENERATING && processing.partTotal > 0) {
                Column {
                    Text(
                        text = "Часть ${processing.partIndex} из ${processing.partTotal}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { processing.partIndex.toFloat() / processing.partTotal.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Прослушивание: сначала сама запись, потом — то, что из неё распознали.
            if (processing.audioPath != null || processing.transcript.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.processing_listen_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.processing_listen_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val audioPath = processing.audioPath
                        if (audioPath != null) {
                            Spacer(Modifier.height(12.dp))
                            AudioPlaybackControl(
                                audioPath = audioPath,
                                label = stringResource(R.string.processing_listen_recording),
                                modifier = Modifier.fillMaxWidth(),
                                onError = { message ->
                                    playbackError = message
                                    onNotice(message)
                                }
                            )
                        }

                        if (processing.transcript.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { onEditTranscript(processing.recordingId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.processing_read_transcript))
                            }
                        }

                        playbackError?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Что именно распознано — чтобы сверить с записью.
            if (processing.transcript.isNotBlank()) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.processing_transcript_preview),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = processing.transcript,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (processing.step == ProcessingStep.AWAITING_DECISION) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.processing_decision_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onGenerate(processing.recordingId) }) {
                                Text(stringResource(R.string.processing_continue))
                            }
                            OutlinedButton(onClick = { onEditTranscript(processing.recordingId) }) {
                                Text(stringResource(R.string.processing_edit_transcript))
                            }
                        }
                    }
                }
            }

            processing.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onRetry(processing.recordingId) }) {
                                Text(stringResource(R.string.processing_retry))
                            }
                            OutlinedButton(onClick = onOpenSettings) {
                                Text(stringResource(R.string.settings_title))
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.processing_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

private enum class StepState { Pending, Active, Done }

@Composable
private fun StepRow(title: String, state: StepState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            StepState.Active -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )

            StepState.Done -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            StepState.Pending -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == StepState.Pending) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
