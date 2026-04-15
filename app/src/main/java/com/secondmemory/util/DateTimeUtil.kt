package com.secondmemory.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayKeyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val displayDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
private val displayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

/**
 * Returns today's day key in yyyymmdd format used by raw thought files.
 */
fun todayDayKey(): String {
    return LocalDate.now().format(dayKeyFormatter)
}

/**
 * Returns a day key moved by the provided number of days.
 */
fun shiftDayKey(dayKey: String, deltaDays: Long): String {
    val date = parseDayKey(dayKey) ?: LocalDate.now()
    return date.plusDays(deltaDays).format(dayKeyFormatter)
}

/**
 * Returns today's date as a UTC midnight timestamp for the date picker.
 */
fun todayUtcStartOfDayMillis(): Long {
    return LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * Converts a date picker timestamp into the app's yyyymmdd day key.
 */
fun dayKeyFromUtcMillis(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(dayKeyFormatter)
}

/**
 * Converts a day key into UTC midnight timestamp.
 */
fun dayKeyToUtcMillis(dayKey: String): Long {
    val date = parseDayKey(dayKey) ?: LocalDate.now()
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * Formats a day key for user-facing display labels.
 */
fun dayKeyDisplayText(dayKey: String): String {
    val date = parseDayKey(dayKey) ?: return dayKey
    return date.format(displayDayFormatter)
}

/**
 * Parses yyyymmdd day key into LocalDate.
 */
private fun parseDayKey(dayKey: String): LocalDate? {
    return try {
        LocalDate.parse(dayKey, dayKeyFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
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

/**
 * Formats epoch milliseconds into a local date-time string.
 */
fun formatDateTime(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(displayDateTimeFormatter)
}
