package com.raviga.downwork.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-on-disk cache for the last known config, project list and open
 * projects, so the app opens instantly and reads work offline. The backend is
 * the source of truth; nothing here is ever written back to it.
 */
class CacheStore(context: Context, private val json: Json) {

    private val dir = File(context.filesDir, "cache").apply { mkdirs() }

    suspend fun <T> read(name: String, deserializer: DeserializationStrategy<T>): T? =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$name.json")
            if (!file.exists()) return@withContext null
            runCatching { json.decodeFromString(deserializer, file.readText()) }.getOrNull()
        }

    suspend fun <T> write(name: String, serializer: SerializationStrategy<T>, value: T) =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$name.json")
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            tmp.renameTo(file)
        }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        File(dir, "$name.json").delete()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val CONFIG = "config"
        const val ME = "me"
        const val PROJECTS = "projects"
        const val CREDITS = "credits"
        fun project(id: String) = "project_$id"
        fun document(id: String) = "document_$id"
    }
}
