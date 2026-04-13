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
                preview = "",
            )
        }
    }

    override suspend fun readSummary(fileName: String): String = withContext(Dispatchers.IO) {
        val safeName = fileName.substringAfterLast('/')
        val summaryFile = File(dailyDirectory(context), safeName)
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext ""
        summaryFile.readText()
    }

    override suspend fun readSummaryForDay(dayKey: String): String = withContext(Dispatchers.IO) {
        val summaryFile = File(dailyDirectory(context), summaryFileName(dayKey))
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext ""
        summaryFile.readText()
    }

    override suspend fun saveSummaryForDay(dayKey: String, markdown: String) = withContext(Dispatchers.IO) {
        val summaryFile = File(dailyDirectory(context), summaryFileName(dayKey))
        summaryFile.writeText(markdown)
    }

    override fun summaryFileName(dayKey: String): String {
        return "$dayKey.md"
    }

    override suspend fun lastUpdatedMillisForDay(dayKey: String): Long? = withContext(Dispatchers.IO) {
        val summaryFile = File(dailyDirectory(context), summaryFileName(dayKey))
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext null
        summaryFile.lastModified()
    }

}
