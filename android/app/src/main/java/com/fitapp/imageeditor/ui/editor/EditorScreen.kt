@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitapp.imageeditor.ui.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fitapp.imageeditor.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.fitapp.imageeditor.ui.theme.Ash
import com.fitapp.imageeditor.ui.theme.Cream
import com.fitapp.imageeditor.ui.theme.Emerald
import com.fitapp.imageeditor.ui.theme.EmeraldDim
import com.fitapp.imageeditor.ui.theme.Gold
import com.fitapp.imageeditor.ui.theme.GoldLight
import com.fitapp.imageeditor.ui.theme.Graphite
import com.fitapp.imageeditor.ui.theme.Iron
import com.fitapp.imageeditor.ui.theme.Obsidian
import com.fitapp.imageeditor.ui.theme.Onyx
import com.fitapp.imageeditor.ui.theme.Steel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// ── Entry point ───────────────────────────────────────────────────────────────

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
    // Proceed is only enabled once the image has been extracted + uploaded (mediaId set).
    // Typing a URL is not enough — the user must tap LOAD and wait for extraction.
    val garmentReady = state.referenceMediaId != null ||
        (state.referenceMode == ReferenceMode.Gallery && state.referenceUri != null)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
    ) {
        val floatBottomOffset = maxHeight * 0.05f
        val buttonHeight = 60.dp
        val scrollBottomPad = floatBottomOffset + buttonHeight

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Editorial header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .padding(top = 56.dp, bottom = 32.dp)
            ) {
                Text(
                    text = "01",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "ADD PRODUCT URL",
                    style = MaterialTheme.typography.displayLarge,
                    color = Cream,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Paste an Amazon product link to preview the garment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                )
            }

            GoldDivider()

            Spacer(Modifier.height(28.dp))

            // Garment content area
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                when {
                    state.garmentLoading ->
                        GarmentLoadingCard()
                    state.referenceProductTitle != null && state.referenceUrl.isNotBlank() ->
                        GarmentProductCard(
                            imageUrl = state.referenceUrl,
                            title    = state.referenceProductTitle,
                        )
                    else ->
                        GarmentManualPicker(
                            mode         = state.referenceMode,
                            uri          = state.referenceUri,
                            url          = state.referenceUrl,
                            onModeChange = viewModel::setReferenceMode,
                            onPicked     = viewModel::setReferenceUri,
                            onUrlChange  = viewModel::setReferenceUrl,
                            onLoadUrl    = viewModel::loadGarmentFromShareUrl,
                        )
                }
            }

            Spacer(Modifier.height(24.dp))

            state.error?.let {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    LuxuryErrorCard(msg = it, onDismiss = viewModel::clearError)
                }
            }

            // Bottom padding so content scrolls clear of the floating button
            Spacer(Modifier.height(scrollBottomPad))
        }

        // ── Floating PROCEED button — fixed, non-draggable ────────────────────
        val enabled = garmentReady && !state.garmentLoading
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -floatBottomOffset)
                .padding(horizontal = 36.dp)
                .shadow(
                    elevation    = 24.dp,
                    shape        = RoundedCornerShape(4.dp),
                    ambientColor = Gold.copy(alpha = 0.25f),
                    spotColor    = Gold.copy(alpha = 0.4f),
                )
                .background(
                    brush = if (enabled)
                        Brush.horizontalGradient(listOf(Gold, GoldLight, Gold))
                    else
                        Brush.horizontalGradient(listOf(Steel, Steel)),
                    shape = RoundedCornerShape(4.dp),
                )
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    enabled           = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = viewModel::proceedToPersonStep,
                )
                .padding(horizontal = 28.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P R O C E E D",
                style        = MaterialTheme.typography.labelLarge,
                color        = if (enabled) Obsidian else Ash,
                letterSpacing = 2.5.sp,
            )
        }
    }
}

// ── Step 2: Person + Generate ─────────────────────────────────────────────────

@Composable
private fun PersonStep(state: EditorUiState, viewModel: EditorViewModel) {
    val isProcessing = state.phase in listOf(Phase.Uploading, Phase.Generating, Phase.Polling)

    if (isProcessing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            LuxuryProgressCard(
                progress = state.loadingProgress,
                message  = state.loadingMessage,
            )
        }
    }

    Scaffold(
        containerColor = Obsidian,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (!isProcessing) {
                    IconButton(
                        onClick = viewModel::backToGarmentStep,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Silver,
                        )
                    }
                }
                Text(
                    text = "02  ·  TRY IT ON",
                    style = MaterialTheme.typography.titleMedium,
                    color = Silver,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (!isProcessing) {
                    IconButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Ash)
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
        ) {
            GoldDivider()

            // Garment strip
            GarmentSummaryRow(state = state)
            GoldDivider()

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section label
                Text(
                    text = "YOUR PHOTO",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    letterSpacing = 2.sp,
                )

                PersonPhotoPicker(
                    uri     = state.sourceUri,
                    enabled = !isProcessing,
                    onPicked = viewModel::setSourceUri,
                )

                ScrollDownHint()

                // Edit instruction — underline style
                LuxuryTextField(
                    value       = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    label       = "EDIT INSTRUCTION",
                    enabled     = !isProcessing,
                    minLines    = 2,
                )

                if (state.phase in listOf(Phase.Idle, Phase.Error)) {
                    LuxuryPrimaryButton(
                        label   = "GENERATE LOOK",
                        onClick = viewModel::generate,
                        enabled = viewModel.isGenerateEnabled(state),
                        modifier = Modifier.fillMaxWidth(),
                        padded  = false,
                    )
                }

                state.error?.let {
                    LuxuryErrorCard(msg = it, onDismiss = viewModel::clearError)
                }

                Spacer(Modifier.height(16.dp))
            }
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
    // Redirect button is locked until the generated image has fully loaded in the viewer
    var imageReady by remember { mutableStateOf(false) }

    // Redirect action — extracted so both the button and its logic stay in one place
    val onRedirect: () -> Unit = {
        saving = true
        coroutineScope.launch {
            val saved = state.outputUrl?.let { saveImageToGallery(context, it) } ?: false
            if (saved) {
                val originalUrl = state.shareUrl ?: "https://www.amazon.in"
                val redirectUrl = appendAffiliateTag(originalUrl)
                val tagAdded = redirectUrl != originalUrl
                val tag = BuildConfig.AFFILIATE_TAG.trim().takeIf { it.isNotEmpty() }

                Log.i("FitApp/Redirect", "Original URL : $originalUrl")
                Log.i("FitApp/Redirect", "Outgoing URL : $redirectUrl")
                if (tagAdded) {
                    Log.i("FitApp/Redirect", "Affiliate tag injected: $tag")
                } else {
                    Log.i("FitApp/Redirect", "Affiliate tag NOT added (third-party tag present or config empty)")
                }
                viewModel.logRedirect(originalUrl, redirectUrl, tagAdded, tag)

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                saveError = "Failed to save. Please try again."
            }
            saving = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
    ) {
        // The floating button sits 20 % above the bottom edge.
        // Add equivalent bottom padding to scroll content so nothing hides behind it.
        val floatBottomOffset = maxHeight * 0.05f
        val buttonHeight = 60.dp          // approximate button height
        val scrollBottomPad = floatBottomOffset + buttonHeight

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 36.dp, bottom = 20.dp)
            ) {
                Text(
                    text = "03",
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "YOUR LOOK",
                    style = MaterialTheme.typography.displayMedium,
                    color = Cream,
                )
            }

            GoldDivider()
            Spacer(Modifier.height(24.dp))

            // Before / After
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "BEFORE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ash,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .background(Graphite)
                    ) {
                        if (state.sourceUri != null) {
                            AsyncImage(
                                model = state.sourceUri,
                                contentDescription = "Before",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .aspectRatio(0.75f / 2f)
                        .background(Steel)
                        .align(Alignment.Bottom)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "AFTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .background(Graphite)
                            .border(1.dp, Gold.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.outputUrl != null) {
                            SubcomposeAsyncImage(
                                model              = state.outputUrl,
                                contentDescription = "Result",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize(),
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                modifier    = Modifier.size(28.dp),
                                                strokeWidth = 1.5.dp,
                                                color       = Gold,
                                                trackColor  = Steel,
                                            )
                                            Text(
                                                "LOADING…",
                                                style        = MaterialTheme.typography.labelSmall,
                                                color        = Ash,
                                                letterSpacing = 1.5.sp,
                                            )
                                        }
                                    }
                                },
                                onSuccess = { imageReady = true },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            GoldDivider()
            Spacer(Modifier.height(24.dp))

            if (state.outputUrl != null) {
                Text(
                    "FULL RESULT",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = state.outputUrl,
                    contentDescription = "Full generated result",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .border(1.dp, Steel),
                )
            }

            saveError?.let {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    LuxuryErrorCard(msg = it, onDismiss = { saveError = null })
                }
            }

            // Bottom padding so content scrolls clear of the floating button
            Spacer(Modifier.height(scrollBottomPad))
        }

        // ── Floating redirect button — fixed, non-draggable ───────────────────
        // Disabled (greyed out with spinner) until the generated image has fully loaded.
        val buttonEnabled = imageReady && !saving
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -floatBottomOffset)
                .padding(horizontal = 36.dp)
                .shadow(
                    elevation       = if (buttonEnabled) 24.dp else 4.dp,
                    shape           = RoundedCornerShape(4.dp),
                    ambientColor    = Emerald.copy(alpha = if (buttonEnabled) 0.4f else 0.1f),
                    spotColor       = Emerald.copy(alpha = if (buttonEnabled) 0.6f else 0.1f),
                )
                .background(
                    brush = if (buttonEnabled)
                        Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Emerald, Color(0xFF2E7D32)))
                    else
                        Brush.horizontalGradient(listOf(Steel, Steel)),
                    shape = RoundedCornerShape(4.dp),
                )
                .clip(RoundedCornerShape(4.dp))
                .clickable(
                    enabled           = buttonEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onRedirect,
                )
                .padding(horizontal = 28.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                saving -> Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color       = Cream.copy(alpha = 0.7f),
                    )
                    Text(
                        "SAVING…",
                        style        = MaterialTheme.typography.labelLarge,
                        color        = Cream.copy(alpha = 0.7f),
                        letterSpacing = 2.sp,
                    )
                }
                !imageReady -> Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color       = Ash,
                        trackColor  = Iron,
                    )
                    Text(
                        "LOADING IMAGE…",
                        style        = MaterialTheme.typography.labelLarge,
                        color        = Ash,
                        letterSpacing = 2.sp,
                    )
                }
                else -> Text(
                    "↗  REDIRECT TO MAIN APP",
                    style        = MaterialTheme.typography.labelLarge,
                    color        = Cream,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

// ── Logic functions (unchanged) ───────────────────────────────────────────────

/** Saves [imageUrl] to the device gallery under Pictures/FitApp. */
private suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val bytes = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                URL(imageUrl).readBytes()
            } else {
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

/**
 * Injects the affiliate tag (from BuildConfig.AFFILIATE_TAG) into [url], but ONLY if:
 *  - No 'tag' param exists yet, OR
 *  - The existing 'tag' already matches our tag (idempotent re-apply).
 * If a third-party affiliate tag is present the URL is returned unchanged.
 */
private fun appendAffiliateTag(url: String): String {
    val affiliateTag = BuildConfig.AFFILIATE_TAG.trim()
    if (affiliateTag.isEmpty()) return url
    return try {
        val uri = Uri.parse(url)
        val existingTag = uri.getQueryParameter("tag")
        when {
            existingTag == null       -> uri.buildUpon().appendQueryParameter("tag", affiliateTag).build().toString()
            existingTag == affiliateTag -> url
            else                      -> url
        }
    } catch (e: Exception) {
        url
    }
}

// ── Luxury Design Components ──────────────────────────────────────────────────

/** Animated scroll-down hint shown below the person photo picker */
@Composable
private fun ScrollDownHint() {
    val infiniteTransition = rememberInfiniteTransition(label = "scroll_hint")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 6f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text   = "scroll for more options",
            style  = MaterialTheme.typography.labelSmall,
            color  = Ash.copy(alpha = 0.6f),
            letterSpacing = 1.sp,
        )
        Text(
            text     = "↓",
            style    = MaterialTheme.typography.labelLarge,
            color    = Gold.copy(alpha = 0.7f),
            modifier = Modifier.offset(y = offsetY.dp),
        )
    }
}

/** 1px gold-tinted horizontal rule */
@Composable
private fun GoldDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Gold.copy(alpha = 0.3f),
                        Gold.copy(alpha = 0.5f),
                        Gold.copy(alpha = 0.3f),
                        Color.Transparent,
                    )
                )
            )
    )
}

/** Full-width primary CTA — gold gradient bar */
@Composable
private fun LuxuryPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
) {
    val bg = if (enabled)
        Brush.horizontalGradient(listOf(Gold, GoldLight, Gold))
    else
        Brush.horizontalGradient(listOf(Steel, Steel))

    val textColor = if (enabled) Obsidian else Ash

    Box(
        modifier = modifier
            .then(if (padded) Modifier.fillMaxWidth() else Modifier)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            letterSpacing = 2.5.sp,
        )
    }
}

/** Full-screen luxury progress overlay */
@Composable
private fun LuxuryProgressCard(progress: Float, message: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(700, easing = EaseOutCubic),
        label = "progress",
    )
    val percent = (animatedProgress * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Onyx)
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "CREATING YOUR LOOK",
                style = MaterialTheme.typography.labelLarge,
                color = Ash,
                letterSpacing = 2.sp,
            )

            Text(
                "$percent%",
                style = MaterialTheme.typography.displayLarge,
                color = Gold,
                fontWeight = FontWeight.Bold,
            )

            // Thin animated gold progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Steel)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Gold.copy(alpha = 0.6f), GoldLight, Gold)
                            )
                        )
                )
            }

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Silver,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Garment loading skeleton */
@Composable
private fun GarmentLoadingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(28.dp),
            strokeWidth = 1.5.dp,
            color       = Gold,
            trackColor  = Steel,
        )
        Text(
            "EXTRACTING GARMENT…",
            style        = MaterialTheme.typography.labelLarge,
            color        = Ash,
            letterSpacing = 2.sp,
        )
    }
}

/** Full-bleed editorial product card */
@Composable
private fun GarmentProductCard(imageUrl: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .background(Graphite)
                .border(1.dp, Steel)
        ) {
            AsyncImage(
                model              = imageUrl,
                contentDescription = title,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize(),
            )
        }
        Text(
            text     = title,
            style    = MaterialTheme.typography.bodyMedium,
            color    = Silver,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tab-switched garment picker (gallery / URL).
 *  Gallery tab is hidden when BuildConfig.ENABLE_GALLERY_PICKER == false;
 *  the underlying Gallery code is preserved and re-activates automatically
 *  when the flag is flipped back to true. */
@Composable
private fun GarmentManualPicker(
    mode: ReferenceMode,
    uri: Uri?,
    url: String,
    onModeChange: (ReferenceMode) -> Unit,
    onPicked: (Uri) -> Unit,
    onUrlChange: (String) -> Unit,
    onLoadUrl: (String) -> Unit,
) {
    val galleryEnabled = BuildConfig.ENABLE_GALLERY_PICKER

    // When gallery is disabled, force URL mode so ViewModel state stays consistent
    LaunchedEffect(galleryEnabled) {
        if (!galleryEnabled) onModeChange(ReferenceMode.Url)
    }

    // Effective mode: ignore Gallery selection when picker is disabled
    val effectiveMode = if (!galleryEnabled) ReferenceMode.Url else mode

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // Tab switcher — only rendered when gallery is enabled
        if (galleryEnabled) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(ReferenceMode.Gallery to "GALLERY", ReferenceMode.Url to "PASTE URL")
                    .forEach { (m, label) ->
                        val selected = effectiveMode == m
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null,
                                    onClick           = { onModeChange(m) },
                                )
                                .padding(bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                label,
                                style      = MaterialTheme.typography.labelMedium,
                                color      = if (selected) Gold else Ash,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(if (selected) 1.5.dp else 0.5.dp)
                                    .background(if (selected) Gold else Steel)
                            )
                        }
                    }
            }
        }

        when (effectiveMode) {
            ReferenceMode.Gallery -> {
                // Reached only when ENABLE_GALLERY_PICKER = true
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { it?.let(onPicked) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .background(Graphite)
                        .border(
                            width = if (uri != null) 1.dp else 0.5.dp,
                            color = if (uri != null) Gold.copy(alpha = 0.6f) else Steel,
                        )
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    if (uri != null) {
                        AsyncImage(
                            model              = uri,
                            contentDescription = "Garment",
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.dp, Steel),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint     = Ash,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Text(
                                "TAP TO SELECT",
                                style        = MaterialTheme.typography.labelMedium,
                                color        = Ash,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }
            }

            ReferenceMode.Url -> {
                LuxuryTextField(
                    value         = url,
                    onValueChange = onUrlChange,
                    label         = "PRODUCT URL",
                    placeholder   = "https://www.amazon.in/dp/…",
                    keyboardType  = KeyboardType.Uri,
                )
                val canLoad = url.startsWith("http://") || url.startsWith("https://")
                if (canLoad) {
                    Spacer(Modifier.height(12.dp))
                    // LOAD button — triggers extract-product on backend (same as share flow)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(listOf(Gold, GoldLight, Gold)),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null,
                                onClick           = { onLoadUrl(url) },
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "LOAD GARMENT",
                            style        = MaterialTheme.typography.labelLarge,
                            color        = Obsidian,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Person photo picker with gold-bordered preview */
@Composable
private fun PersonPhotoPicker(uri: Uri?, enabled: Boolean, onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { it?.let(onPicked) }

    if (uri != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Graphite)
                    .border(1.dp, Gold.copy(alpha = 0.5f))
            ) {
                AsyncImage(
                    model              = uri,
                    contentDescription = "Your photo",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            if (enabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .background(Obsidian.copy(alpha = 0.88f))
                        .border(0.5.dp, Steel)
                        .clickable { launcher.launch("image/*") }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "CHANGE PHOTO",
                        style        = MaterialTheme.typography.labelMedium,
                        color        = Gold,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Graphite)
                .border(0.5.dp, Steel)
                .clickable(enabled = enabled) { launcher.launch("image/*") },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, Steel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint     = Ash,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    "ADD YOUR PHOTO",
                    style        = MaterialTheme.typography.labelMedium,
                    color        = Ash,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

/** Garment thumbnail strip shown in PersonStep */
@Composable
private fun GarmentSummaryRow(state: EditorUiState) {
    val imageModel: Any? = when {
        state.referenceUrl.isNotBlank() -> state.referenceUrl
        state.referenceUri != null      -> state.referenceUri
        else                            -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Graphite)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Iron)
                .border(0.5.dp, Steel)
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model              = imageModel,
                    contentDescription = "Garment",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "SELECTED GARMENT",
                style        = MaterialTheme.typography.labelSmall,
                color        = Ash,
                letterSpacing = 1.5.sp,
            )
            Text(
                state.referenceProductTitle ?: "Ready to try on",
                style    = MaterialTheme.typography.bodyMedium,
                color    = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Underline-style text field (luxury editorial) */
@Composable
private fun LuxuryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    enabled: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value            = value,
        onValueChange    = onValueChange,
        label            = {
            Text(
                label,
                style        = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.5.sp,
            )
        },
        placeholder      = if (placeholder.isNotEmpty()) ({
            Text(placeholder, style = MaterialTheme.typography.bodySmall)
        }) else null,
        enabled          = enabled,
        minLines         = minLines,
        modifier         = Modifier.fillMaxWidth(),
        keyboardOptions  = KeyboardOptions(keyboardType = keyboardType),
        colors           = TextFieldDefaults.colors(
            focusedContainerColor      = Color.Transparent,
            unfocusedContainerColor    = Color.Transparent,
            disabledContainerColor     = Color.Transparent,
            focusedIndicatorColor      = Gold,
            unfocusedIndicatorColor    = Steel,
            disabledIndicatorColor     = Iron,
            focusedLabelColor          = Gold,
            unfocusedLabelColor        = Ash,
            focusedTextColor           = Cream,
            unfocusedTextColor         = Cream,
            disabledTextColor          = Ash,
            cursorColor                = Gold,
        ),
        textStyle        = MaterialTheme.typography.bodyMedium,
    )
}

/** Model selector dropdown — underline style */
@Composable
private fun LuxuryModelDropdown(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AVAILABLE_MODELS.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        TextField(
            value         = selectedLabel,
            onValueChange = {},
            readOnly      = true,
            label         = {
                Text(
                    "MODEL",
                    style        = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.5.sp,
                )
            },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled       = enabled,
            colors        = TextFieldDefaults.colors(
                focusedContainerColor      = Color.Transparent,
                unfocusedContainerColor    = Color.Transparent,
                disabledContainerColor     = Color.Transparent,
                focusedIndicatorColor      = Gold,
                unfocusedIndicatorColor    = Steel,
                disabledIndicatorColor     = Iron,
                focusedLabelColor          = Gold,
                unfocusedLabelColor        = Ash,
                focusedTextColor           = Cream,
                unfocusedTextColor         = Cream,
                disabledTextColor          = Ash,
                cursorColor                = Gold,
            ),
            textStyle     = MaterialTheme.typography.bodyMedium,
        )
        ExposedDropdownMenu(
            expanded        = expanded,
            onDismissRequest = { expanded = false },
            modifier        = Modifier.background(Onyx),
        ) {
            AVAILABLE_MODELS.forEach { (id, lbl) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            lbl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (id == selected) Gold else Cream,
                        )
                    },
                    onClick = { onSelect(id); expanded = false },
                    colors  = MenuDefaults.itemColors(
                        textColor         = Cream,
                        leadingIconColor  = Gold,
                    ),
                )
            }
        }
    }
}

/** Minimal left-accented error card */
@Composable
private fun LuxuryErrorCard(msg: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .drawBehind {
                drawRect(
                    color    = ErrorRose,
                    topLeft  = Offset.Zero,
                    size     = size.copy(width = 3.dp.toPx()),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            msg,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(
                "DISMISS",
                style        = MaterialTheme.typography.labelSmall,
                color        = MaterialTheme.colorScheme.onErrorContainer,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

// Error color needed locally for the drawBehind accent
private val ErrorRose = com.fitapp.imageeditor.ui.theme.ErrorRose
private val Silver    = com.fitapp.imageeditor.ui.theme.Silver
