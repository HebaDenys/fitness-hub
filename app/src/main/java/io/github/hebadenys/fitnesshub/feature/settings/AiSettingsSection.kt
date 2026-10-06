package io.github.hebadenys.fitnesshub.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.ai.AiKeyStore
import io.github.hebadenys.fitnesshub.core.ai.AiMealRequest
import io.github.hebadenys.fitnesshub.core.ai.AiProvider
import io.github.hebadenys.fitnesshub.core.ai.AiRequestPayload
import io.github.hebadenys.fitnesshub.core.ai.AiResult
import io.github.hebadenys.fitnesshub.core.ai.AiSettings
import io.github.hebadenys.fitnesshub.core.ai.AiSettingsStore
import io.github.hebadenys.fitnesshub.core.ai.AiTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val settings: AiSettings,
    val hasStoredKey: Boolean
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsStore: AiSettingsStore,
    private val keyStore: AiKeyStore,
    private val transport: AiTransport
) : ViewModel() {

    /** Held until the user approves it in the confirmation dialog. */
    private val pendingPayload = MutableStateFlow<AiRequestPayload?>(null)
    val pending: StateFlow<AiRequestPayload?> = pendingPayload.asStateFlow()

    private val resultState = MutableStateFlow<AiResult?>(null)
    val result: StateFlow<AiResult?> = resultState.asStateFlow()

    private val hasKey = MutableStateFlow(keyStore.isConfigured())

    val uiState: StateFlow<AiUiState?> = combine(settingsStore.settings, hasKey) { settings, key ->
        AiUiState(settings, key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setEnabled(enabled) }

    fun setProvider(provider: AiProvider) = viewModelScope.launch { settingsStore.setProvider(provider) }

    fun saveKey(key: String) = viewModelScope.launch {
        keyStore.save(key)
        val stored = keyStore.isConfigured()
        hasKey.value = stored
        if (stored) settingsStore.setEnabled(true) else settingsStore.disable()
    }

    fun clearKey() = viewModelScope.launch {
        keyStore.clear()
        hasKey.value = false
        settingsStore.disable()
    }

    fun stageRequest(text: String) {
        val settings = uiState.value?.settings ?: return
        val key = keyStore.load() ?: return
        pendingPayload.value = AiMealRequest.build(settings, key, text)
    }

    fun cancelRequest() {
        pendingPayload.value = null
    }

    fun confirmRequest() {
        val payload = pendingPayload.value ?: return
        pendingPayload.value = null
        viewModelScope.launch { resultState.value = transport.send(payload) }
    }

    fun dismissResult() {
        resultState.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSection(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    var providerMenuOpen by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var promptText by remember { mutableStateOf("") }

    val current = state ?: return
    val settings = current.settings
    val hasStoredKey = current.hasStoredKey

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_section_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.ai_section_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ai_enable_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
            }

            ExposedDropdownMenuBox(
                expanded = providerMenuOpen,
                onExpandedChange = { providerMenuOpen = it }
            ) {
                OutlinedTextField(
                    value = settings.provider?.id ?: stringResource(R.string.ai_provider_unset),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ai_provider_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuOpen) },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false }
                ) {
                    AiProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.id) },
                            onClick = {
                                viewModel.setProvider(provider)
                                providerMenuOpen = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.ai_api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveKey(apiKey); apiKey = "" },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ai_save_key))
                }
                if (hasStoredKey) {
                    OutlinedButton(
                        onClick = viewModel::clearKey,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.ai_clear_key))
                    }
                }
            }

            if (settings.enabled && hasStoredKey) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text(stringResource(R.string.ai_prompt_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.stageRequest(promptText) },
                    enabled = promptText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ai_review_payload))
                }
            }

            val outcome = result
            if (outcome != null) {
                Text(
                    text = when (outcome) {
                        is AiResult.Success -> stringResource(R.string.ai_result_ok)
                        is AiResult.Failed -> stringResource(R.string.ai_result_failed, outcome.reason)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = viewModel::dismissResult) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }

    pending?.let { payload ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRequest,
            title = { Text(stringResource(R.string.ai_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.ai_confirm_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SelectionContainer {
                        Text(
                            text = payload.preview(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRequest) {
                    Text(stringResource(R.string.ai_confirm_send))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRequest) {
                    Text(stringResource(R.string.ai_confirm_cancel))
                }
            }
        )
    }
}
