package com.fitapp.imageeditor.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitapp.imageeditor.BuildConfig
import com.fitapp.imageeditor.data.ImageRepository
import com.fitapp.imageeditor.data.PersonPhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReferenceMode { Gallery, Url }
enum class Step { Garment, Person, Result }

// Progress messages shown during generation
private val PROGRESS_MESSAGES = listOf(
    "Analyzing your photo…",
    "Detecting clothing regions…",
    "Swapping clothes…",
    "Matching fabric texture…",
    "Preserving your features…",
    "Blending the outfit…",
    "Refining edges…",
    "Adding final touches…",
    "Almost there…"
)

data class EditorUiState(
    val step: Step = Step.Garment,
    // Person photo — retained across generate cycles
    val sourceUri: Uri? = null,
    // Garment
    val referenceUri: Uri? = null,
    val referenceMode: ReferenceMode = ReferenceMode.Gallery,
    val referenceUrl: String = "",
    val referenceMediaId: String? = null,       // pre-uploaded from share
    val referenceProductTitle: String? = null,
    val garmentLoading: Boolean = false,
    // Prompt / model
    val prompt: String = "Replace the clothing on the person in Figure 1 with the garment shown in Figure 2. Keep the person's face, skin tone, hair, body shape, and background exactly the same. Only swap the clothes.",
    val selectedModel: String = "seedream_v4_5",
    // Generation progress
    val phase: Phase = Phase.Idle,
    val loadingProgress: Float = 0f,            // 0..1
    val loadingMessage: String = "",
    val pollStatus: String = "",
    // Result
    val outputUrl: String? = null,
    val error: String? = null,
    // Share origin — used to redirect back after trying on
    val shareUrl: String? = null
)

enum class Phase { Idle, Uploading, Generating, Polling, Done, Error }

val AVAILABLE_MODELS = listOf(
    "flux_kontext"    to "Flux Kontext (style transfer)",
    "seedream_v5_lite" to "Seedream 5 (instruction edit)",
    "gpt_image_2"     to "GPT Image 2 (4K edit)",
    "image_auto"      to "Auto (best model)"
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ImageRepository,
    private val photoStore: PersonPhotoStore
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(
        // Restore persisted person photo on every launch
        sourceUri = photoStore.load()
    ))
    val state = _state.asStateFlow()

    fun setSourceUri(uri: Uri) {
        // Copy to internal storage so the URI survives process death
        val stableUri = photoStore.save(uri)
        _state.update { it.copy(sourceUri = stableUri, error = null) }
    }
    fun setReferenceUri(uri: Uri) = _state.update { it.copy(referenceUri = uri, error = null) }
    fun setReferenceMode(mode: ReferenceMode) = _state.update { it.copy(referenceMode = mode, error = null) }
    fun setReferenceUrl(url: String) = _state.update { it.copy(referenceUrl = url, error = null) }
    fun setPrompt(text: String) = _state.update { it.copy(prompt = text) }
    fun setModel(model: String) = _state.update { it.copy(selectedModel = model) }
    fun clearError() = _state.update { it.copy(error = null, phase = Phase.Idle) }

    fun proceedToPersonStep() = _state.update { it.copy(step = Step.Person) }
    fun backToGarmentStep() = _state.update { it.copy(step = Step.Garment, phase = Phase.Idle, error = null) }

    /** From Result: try again with same garment, retain person photo */
    fun tryAgain() = _state.update {
        it.copy(step = Step.Person, phase = Phase.Idle, outputUrl = null, error = null,
            loadingProgress = 0f, loadingMessage = "")
    }

    /** Full reset — back to garment selection, retains person photo */
    fun reset() = _state.update { EditorUiState(sourceUri = it.sourceUri) }

    private fun referenceReady(s: EditorUiState) =
        s.referenceMediaId != null ||
        when (s.referenceMode) {
            ReferenceMode.Gallery -> s.referenceUri != null
            ReferenceMode.Url     -> s.referenceUrl.isNotBlank()
        }

    fun isGenerateEnabled(s: EditorUiState) =
        s.sourceUri != null && referenceReady(s) && !s.garmentLoading

    /** Opened via share intent — extract product image from URL */
    fun loadGarmentFromShareUrl(pageUrl: String) {
        if (_state.value.garmentLoading) return
        _state.update { it.copy(garmentLoading = true, error = null, shareUrl = pageUrl) }
        viewModelScope.launch {
            try {
                val result = repo.extractProductImage(pageUrl)
                _state.update {
                    it.copy(
                        garmentLoading = false,
                        referenceMediaId = result.mediaId,
                        referenceProductTitle = result.productTitle,
                        referenceMode = ReferenceMode.Url,
                        referenceUrl = result.imageUrl,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(garmentLoading = false, error = "Couldn't extract garment: ${e.message}") }
            }
        }
    }

    fun generate() {
        val s = _state.value
        if (s.sourceUri == null) { _state.update { it.copy(error = "Please select a person photo.") }; return }
        if (!referenceReady(s)) { _state.update { it.copy(error = "Please select or enter a garment image.") }; return }
        if (s.prompt.isBlank()) { _state.update { it.copy(error = "Please enter an edit prompt.") }; return }

        if (BuildConfig.MOCK_GENERATION) {
            mockGenerate(s)
            return
        }

        viewModelScope.launch {
            try {
                // Upload phase (0–25%)
                _state.update { it.copy(phase = Phase.Uploading, error = null, loadingProgress = 0.05f, loadingMessage = "Uploading your photo…") }
                val sourceId = repo.uploadImage(s.sourceUri)

                _state.update { it.copy(loadingProgress = 0.15f, loadingMessage = "Uploading garment…") }
                val refId = when {
                    s.referenceMediaId != null               -> s.referenceMediaId
                    s.referenceMode == ReferenceMode.Gallery -> repo.uploadImage(s.referenceUri!!)
                    else                                     -> repo.uploadImageFromUrl(s.referenceUrl.trim())
                }

                // Submit phase (25–35%)
                _state.update { it.copy(phase = Phase.Generating, loadingProgress = 0.28f, loadingMessage = "Submitting to AI…") }
                val jobId = repo.generate(sourceId, refId, s.prompt, s.selectedModel)

                // Polling phase (35–95%)
                _state.update { it.copy(phase = Phase.Polling, loadingProgress = 0.35f, loadingMessage = PROGRESS_MESSAGES[0]) }

                var msgIndex = 0
                var pollCount = 0
                val result = repo.pollUntilDone(jobId) { status ->
                    pollCount++
                    val progress = (0.35f + (pollCount.toFloat() / 40f) * 0.60f).coerceAtMost(0.95f)
                    msgIndex = (pollCount / 2).coerceAtMost(PROGRESS_MESSAGES.lastIndex)
                    _state.update { it.copy(pollStatus = status, loadingProgress = progress, loadingMessage = PROGRESS_MESSAGES[msgIndex]) }
                }

                when (result.status) {
                    "completed" -> _state.update {
                        it.copy(phase = Phase.Done, step = Step.Result,
                            outputUrl = result.outputUrl, loadingProgress = 1f, loadingMessage = "Done!")
                    }
                    else -> _state.update { it.copy(phase = Phase.Error, error = "Generation failed. Try again.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.Error, error = e.message ?: "Unknown error") }
            }
        }
    }

    /**
     * Mock generation — simulates the full progress flow with delays.
     * Uses the person's own photo as the fake result so no API calls are made.
     * Controlled by BuildConfig.MOCK_GENERATION.
     */
    private fun mockGenerate(s: EditorUiState) {
        viewModelScope.launch {
            val steps = listOf(
                0.05f to "Uploading your photo…",
                0.15f to "Uploading garment…",
                0.28f to "Submitting to AI…",
                0.40f to PROGRESS_MESSAGES[0],
                0.52f to PROGRESS_MESSAGES[2],  // Swapping clothes…
                0.65f to PROGRESS_MESSAGES[4],  // Preserving your features…
                0.78f to PROGRESS_MESSAGES[6],  // Refining edges…
                0.90f to PROGRESS_MESSAGES[7],  // Adding final touches…
                0.97f to PROGRESS_MESSAGES[8],  // Almost there…
                1.00f to "Done!"
            )

            _state.update { it.copy(phase = Phase.Uploading, error = null) }
            for ((progress, message) in steps) {
                val phase = when {
                    progress < 0.20f -> Phase.Uploading
                    progress < 0.35f -> Phase.Generating
                    else             -> Phase.Polling
                }
                _state.update { it.copy(phase = phase, loadingProgress = progress, loadingMessage = message) }
                delay(800)
            }

            // Use the person's own saved photo as the mock result URI
            val mockResult = s.sourceUri.toString()
            _state.update {
                it.copy(phase = Phase.Done, step = Step.Result,
                    outputUrl = mockResult, loadingProgress = 1f, loadingMessage = "Done!")
            }
        }
    }
}
