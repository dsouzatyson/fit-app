package com.fitapp.imageeditor.network

import okhttp3.MultipartBody
import retrofit2.http.*

data class UploadUrlRequest(val url: String)

data class ExtractProductRequest(val url: String)
data class ExtractProductResponse(
    val success: Boolean,
    val mediaId: String,
    val imageUrl: String,
    val productTitle: String
)

// ── Response models ──────────────────────────────────────────────────────────

data class UploadResponse(val success: Boolean, val mediaId: String)

data class GenerateRequest(
    val sourceMediaId: String,
    val referenceMediaId: String,
    val prompt: String,
    val model: String = "seedream_v4_5",
    val aspectRatio: String? = null
)

data class GenerateResponse(val success: Boolean, val jobId: String)

data class JobStatusResponse(
    val success: Boolean,
    val jobId: String,
    val status: String,         // pending | processing | completed | failed
    val outputUrl: String?
)

data class LogRedirectRequest(
    val originalUrl: String,
    val finalUrl: String,
    val affiliateTagAdded: Boolean,
    val affiliateTag: String?
)

data class LogRedirectResponse(val success: Boolean)

// ── API interface ─────────────────────────────────────────────────────────────

interface ApiService {

    @Multipart
    @POST("api/images/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): UploadResponse

    @POST("api/images/extract-product")
    suspend fun extractProductImage(
        @Body request: ExtractProductRequest
    ): ExtractProductResponse

    @POST("api/images/upload-url")
    suspend fun uploadImageFromUrl(
        @Body request: UploadUrlRequest
    ): UploadResponse

    @POST("api/images/generate")
    suspend fun generate(
        @Body request: GenerateRequest
    ): GenerateResponse

    @GET("api/images/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): JobStatusResponse

    @POST("api/images/log-redirect")
    suspend fun logRedirect(
        @Body request: LogRedirectRequest
    ): LogRedirectResponse
}
