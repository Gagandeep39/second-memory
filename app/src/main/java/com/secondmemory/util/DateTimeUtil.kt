package com.secondmemory.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Returns today's date as an ISO day key used for file naming.
 */
fun todayDayKey(): String {
    return LocalDate.now().toString()
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
