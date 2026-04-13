package com.secondmemory.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun todayDayKey(): String {
    return LocalDate.now().toString()
}

fun formatTime(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
}
