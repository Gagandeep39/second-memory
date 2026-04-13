package com.secondmemory.domain.model

/**
 * Represents the current sync lifecycle state shown to the user.
 */
enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR,
}
