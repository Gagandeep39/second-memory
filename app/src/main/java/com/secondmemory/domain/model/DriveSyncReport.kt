package com.secondmemory.domain.model

/**
 * Summary of a Drive mirror sync run.
 */
data class DriveSyncReport(
    val uploadedCount: Int,
    val downloadedCount: Int,
    val deletedCount: Int,
    val conflictedCount: Int,
)
