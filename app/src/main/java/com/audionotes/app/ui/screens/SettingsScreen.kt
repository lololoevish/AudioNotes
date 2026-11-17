package com.audionotes.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.audionotes.app.BuildConfig
import com.audionotes.app.R
import com.audionotes.app.data.remote.LlmProvider
import com.audionotes.app.ui.LlmSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    llm: LlmSettings,
    onDarkThemeChange: (Boolean) -> Unit,
    onProviderChange: (LlmProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onTestConnection: (LlmProvider, String, String, (Result<String>) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var keyInput by remember(llm.apiKey) { mutableStateOf(llm.apiKey) }
    var modelInput by remember(llm.model) { mutableStateOf(llm.model) }
    var keyVisible by remember { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_llm_provider),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = providerMenuOpen,
                onExpandedChange = { providerMenuOpen = it }
            ) {
                OutlinedTextField(
                    value = llm.provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_llm_provider)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuOpen)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    supportingText = { Text(llm.provider.freeTierNote) }
                )
                ExposedDropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false }
                ) {
                    LlmProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(provider.displayName)
                                    Text(
                                        text = provider.freeTierNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProviderChange(provider)
                                providerMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (!llm.provider.worksFromRussia) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_provider_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    onApiKeyChange(it)
                },
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = null
                        )
                    }
                },
                supportingText = { Text(stringResource(R.string.settings_key_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = modelInput,
                onValueChange = {
                    modelInput = it
                    onModelChange(it)
                },
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                supportingText = { Text(llm.provider.defaultModel) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        onTestConnection(llm.provider, keyInput, modelInput) { result ->
                            testing = false
                            testResult = result.fold(
                                onSuccess = { "OK: $it" },
                                onFailure = { "Ошибка: ${it.message ?: "неизвестная ошибка"}" }
                            )
                        }
                    },
                    enabled = !testing
                ) {
                    Text(stringResource(R.string.settings_test))
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(llm.provider.consoleUrl))
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Text(stringResource(R.string.settings_open_console))
                }
            }

            Spacer(Modifier.height(8.dp))

            testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.startsWith("OK")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SettingRow(
                title = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME
            )

            Spacer(Modifier.height(12.dp))

            SettingRow(
                title = "LLM",
                value = "${llm.provider.displayName} · ${llm.effectiveModel}"
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Аудио записывается только на устройстве и не передаётся в сеть. " +
                    "В LLM отправляется лишь текст расшифровки для формирования конспекта.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
