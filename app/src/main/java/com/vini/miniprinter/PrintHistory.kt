package com.vini.miniprinter

import com.vini.miniprinter.image.MonoImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** [id] is the print time in millis and also the folder name. */
data class HistoryEntry(val id: Long, val label: String, val pageCount: Int)

/**
 * Last [MAX_ENTRIES] successful prints, stored as the exact 1-bit pages that were sent
 * (~30 KB per photo), so a reprint is pixel-identical and needs no source file.
 * Layout: <dir>/<id>/label.txt + page-<n>.bin (int width, int height, packed rows).
 */
class PrintHistory(private val dir: File) {

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private fun load(): List<HistoryEntry> =
        dir.listFiles().orEmpty()
            .mapNotNull { d ->
                val id = d.name.toLongOrNull() ?: return@mapNotNull null
                val pages = d.listFiles { f -> f.name.startsWith("page-") }?.size ?: 0
                if (pages == 0) return@mapNotNull null
                HistoryEntry(id, File(d, "label.txt").takeIf { it.exists() }?.readText() ?: "impressão", pages)
            }
            .sortedByDescending { it.id }

    suspend fun add(label: String, pages: List<MonoImage>) = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext
        val id = System.currentTimeMillis()
        val d = File(dir, id.toString()).apply { mkdirs() }
        pages.forEachIndexed { i, p ->
            DataOutputStream(File(d, "page-$i.bin").outputStream().buffered()).use {
                it.writeInt(p.width)
                it.writeInt(p.height)
                it.write(p.data)
            }
        }
        File(d, "label.txt").writeText(label)
        val all = listOf(HistoryEntry(id, label, pages.size)) + _entries.value
        all.drop(MAX_ENTRIES).forEach { File(dir, it.id.toString()).deleteRecursively() }
        _entries.value = all.take(MAX_ENTRIES)
    }

    suspend fun page(id: Long, index: Int): MonoImage? = withContext(Dispatchers.IO) {
        val f = File(File(dir, id.toString()), "page-$index.bin")
        if (!f.exists()) return@withContext null
        runCatching {
            DataInputStream(f.inputStream().buffered()).use {
                val w = it.readInt()
                val h = it.readInt()
                MonoImage(w, h).also { img -> it.readFully(img.data) }
            }
        }.getOrNull()
    }

    suspend fun pages(id: Long): List<MonoImage> {
        val n = _entries.value.firstOrNull { it.id == id }?.pageCount ?: return emptyList()
        return (0 until n).mapNotNull { page(id, it) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        File(dir, id.toString()).deleteRecursively()
        _entries.update { list -> list.filterNot { it.id == id } }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        _entries.value = emptyList()
    }

    companion object {
        const val MAX_ENTRIES = 30
    }
}
