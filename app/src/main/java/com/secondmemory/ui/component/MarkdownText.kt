package com.secondmemory.ui.component

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/**
 * Renders markdown content inside a TextView-backed AndroidView.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext)
        },
        update = { textView ->
            textView.setTextColor(onSurfaceColor)
            textView.setLinkTextColor(primaryColor)
            textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            markwon.setMarkdown(textView, markdown)
        },
    )
}
