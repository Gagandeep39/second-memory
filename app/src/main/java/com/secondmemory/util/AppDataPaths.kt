package com.secondmemory.util

import android.content.Context
import java.io.File

/**
 * Ensures and exposes the canonical local storage layout used for sync with cloud folders.
 */
fun ensureAppDataDirectories(context: Context) {
    appDataRootDirectory(context).mkdirs()
    rawDirectory(context).mkdirs()
    dailyDirectory(context).mkdirs()
    weeklyDirectory(context).mkdirs()
    monthlyDirectory(context).mkdirs()
}

/**
 * Returns the top-level data directory used by the app.
 */
fun appDataRootDirectory(context: Context): File = File(context.filesDir, "data")

/**
 * Returns the directory that stores raw thought files as yyyymmdd.json.
 */
fun rawDirectory(context: Context): File = File(appDataRootDirectory(context), "raw")

/**
 * Returns the directory that stores daily summaries as yyyymmdd.md.
 */
fun dailyDirectory(context: Context): File = File(appDataRootDirectory(context), "daily")

/**
 * Returns the directory that stores weekly summaries as yyyymmx.md.
 */
fun weeklyDirectory(context: Context): File = File(appDataRootDirectory(context), "weekly")

/**
 * Returns the directory that stores monthly summaries as yyyymm.md.
 */
fun monthlyDirectory(context: Context): File = File(appDataRootDirectory(context), "monthly")
