package com.raviga.downwork.data.drafts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Drafts on this phone: one sealed JSON file each under filesDir/drafts,
 * excluded from backup. Everything is held in memory once loaded (a client
 * has a handful of drafts), and every change is written through atomically.
 */
class DraftStore(
    private val dir: File,
    private val json: Json,
    private val sealer: Sealer,
) {
    private val _drafts = MutableStateFlow<Map<String, LocalDraft>>(emptyMap())
    val drafts: StateFlow<Map<String, LocalDraft>> = _drafts.asStateFlow()

    private val lock = Mutex()
    @Volatile private var loaded = false

    /** Files that could not be opened (Keystore key lost, e.g. after a factory reset of credentials). */
    @Volatile var unreadable: Int = 0
        private set

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        val found = withContext(Dispatchers.IO) {
            dir.mkdirs()
            var bad = 0
            val map = dir.listFiles { f -> f.name.endsWith(EXT) }.orEmpty().mapNotNull { f ->
                runCatching { json.decodeFromString(LocalDraft.serializer(), String(sealer.open(f.readBytes()), Charsets.UTF_8)) }
                    .onFailure { bad++ }
                    .getOrNull()
            }.associateBy { it.id }
            unreadable = bad
            map
        }
        _drafts.value = found
        loaded = true
    }

    fun get(id: String): LocalDraft? = _drafts.value[id]

    fun bySubmitted(serverProjectId: String): LocalDraft? =
        _drafts.value.values.firstOrNull { it.serverProjectId == serverProjectId }

    /** Applies [change] to the draft and writes it; returns the result. */
    suspend fun update(id: String, change: (LocalDraft) -> LocalDraft): LocalDraft = lock.withLock {
        val before = _drafts.value[id] ?: throw NoSuchElementException("No draft $id")
        val after = change(before)
        write(after)
        _drafts.update { it + (after.id to after) }
        after
    }

    suspend fun create(draft: LocalDraft): LocalDraft = lock.withLock {
        write(draft)
        _drafts.update { it + (draft.id to draft) }
        draft
    }

    suspend fun delete(id: String) = lock.withLock {
        withContext(Dispatchers.IO) { File(dir, "$id$EXT").delete() }
        _drafts.update { it - id }
    }

    /** Delete my data: every draft on this phone goes. */
    suspend fun wipe() = lock.withLock {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
        _drafts.value = emptyMap()
        unreadable = 0
    }

    /** Plain JSON of every draft, for the data export. */
    fun exportJson(): Map<String, String> = _drafts.value.mapValues { (_, d) ->
        json.encodeToString(LocalDraft.serializer(), d)
    }

    private suspend fun write(draft: LocalDraft) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val bytes = sealer.seal(json.encodeToString(LocalDraft.serializer(), draft).toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "${draft.id}.${UUID.randomUUID()}.tmp")
        tmp.writeBytes(bytes)
        val target = File(dir, "${draft.id}$EXT")
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); throw java.io.IOException("Couldn't save the draft") }
        }
    }

    companion object {
        private const val EXT = ".draft"
        fun newId(): String = "ld_" + UUID.randomUUID().toString().replace("-", "").take(20)
    }
}
