package com.secondmemory.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A custom styled Snackbar that distinguishes between error and success messages
 * using colors and icons, and includes a dismiss button.
 */
@Composable
fun AppSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val message = snackbarData.visuals.message
    // Heuristic to detect error messages
    val isError = message.contains("failed", ignoreCase = true) ||
            message.contains("error", ignoreCase = true) ||
            message.contains("key", ignoreCase = true) ||
            message.contains("missing", ignoreCase = true) ||
            message.contains("empty", ignoreCase = true)

    val backgroundColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val icon = if (isError) {
        Icons.Default.ErrorOutline
    } else {
        Icons.Default.CheckCircle
    }

    Snackbar(
        modifier = modifier.padding(12.dp),
        containerColor = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        dismissAction = {
            IconButton(onClick = { snackbarData.dismiss() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isError) "Error" else "Success",
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Shows a snackbar immediately, dismissing any current one first to avoid queuing.
 */
suspend fun SnackbarHostState.showSnackbarImmediate(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
