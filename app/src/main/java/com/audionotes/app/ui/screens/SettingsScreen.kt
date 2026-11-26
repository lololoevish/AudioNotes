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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
    onEndpointChange: (String) -> Unit,
    onTestConnection: (LlmProvider, String, String, String, (Result<String>) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Черновик ввода: не пересоздаётся при обновлении сохранённых значений,
    // иначе курсор в поле прыгает и ключ невозможно напечатать.
    var keyInput by rememberSaveable { mutableStateOf(llm.apiKey) }
    var modelInput by rememberSaveable { mutableStateOf(llm.model) }
    var endpointInput by rememberSaveable { mutableStateOf(llm.endpoint) }
    var savedKey by rememberSaveable { mutableStateOf(llm.apiKey) }
    var savedModel by rememberSaveable { mutableStateOf(llm.model) }
    var savedEndpoint by rememberSaveable { mutableStateOf(llm.endpoint) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    val hasUnsavedChanges = keyInput != savedKey ||
        modelInput != savedModel ||
        endpointInput != savedEndpoint

    fun saveAll() {
        onApiKeyChange(keyInput.trim())
        onModelChange(modelInput.trim())
        onEndpointChange(endpointInput.trim())
        savedKey = keyInput.trim()
        savedModel = modelInput.trim()
        savedEndpoint = endpointInput.trim()
    }

    // При смене провайдера Account ID от старого провайдера не подходит новому,
    // поэтому подключение чистим, а модель приводим к дефолту нового провайдера.
    fun applyProvider(provider: LlmProvider) {
        onProviderChange(provider)
        endpointInput = ""
        savedEndpoint = ""
        onEndpointChange("")
        if (provider.freeModels.isNotEmpty() && provider.freeModels.none { it == modelInput.trim() }) {
            modelInput = provider.defaultModel
            savedModel = provider.defaultModel
            onModelChange(provider.defaultModel)
        }
        providerMenuOpen = false
    }

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
                                applyProvider(provider)
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
                onValueChange = { keyInput = it },
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(R.string.settings_show_key)
                            )
                        }
                        IconButton(
                            onClick = {
                                keyInput = ""
                                onApiKeyChange("")
                                savedKey = ""
                            }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.settings_clear_key)
                            )
                        }
                    }
                },
                supportingText = {
                    Column {
                        Text(stringResource(R.string.settings_key_hint))
                        Text(
                            text = when {
                                keyInput.isBlank() -> stringResource(R.string.settings_key_missing)
                                hasUnsavedChanges -> stringResource(R.string.settings_key_dirty)
                                else -> stringResource(R.string.settings_key_saved)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                keyInput.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                                hasUnsavedChanges -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused && keyInput.trim() != savedKey) saveAll()
                    }
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                supportingText = { Text(llm.provider.defaultModel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused && modelInput.trim() != savedModel) saveAll()
                    }
            )

            if (llm.provider.freeModels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = modelMenuOpen,
                    onExpandedChange = { modelMenuOpen = it }
                ) {
                    val selectedFree = llm.provider.freeModels
                        .firstOrNull { it == modelInput.trim() }
                    OutlinedTextField(
                        value = selectedFree ?: stringResource(R.string.settings_free_models),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_free_models)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen)
                        },
                        supportingText = { Text(stringResource(R.string.settings_free_models_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false }
                    ) {
                        llm.provider.freeModels.forEach { freeModel ->
                            DropdownMenuItem(
                                text = { Text(freeModel) },
                                onClick = {
                                    modelInput = freeModel
                                    savedModel = freeModel
                                    onModelChange(freeModel)
                                    modelMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = endpointInput,
                onValueChange = { endpointInput = it },
                label = {
                    Text(
                        stringResource(
                            if (llm.provider.needsAccountId) {
                                R.string.settings_account_id
                            } else {
                                R.string.settings_base_url
                            }
                        )
                    )
                },
                singleLine = true,
                supportingText = {
                    Text(
                        stringResource(
                            if (llm.provider.needsAccountId) {
                                R.string.settings_account_id_hint
                            } else {
                                R.string.settings_base_url_hint
                            }
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused && endpointInput.trim() != savedEndpoint) saveAll()
                    }
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { saveAll() },
                    enabled = hasUnsavedChanges
                ) {
                    Text(stringResource(R.string.settings_save))
                }
                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        onTestConnection(
                            llm.provider,
                            keyInput.trim(),
                            modelInput.trim(),
                            endpointInput.trim()
                        ) { result ->
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
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(llm.provider.consoleUrl))
                    runCatching { context.startActivity(intent) }
                }
            ) {
                Text(stringResource(R.string.settings_open_console))
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
