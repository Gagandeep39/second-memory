package com.secondmemory.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayKeyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

/**
 * Returns today's day key in yyyymmdd format used by raw thought files.
 */
fun todayDayKey(): String {
    return LocalDate.now().format(dayKeyFormatter)
}

/**
 * Formats epoch milliseconds into a local HH:mm time string.
 */
fun formatTime(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
}
