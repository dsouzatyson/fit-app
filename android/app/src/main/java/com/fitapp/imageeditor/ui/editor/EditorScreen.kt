@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitapp.imageeditor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun EditorScreen(
    viewModel: EditorViewModel = hiltViewModel(),
    shareUrl: String? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(shareUrl) {
        if (!shareUrl.isNullOrBlank()) viewModel.loadGarmentFromShareUrl(shareUrl)
    }

    when (state.step) {
        Step.Garment -> GarmentStep(state = state, viewModel = viewModel)
        Step.Person  -> PersonStep(state = state, viewModel = viewModel)
        Step.Result  -> ResultStep(state = state, viewModel = viewModel)
    }
}

// ── Step 1: Garment ───────────────────────────────────────────────────────────

@Composable
private fun GarmentStep(state: EditorUiState, viewModel: EditorViewModel) {
    val garmentReady = state.referenceMediaId != null ||
        (state.referenceMode == ReferenceMode.Gallery && state.referenceUri != null) ||
        (state.referenceMode == ReferenceMode.Url && state.referenceUrl.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Choose Garment", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = viewModel::proceedToPersonStep,
                    enabled = garmentReady && !state.garmentLoading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                ) {
                    Text("Proceed →", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when {
                state.garmentLoading -> GarmentLoadingCard()
                state.referenceProductTitle != null && state.referenceUrl.isNotBlank() ->
                    GarmentProductCard(imageUrl = state.referenceUrl, title = state.referenceProductTitle)
                else -> GarmentManualPicker(
                    mode = state.referenceMode,
                    uri = state.referenceUri,
                    url = state.referenceUrl,
                    onModeChange = viewModel::setReferenceMode,
                    onPicked = viewModel::setReferenceUri,
                    onUrlChange = viewModel::setReferenceUrl
                )
            }
            state.error?.let { ErrorCard(msg = it, onDismiss = viewModel::clearError) }
        }
    }
}

// ── Step 2: Person + Generate ─────────────────────────────────────────────────

@Composable
private fun PersonStep(state: EditorUiState, viewModel: EditorViewModel) {
    val isProcessing = state.phase in listOf(Phase.Uploading, Phase.Generating, Phase.Polling)

    // Non-dismissable progress dialog — dims + disables everything behind it
    if (isProcessing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            InteractiveProgressCard(
                progress = state.loadingProgress,
                message = state.loadingMessage
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Try It On", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isProcessing) {
                        IconButton(onClick = viewModel::backToGarmentStep) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isProcessing) {
                        IconButton(onClick = viewModel::reset) {
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GarmentSummaryRow(state = state)
            HorizontalDivider()

            Text("Your Photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PersonPhotoPicker(
                uri = state.sourceUri,
                enabled = !isProcessing,
                onPicked = viewModel::setSourceUri
            )

            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Edit instruction") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                minLines = 2
            )

            ModelDropdown(selected = state.selectedModel, onSelect = viewModel::setModel, enabled = !isProcessing)

            if (state.phase in listOf(Phase.Idle, Phase.Error)) {
                Button(
                    onClick = viewModel::generate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = viewModel.isGenerateEnabled(state)
                ) {
                    Text("Generate", style = MaterialTheme.typography.titleMedium)
                }
            }

            state.error?.let { ErrorCard(msg = it, onDismiss = viewModel::clearError) }
        }
    }
}

// ── Step 3: Result ────────────────────────────────────────────────────────────

@Composable
private fun ResultStep(state: EditorUiState, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Result", fontWeight = FontWeight.Bold) })
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        saving = true
                        coroutineScope.launch {
                            val saved = state.outputUrl?.let { saveImageToGallery(context, it) } ?: false
                            if (saved) {
                                // Redirect back to the originating app (Amazon)
                                val redirectUrl = state.shareUrl ?: "https://www.amazon.in"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } else {
                                saveError = "Failed to save image. Please try again."
                            }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),   // Material Green 800
                        contentColor = Color.White
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Saving…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("↗  Redirect to main app", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Before / After comparison
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Before", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (state.sourceUri != null) {
                            AsyncImage(model = state.sourceUri, contentDescription = "Before",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("After", style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (state.outputUrl != null) {
                            AsyncImage(model = state.outputUrl, contentDescription = "Result",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // Full result image
            if (state.outputUrl != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    AsyncImage(model = state.outputUrl, contentDescription = "Generated result",
                        contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
            }

            saveError?.let {
                ErrorCard(msg = it, onDismiss = { saveError = null })
            }
        }
    }
}

/** Saves [imageUrl] (http/https or file/content URI) to the device gallery under Pictures/FitApp. */
private suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val bytes = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                URL(imageUrl).readBytes()
            } else {
                // Local URI (mock mode) — read via ContentResolver
                val uri = Uri.parse(imageUrl)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext false
            }
            val filename = "FitApp_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FitApp")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun PersonPhotoPicker(uri: Uri?, enabled: Boolean, onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onPicked) }
    val shape = RoundedCornerShape(16.dp)

    if (uri != null) {
        // Photo selected — show preview with "Choose another" button
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(shape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // "Choose another" overlay button at bottom
            if (enabled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clickable { launcher.launch("image/*") },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        "Choose another image",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    } else {
        // No photo yet — empty picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled) { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null,
                    modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap to add your photo", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InteractiveProgressCard(progress: Float, message: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "progress"
    )
    val percent = (animatedProgress * 100).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circular progress with % in centre
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(80.dp),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // Animated status message
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            // Step indicators
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                strokeCap = StrokeCap.Round,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun GarmentLoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("Extracting garment from link…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GarmentProductCard(imageUrl: String, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp)
        )
    }
}

@Composable
private fun GarmentManualPicker(
    mode: ReferenceMode,
    uri: Uri?,
    url: String,
    onModeChange: (ReferenceMode) -> Unit,
    onPicked: (Uri) -> Unit,
    onUrlChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select Garment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        val tabShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier.fillMaxWidth().clip(tabShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            listOf(ReferenceMode.Gallery to "📁  Gallery", ReferenceMode.Url to "🔗  Paste URL").forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier.weight(1f).clip(tabShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onModeChange(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (mode) {
            ReferenceMode.Gallery -> {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onPicked) }
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                        .clip(shape)
                        .border(2.dp,
                            if (uri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uri != null) {
                        AsyncImage(model = uri, contentDescription = "Garment",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap to pick from gallery", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            ReferenceMode.Url -> {
                OutlinedTextField(
                    value = url, onValueChange = onUrlChange,
                    label = { Text("Image URL") },
                    placeholder = { Text("https://m.media-amazon.com/…") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp).clip(shape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, shape)) {
                        AsyncImage(model = url, contentDescription = "Garment preview",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun GarmentSummaryRow(state: EditorUiState) {
    val imageModel: Any? = when {
        state.referenceUrl.isNotBlank() -> state.referenceUrl
        state.referenceUri != null      -> state.referenceUri
        else                            -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageModel != null) {
                AsyncImage(model = imageModel, contentDescription = "Garment",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column {
            Text("Garment", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.referenceProductTitle ?: "Selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ModelDropdown(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AVAILABLE_MODELS.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {}, readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(), enabled = enabled
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AVAILABLE_MODELS.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
