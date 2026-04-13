package com.secondmemory.data.repository

import android.content.Context
import com.secondmemory.domain.model.DailySummaryFile
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.util.dailyDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File-backed repository for daily markdown summaries in data/daily.
 */
class FileDailySummaryRepository(private val context: Context) : DailySummaryRepository {
    override suspend fun listDailySummaries(): List<DailySummaryFile> = withContext(Dispatchers.IO) {
        val files = dailyDirectory(context)
            .listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.nameWithoutExtension }

        files.map { file ->
            DailySummaryFile(
                fileName = file.name,
                dayKey = file.nameWithoutExtension,
                preview = file.previewLine(),
            )
        }
    }

    override suspend fun readSummary(fileName: String): String = withContext(Dispatchers.IO) {
        val safeName = fileName.substringAfterLast('/')
        val summaryFile = File(dailyDirectory(context), safeName)
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext ""
        summaryFile.readText()
    }

    /**
     * Returns the first non-empty line as a preview snippet.
     */
    private fun File.previewLine(): String {
        return useLines { sequence ->
            sequence.firstOrNull { line -> line.isNotBlank() }
        }?.take(PREVIEW_MAX_CHARS) ?: "(No content yet)"
    }

    private companion object {
        const val PREVIEW_MAX_CHARS = 140
    }
}
