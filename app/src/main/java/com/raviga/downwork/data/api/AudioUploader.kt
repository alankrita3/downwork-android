package com.raviga.downwork.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/** PUTs a recording to the presigned S3 URL exactly as the backend hands it out. */
class AudioUploader(private val client: OkHttpClient) {
    suspend fun upload(file: File, slot: UploadUrlResponse, contentType: String = "audio/m4a") =
        withContext(Dispatchers.IO) {
            val type = (slot.headers["Content-Type"] ?: contentType).toMediaType()
            val builder = Request.Builder().url(slot.uploadUrl).put(file.asRequestBody(type))
            slot.headers.forEach { (k, v) -> builder.header(k, v) }
            // Presigned URLs must not carry our bearer token or app headers.
            builder.removeHeader("Authorization")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Upload failed (${response.code})")
            }
        }
}
