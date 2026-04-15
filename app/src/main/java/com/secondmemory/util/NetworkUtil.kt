package com.secondmemory.util

import android.content.Context
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Utility to check for actual internet connectivity (not just network connection).
 * Attempts to connect to a well-known server (Google DNS) with a short timeout.
 * Returns true if the connection succeeds, false otherwise.
 */
fun hasInternetConnection(timeoutMs: Int = 1500): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("8.8.8.8", 53), timeoutMs)
            true
        }
    } catch (e: IOException) {
        false
    }
}
