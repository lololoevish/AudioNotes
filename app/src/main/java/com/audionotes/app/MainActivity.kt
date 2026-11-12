package com.audionotes.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audionotes.app.core.ServiceLocator
import com.audionotes.app.ui.AppNavHost
import com.audionotes.app.ui.AppViewModel
import com.audionotes.app.ui.theme.AudioNotesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = ServiceLocator.settings(this)

        setContent {
            val darkTheme by settings.darkTheme.collectAsStateWithLifecycle(initialValue = false)
            val viewModel: AppViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // Не даём экрану погаснуть во время длинной записи.
            if (state.isRecording) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            AudioNotesTheme(darkTheme = darkTheme) {
                AppNavHost(
                    viewModel = viewModel,
                    darkTheme = darkTheme,
                    onDarkThemeChange = { enabled ->
                        scope.launch { settings.setDarkTheme(enabled) }
                    }
                )
            }
        }
    }
}
