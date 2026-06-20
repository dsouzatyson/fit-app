package com.fitapp.imageeditor.data

import android.content.Context
import android.net.Uri
import com.fitapp.imageeditor.network.ApiService
import com.fitapp.imageeditor.network.GenerateRequest
import com.fitapp.imageeditor.network.JobStatusResponse
import com.fitapp.imageeditor.network.ExtractProductRequest
import com.fitapp.imageeditor.network.UploadUrlRequest
import com.fitapp.imageeditor.network.LogRedirectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    private val api: ApiService,
    @ApplicationContext private val context: Context
) {
    /** Upload a Uri → returns fal.ai CDN URL (used as mediaId in /generate) */
    suspend fun uploadImage(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open image")
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = stream.use { it.readBytes() }
        val part = MultipartBody.Part.createFormData(
            "file", "image.jpg",
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )
        return api.uploadImage(part).mediaId
    }

    /** Upload image from external URL → returns fal.ai CDN URL */
    suspend fun uploadImageFromUrl(url: String): String =
        api.uploadImageFromUrl(UploadUrlRequest(url)).mediaId

    /**
     * Given a product page URL (Amazon share link, etc.),
     * extracts the main product image, uploads to fal.ai, returns mediaId + metadata.
     */
    suspend fun extractProductImage(pageUrl: String) =
        api.extractProductImage(ExtractProductRequest(pageUrl))

    /** Submit generation → returns jobId */
    suspend fun generate(
        sourceMediaId: String,
        referenceMediaId: String,
        prompt: String,
        model: String = "flux_kontext"
    ): String = api.generate(
        GenerateRequest(sourceMediaId, referenceMediaId, prompt, model)
    ).jobId

    /** Fire-and-forget: send redirect URL info to backend logs. Swallows errors silently. */
    suspend fun logRedirect(originalUrl: String, finalUrl: String, tagAdded: Boolean, tag: String?) {
        runCatching {
            api.logRedirect(LogRedirectRequest(originalUrl, finalUrl, tagAdded, tag))
        }
    }

    /**
     * Poll every 3s until completed/failed (max 120s).
     * Emits each status via callback for UI progress updates.
     */
    suspend fun pollUntilDone(
        jobId: String,
        onStatus: (String) -> Unit = {}
    ): JobStatusResponse {
        repeat(40) {
            val result = api.getJobStatus(jobId)
            onStatus(result.status)
            when (result.status) {
                "completed", "failed" -> return result
            }
            delay(3_000)
        }
        return api.getJobStatus(jobId) // final check
    }
}
