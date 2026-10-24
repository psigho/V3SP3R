package com.vesper.flipper.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vesper.flipper.data.AiConnectionConfig
import com.vesper.flipper.data.AiProvider
import com.vesper.flipper.data.ModelInfo
import com.vesper.flipper.data.modelIdValidationError
import com.vesper.flipper.data.apiKeyValidationError
import com.vesper.flipper.ui.viewmodel.SettingsState

/** Provider drafts remain in memory only; saving is an explicit, atomic operation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderConfiguration(
    state: SettingsState,
    models: List<ModelInfo>,
    refreshing: Boolean,
    onProvider: (AiProvider) -> Unit,
    onSave: (String?, String) -> Unit,
    onRefresh: () -> Unit,
    onTest: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var providerExpanded by remember { mutableStateOf(false) }
    var keyDraft by remember(state.provider, state.apiKey) { mutableStateOf(state.apiKey) }
    var modelDraft by remember(state.provider, state.selectedModel) { mutableStateOf(state.selectedModel) }
    var showKey by remember(state.provider) { mutableStateOf(false) }
    var modelExpanded by remember(state.provider) { mutableStateOf(false) }
    var filter by remember(state.provider) { mutableStateOf("") }
    var localMessage by remember(state.provider) { mutableStateOf<String?>(null) }
    val dirty = keyDraft != state.apiKey || modelDraft != state.selectedModel
    val configError = AiConnectionConfig(state.provider, keyDraft, modelDraft).validationError()
    val modelError = modelIdValidationError(modelDraft)
    val keyError = apiKeyValidationError(keyDraft)
    val saveError = keyError ?: modelError.takeIf { modelDraft.isNotEmpty() }

    ExposedDropdownMenuBox(
        expanded = providerExpanded,
        onExpandedChange = { if (state.providerReady) providerExpanded = it }
    ) {
        OutlinedTextField(
            value = state.provider.displayName,
            onValueChange = {}, readOnly = true,
            label = { Text("AI provider") },
            enabled = state.providerReady,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
            AiProvider.entries.forEach { provider ->
                DropdownMenuItem(text = { Text(provider.displayName) }, onClick = {
                    providerExpanded = false
                    localMessage = null
                    onProvider(provider)
                })
            }
        }
    }
    Text(state.provider.baseUrl, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Each provider keeps its own key and model. Save edits before switching providers.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = keyDraft,
        onValueChange = { keyDraft = it; localMessage = null },
        label = { Text("${state.provider.displayName} API key") },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.providerReady,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "Hide API key" else "Show API key")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = keyDraft.isNotEmpty() && keyError != null,
        supportingText = { if (keyDraft.isNotEmpty() && keyError != null) Text(keyError) },
        shape = RoundedCornerShape(12.dp)
    )
    Text("Use this provider's own API key. It is never reused for another provider.",
        style = MaterialTheme.typography.bodySmall)
    TextButton(
        onClick = { onSave(null, state.selectedModel) },
        enabled = state.providerReady && state.apiKey.isNotEmpty() && !state.isTestingProvider
    ) {
        Text("Remove saved key")
    }
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = modelDraft,
        onValueChange = { modelDraft = it; localMessage = null },
        label = { Text("Exact model ID") },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.providerReady,
        singleLine = true,
        isError = modelDraft.isNotEmpty() && modelError != null,
        supportingText = {
            Text(modelError ?: "Sent exactly as shown: capitalization, prefixes and suffixes are preserved.")
        },
        keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(12.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            try {
                val text = clipboard.getText()?.text
                if (text.isNullOrEmpty()) localMessage = "Clipboard has no text. Copy the model ID first."
                else { modelDraft = text; localMessage = null }
            } catch (_: Exception) {
                localMessage = "Clipboard unavailable. Long-press the model field to paste."
            }
        }, enabled = state.providerReady) {
            Icon(Icons.Default.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(4.dp)); Text("Paste ID")
        }
        TextButton(onClick = {
            try { uriHandler.openUri(state.provider.catalogUrl) }
            catch (_: Exception) { localMessage = "No browser available to open the provider catalog." }
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(4.dp)); Text("Open catalog")
        }
    }

    TextButton(onClick = onRefresh, enabled = state.providerReady && !refreshing && !dirty) {
        if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(Modifier.width(6.dp)); Text(if (refreshing) "Loading models…" else "Load provider model list")
    }
    if (models.isNotEmpty()) {
        OutlinedTextField(value = filter, onValueChange = { filter = it },
            label = { Text("Filter available models") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        val filtered = models.filter {
            it.id.contains(filter, ignoreCase = true) || it.displayName.contains(filter, ignoreCase = true)
        }
        ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
            OutlinedTextField(
                value = "Choose from ${filtered.size} matching models", onValueChange = {}, readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) }
            )
            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                filtered.take(100).forEach { model ->
                    DropdownMenuItem(text = {
                        Column {
                            Text(model.displayName)
                            Text(model.id, style = MaterialTheme.typography.bodySmall)
                        }
                    }, onClick = { modelDraft = model.id; modelExpanded = false })
                }
                if (filtered.size > 100) {
                    DropdownMenuItem(text = { Text("First 100 shown; use the filter to narrow results.") },
                        onClick = {}, enabled = false)
                }
            }
        }
    }
    state.catalogMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text("No silent model substitution. Device control requires a model that supports function/tool calling.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onSave(keyDraft, modelDraft) },
            enabled = state.providerReady && saveError == null && !state.isTestingProvider) {
            Text("Save settings")
        }
        OutlinedButton(onClick = onTest,
            enabled = state.providerReady && !dirty && configError == null && !state.isTestingProvider) {
            if (state.isTestingProvider) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (state.isTestingProvider) "Testing…" else "Test model")
        }
    }
    Text(if (dirty) "Unsaved changes. Save before loading models or testing."
        else if (modelDraft.isEmpty()) "Save your key, then load the model list or paste an exact model ID. Save again before testing."
        else "Test model sends a small API request and may incur a charge. It does not operate the Flipper.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    localMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    state.visibleProviderMessage(dirty)?.let {
        Text(it, color = if (state.providerMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)
    }
}
