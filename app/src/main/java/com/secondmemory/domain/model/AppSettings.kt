package com.secondmemory.domain.model

/**
 * User-configurable app settings persisted in DataStore.
 */
data class AppSettings(
    val driveSyncEnabled: Boolean,
    val cloudSummaryEnabled: Boolean,
)
