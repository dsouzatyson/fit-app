package com.fitapp.imageeditor.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitapp.imageeditor.data.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val sourceUri: Uri? = null,
    val referenceUri: Uri? = null,
    val prompt: String = "Replace the clothing on the person in Figure 1 with the garment shown in Figure 2. Keep the person's face, skin tone, hair, body shape, and background exactly the same. Only swap the clothes.",
    val selectedModel: String = "seedream_v4_5",
    val phase: Phase = Phase.Idle,
    val pollStatus: String = "",
    val outputUrl: String? = null,
    val error: String? = null
)

enum class Phase { Idle, Uploading, Generating, Polling, Done, Error }

val AVAILABLE_MODELS = listOf(
    "flux_kontext" to "Flux Kontext (style transfer)",
    "seedream_v5_lite" to "Seedream 5 (instruction edit)",
    "gpt_image_2" to "GPT Image 2 (4K edit)",
    "image_auto" to "Auto (best model)"
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ImageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state = _state.asStateFlow()

    fun setSourceUri(uri: Uri) = _state.update { it.copy(sourceUri = uri, outputUrl = null, error = null) }
    fun setReferenceUri(uri: Uri) = _state.update { it.copy(referenceUri = uri, outputUrl = null, error = null) }
    fun setPrompt(text: String) = _state.update { it.copy(prompt = text) }
    fun setModel(model: String) = _state.update { it.copy(selectedModel = model) }
    fun clearError() = _state.update { it.copy(error = null, phase = Phase.Idle) }
    fun reset() = _state.update { EditorUiState() }

    fun generate() {
        val s = _state.value
        if (s.sourceUri == null || s.referenceUri == null) {
            _state.update { it.copy(error = "Please select both images.") }
            return
        }
        if (s.prompt.isBlank()) {
            _state.update { it.copy(error = "Please enter an edit prompt.") }
            return
        }

        viewModelScope.launch {
            try {
                // 1. Upload both images
                _state.update { it.copy(phase = Phase.Uploading, error = null) }
                val sourceId = repo.uploadImage(s.sourceUri)
                val refId = repo.uploadImage(s.referenceUri)

                // 2. Submit generation
                _state.update { it.copy(phase = Phase.Generating) }
                val jobId = repo.generate(sourceId, refId, s.prompt, s.selectedModel)

                // 3. Poll for result
                _state.update { it.copy(phase = Phase.Polling) }
                val result = repo.pollUntilDone(jobId) { status ->
                    _state.update { it.copy(pollStatus = status) }
                }

                when (result.status) {
                    "completed" -> _state.update {
                        it.copy(phase = Phase.Done, outputUrl = result.outputUrl)
                    }
                    else -> _state.update {
                        it.copy(phase = Phase.Error, error = "Generation failed. Try again.")
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.Error, error = e.message ?: "Unknown error") }
            }
        }
    }
}
