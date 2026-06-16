@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitapp.imageeditor.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val isProcessing = state.phase in listOf(Phase.Uploading, Phase.Generating, Phase.Polling)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Image Editor", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.phase != Phase.Idle) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image pickers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImagePickerCard(
                    label = "Image to Edit",
                    uri = state.sourceUri,
                    enabled = !isProcessing,
                    onPicked = viewModel::setSourceUri,
                    modifier = Modifier.weight(1f)
                )
                ImagePickerCard(
                    label = "Reference Style",
                    uri = state.referenceUri,
                    enabled = !isProcessing,
                    onPicked = viewModel::setReferenceUri,
                    modifier = Modifier.weight(1f)
                )
            }

            // Prompt field
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Edit instruction") },
                placeholder = { Text("e.g. Apply style from reference image") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                minLines = 2
            )

            // Model picker
            ModelDropdown(
                selected = state.selectedModel,
                onSelect = viewModel::setModel,
                enabled = !isProcessing
            )

            // Generate button / progress
            when (state.phase) {
                Phase.Idle, Phase.Error -> {
                    Button(
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = state.sourceUri != null && state.referenceUri != null
                    ) {
                        Text("Generate", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Phase.Uploading, Phase.Generating, Phase.Polling -> {
                    ProgressCard(phase = state.phase, pollStatus = state.pollStatus)
                }
                Phase.Done -> {
                    Button(
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Regenerate")
                    }
                }
            }

            // Error snackbar
            state.error?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }

            // Output image
            if (state.phase == Phase.Done && state.outputUrl != null) {
                OutputImageCard(url = state.outputUrl!!)
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun ImagePickerCard(
    label: String,
    uri: Uri?,
    enabled: Boolean,
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { it?.let(onPicked) }

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(
                width = 1.5.dp,
                color = if (uri != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) { launcher.launch("image/*") },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AVAILABLE_MODELS.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AVAILABLE_MODELS.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(phase: Phase, pollStatus: String) {
    val message = when (phase) {
        Phase.Uploading -> "Uploading images…"
        Phase.Generating -> "Submitting to AI…"
        Phase.Polling -> "Generating • ${pollStatus.ifBlank { "processing" }}"
        else -> ""
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OutputImageCard(url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Result",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Generated image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
