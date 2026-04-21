package com.secondmemory.data.repository

import android.content.Context
import com.secondmemory.domain.model.WeeklySummaryFile
import com.secondmemory.domain.repository.WeeklySummaryRepository
import com.secondmemory.util.weeklyDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File-backed repository for weekly markdown summaries in data/weekly.
 */
class FileWeeklySummaryRepository(private val context: Context) : WeeklySummaryRepository {
    override suspend fun listWeeklySummaries(): List<WeeklySummaryFile> = withContext(Dispatchers.IO) {
        val files = weeklyDirectory(context)
            .listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.nameWithoutExtension }

        files.map { file ->
            WeeklySummaryFile(
                fileName = file.name,
                weekKey = file.nameWithoutExtension,
                preview = "",
            )
        }
    }

    override suspend fun readSummary(fileName: String): String = withContext(Dispatchers.IO) {
        val safeName = fileName.substringAfterLast('/')
        val summaryFile = File(weeklyDirectory(context), safeName)
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext ""
        summaryFile.readText()
    }

    override suspend fun readSummaryForWeek(weekKey: String): String = withContext(Dispatchers.IO) {
        val summaryFile = File(weeklyDirectory(context), summaryFileName(weekKey))
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext ""
        summaryFile.readText()
    }

    override suspend fun saveSummaryForWeek(weekKey: String, markdown: String) = withContext(Dispatchers.IO) {
        val summaryFile = File(weeklyDirectory(context), summaryFileName(weekKey))
        summaryFile.writeText(markdown)
    }

    override fun summaryFileName(weekKey: String): String {
        return "$weekKey.md"
    }

    override suspend fun lastUpdatedMillisForWeek(weekKey: String): Long? = withContext(Dispatchers.IO) {
        val summaryFile = File(weeklyDirectory(context), summaryFileName(weekKey))
        if (!summaryFile.exists() || !summaryFile.isFile) return@withContext null
        summaryFile.lastModified()
    }
}
