package com.fitapp.imageeditor.network

import okhttp3.MultipartBody
import retrofit2.http.*

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

// ── API interface ─────────────────────────────────────────────────────────────

interface ApiService {

    @Multipart
    @POST("api/images/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): UploadResponse

    @POST("api/images/generate")
    suspend fun generate(
        @Body request: GenerateRequest
    ): GenerateResponse

    @GET("api/images/jobs/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): JobStatusResponse
}
