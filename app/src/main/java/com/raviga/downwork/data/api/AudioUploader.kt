package com.raviga.downwork.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/** PUTs a recording or document to the presigned S3 URL exactly as the backend hands it out. */
class AudioUploader(private val client: OkHttpClient) {
    suspend fun upload(file: File, slot: UploadUrlResponse, contentType: String = "audio/m4a") =
        put(file, slot.uploadUrl, slot.headers, contentType)

    suspend fun upload(file: File, slot: FileUploadUrlResponse, contentType: String) =
        put(file, slot.uploadUrl, slot.headers, contentType)

    private suspend fun put(file: File, url: String, headers: Map<String, String>, contentType: String) =
        withContext(Dispatchers.IO) {
            val type = (headers["Content-Type"] ?: contentType).toMediaType()
            val builder = Request.Builder().url(url).put(file.asRequestBody(type))
            headers.forEach { (k, v) -> builder.header(k, v) }
            // Presigned URLs must not carry our bearer token or app headers.
            builder.removeHeader("Authorization")
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Upload failed (${response.code})")
            }
        }
}
