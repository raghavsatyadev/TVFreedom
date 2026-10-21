package com.gathin.tvgo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.gathin.tvgo.core.Text as Fmt

/** Free RAM on the TV before the run and after its restart. */
@Composable
fun RamCard(before: Int?, after: Int) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Free memory on the TV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (before != null) "${mb(before)}  →  ${mb(after)}" else mb(after), style = MaterialTheme.typography.headlineSmall)
            Text(if (before != null) "Before the changes, and after the restart" else "After the restart",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun mb(v: Int) = if (v >= 1024) Fmt.oneDecimal(v / 1024.0) + " GB" else "$v MB"

/** What the TV showed after the restart. */
@Composable
fun TvScreenshot(png: ByteArray?) {
    // A 4K screenshot is 33 MB as a bitmap: decodeImage keeps it at about 1280 px wide.
    val bitmap = remember(png) { png?.let { runCatching { decodeImage(it) }.getOrNull() } } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("The TV now")
        Image(bitmap, "The TV's screen after the restart", Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.FillWidth)
    }
}
