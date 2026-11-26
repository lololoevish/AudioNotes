package com.audionotes.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audionotes.app.R
import com.audionotes.app.ui.screens.HistoryScreen
import com.audionotes.app.ui.screens.HomeScreen
import com.audionotes.app.ui.screens.ProcessingScreen
import com.audionotes.app.ui.screens.ResultScreen
import com.audionotes.app.ui.screens.SettingsScreen
import com.audionotes.app.ui.screens.TranscriptScreen

object Routes {
    const val HOME = "home"
    const val PROCESSING = "processing"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RESULT = "result/{id}"
    const val TRANSCRIPT = "transcript/{id}"

    fun result(id: String) = "result/$id"
    fun transcript(id: String) = "transcript/$id"
}

@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val appContext = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val llmSettings by viewModel.llmSettings.collectAsStateWithLifecycle()
    val recordings by viewModel.recordingsFlow().collectAsStateWithLifecycle(emptyList())

    // Переход на экран обработки, как только запущен пайплайн.
    LaunchedEffect(state.processing?.recordingId) {
        val id = state.processing?.recordingId
        if (id != null && navController.currentDestination?.route != Routes.PROCESSING) {
            navController.navigate(Routes.PROCESSING)
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onDiscardPending = viewModel::discardPendingRecording,
                onLectureTypeSelected = viewModel::onLectureTypeSelected,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onNoticeShown = viewModel::consumeNotice,
                onMicDenied = {
                    viewModel.showNotice(appContext.getString(R.string.error_mic_denied))
                }
            )
        }

        composable(Routes.PROCESSING) {
            ProcessingScreen(
                processing = state.processing,
                completedRecordingId = state.completedRecordingId,
                onGenerate = viewModel::generate,
                onEditTranscript = { id -> navController.navigate(Routes.transcript(id)) },
                onRetry = viewModel::retry,
                onCompleted = { id ->
                    viewModel.consumeCompleted()
                    navController.navigate(Routes.result(id)) {
                        popUpTo(Routes.PROCESSING) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onNotice = { }
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val recording by viewModel.recordingFlow(id).collectAsStateWithLifecycle(null)
            ResultScreen(
                recording = recording,
                onBack = { navController.popBackStack() },
                onOpenTranscript = { transcriptId -> navController.navigate(Routes.transcript(transcriptId)) },
                onRename = viewModel::renameRecording,
                onRegenerate = viewModel::generate
            )
        }

        composable(
            route = Routes.TRANSCRIPT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val recording by viewModel.recordingFlow(id).collectAsStateWithLifecycle(null)
            TranscriptScreen(
                recording = recording,
                onBack = { navController.popBackStack() },
                onGenerate = { text ->
                    viewModel.saveTranscriptAndGenerate(id, text)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                recordings = recordings,
                onOpen = { id -> navController.navigate(Routes.result(id)) },
                onDelete = { id ->
                    viewModel.deleteRecording(id)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                darkTheme = darkTheme,
                llm = llmSettings,
                onDarkThemeChange = onDarkThemeChange,
                onProviderChange = viewModel::saveProvider,
                onApiKeyChange = viewModel::saveApiKey,
                onModelChange = viewModel::saveModel,
                onEndpointChange = viewModel::saveEndpoint,
                onTestConnection = { provider, apiKey, model, endpoint, onResult ->
                    viewModel.testConnection(provider, apiKey, model, endpoint, onResult)
                },
                onLoadModels = { endpoint, apiKey, onResult ->
                    viewModel.loadModels(endpoint, apiKey, onResult)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

internal fun NavHostController.currentRouteOrNull(): String? = currentDestination?.route
